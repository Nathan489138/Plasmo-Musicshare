package local.plasmomusic;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
public class AudioTest {
    static void eq(boolean pass,String name){if(!pass)throw new AssertionError(name);System.out.println("PASS "+name);}
    static void resamplingAcrossChunks(int sourceRate,int targetRate) {
        int outputFrames=4000;
        double step=(double)sourceRate/targetRate;
        int sourceFrames=(int)Math.ceil(outputFrames*step)+2;
        float[] waveform=new float[sourceFrames*2];
        for(int i=0;i<sourceFrames;i++) {
            waveform[i*2]=(float)(.45*Math.sin(2*Math.PI*997*i/sourceRate));
            waveform[i*2+1]=(float)(.3*Math.cos(2*Math.PI*1733*i/sourceRate));
        }
        AudioBuffer whole=new AudioBuffer(),chunked=new AudioBuffer();
        whole.offer(waveform,sourceRate);
        short[] expected=whole.take(outputFrames,targetRate,true,1),actual=new short[expected.length];
        int supplied=0,produced=0;
        for(int count:new int[]{137,960,311,19,1024,733,816}) {
            // Supply the look-ahead frame before each read so this tests phase continuity, not underrun.
            int needed=(int)Math.ceil((produced+count)*step)+2;
            chunked.offer(Arrays.copyOfRange(waveform,supplied*2,needed*2),sourceRate);
            supplied=needed;
            short[] part=chunked.take(count,targetRate,true,1);
            System.arraycopy(part,0,actual,produced*2,part.length);
            produced+=count;
        }
        eq(Arrays.equals(expected,actual),sourceRate+" to "+targetRate+" nonconstant resampling stays continuous across uneven chunks");
        // Check an independent interpolation reference, including both sides of chunk boundaries.
        boolean matchesReference=true;
        for(int i:new int[]{0,1,136,137,1096,1097,1407,1408,3999}) {
            double position=i*step;
            int base=(int)position;
            double fraction=position-base;
            for(int channel=0;channel<2;channel++) {
                float value=(float)(waveform[base*2+channel]+(waveform[(base+1)*2+channel]-waveform[base*2+channel])*fraction);
                int reference=Math.round(value*32768);
                matchesReference&=Math.abs(expected[i*2+channel]-reference)<=1;
            }
        }
        eq(matchesReference,sourceRate+" to "+targetRate+" resampling preserves waveform positions and stereo channels");
    }
    static void topChannelRouting() {
        int channels=6,frames=4;
        // FL, FR, top-front-left, top-front-right, top-back-left, top-back-right.
        int mask=1|2|0x1000|0x4000|0x8000|0x20000;
        ByteBuffer input=ByteBuffer.allocate(frames*channels*4).order(ByteOrder.LITTLE_ENDIAN);
        for(int frame=0;frame<frames;frame++)
            for(int channel=0;channel<channels;channel++)input.putFloat(channel==frame+2?.5f:0);
        float[] output=new float[frames*2];
        Wasapi.decode(input.array(),frames,channels,32,3,mask,output);
        eq(output[0]>0&&output[1]==0&&output[2]==0&&output[3]>0
            &&output[4]>0&&output[5]==0&&output[6]==0&&output[7]>0,
            "surround top-front and top-back channels retain their left/right positions");
    }
    public static void main(String[] args)throws Exception {
        AudioBuffer quiet=new AudioBuffer();
        quiet.offer(new float[]{.01f,-.005f,.01f,-.005f,.01f,-.005f},48000);
        short[] amplified=new MusicStream(quiet).read(2,48000,true,new Settings("exclude-game",true,500,true),EffectSettings.defaults());
        eq(Math.abs(amplified[0]-1638)<=1&&Math.abs(amplified[1]+819)<=1,"500 percent amplifies quiet stereo music fivefold with channel polarity intact");
        quiet.clear();quiet.offer(new float[]{.3f,-.3f,.3f,-.3f},48000);
        amplified=new MusicStream(quiet).read(1,48000,true,new Settings("exclude-game",true,500,true),EffectSettings.defaults());
        eq(amplified[0]==32767&&amplified[1]==-32768,"high music gain clips at PCM bounds without sign wraparound");
        Path boostedFile=Files.createTempDirectory("musicshare-gain-test").resolve("settings.properties");
        Settings boosted=new Settings("exclude-game",false,500,true,true);boosted.save(boostedFile);
        eq(Settings.read(boostedFile).equals(boosted),"500 percent survives settings reload with other flags intact");
        eq(new Settings("exclude-game",false,Integer.MAX_VALUE,true).volume()==Settings.MAX_VOLUME,"out-of-range gain is bounded at 500 percent");
        for(int oldVolume:new int[]{0,50,100,150})eq(new Settings("exclude-game",false,oldVolume,true).volume()==oldVolume,"existing gain "+oldVolume+" is preserved");
        float[] decoded=new float[4];
        Wasapi.decode(new byte[]{0,64,0,(byte)192,0,32,0,(byte)224},2,2,16,1,3,decoded);
        eq(decoded[0]==.5f&&decoded[1]==-.5f&&decoded[2]==.25f&&decoded[3]==-.25f,"PCM16 channel order and sign");
        byte[] floats=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(.125f).putFloat(-.75f).array();
        Wasapi.decode(floats,1,2,32,3,3,decoded);eq(decoded[0]==.125f&&decoded[1]==-.75f,"float32 format");
        Wasapi.decode(new byte[]{0,0,64,0,0,(byte)192},1,2,24,1,3,decoded);eq(decoded[0]==.5f&&decoded[1]==-.5f,"PCM24 sign extension");
        Wasapi.decode(new byte[]{0,0,0,64,0,0,0,(byte)192},1,2,32,1,3,decoded);eq(decoded[0]==.5f&&decoded[1]==-.5f,"PCM32 format");
        Wasapi.decode(new byte[]{0,64},1,1,16,1,0,decoded);eq(decoded[0]==.5f&&decoded[1]==.5f,"mono duplicates to both channels");
        topChannelRouting();
        AudioBuffer b=new AudioBuffer();float[] input=new float[4800];
        for(int i=0;i<input.length;i+=2){input[i]=.5f;input[i+1]=-.5f;}
        b.offer(input,48000);short[] out=b.take(960,48000,true,1);
        eq(out.length==1920&&out[0]==16384&&out[1]==-16384&&out[1918]==16384,"20ms stereo frame and gain");
        b.clear();b.offer(input,48000);out=b.take(960,48000,false,1);eq(out[0]==0&&out[959]==0,"stereo downmix balances L and R");
        b.clear();b.offer(input,44100);out=b.take(960,48000,true,.5f);eq(out[0]==8192&&out[1918]==8192,"44.1 to 48k resampling and gain");
        resamplingAcrossChunks(44100,48000);
        resamplingAcrossChunks(48000,44100);
        resamplingAcrossChunks(96000,48000);
        b.clear();eq(Arrays.equals(b.take(960,48000,false,1),new short[960]),"stop clears queued audio");
        short[] mix={30000,-30000};AudioBuffer.mix(mix,new short[]{10000,-10000},false);eq(mix[0]==32767&&mix[1]==-32768,"mix saturates without wraparound");
        short[] gate={1000,1000,1000,1000};AudioBuffer.mix(gate,new short[]{2000,3000},true);eq(Arrays.equals(gate,new short[]{3000,3000,4000,4000}),"mono mic aligns stereo frames");
        Path p=Path.of("build/test-settings.properties");new Settings("endpoint-test",false,67,true).save(p);eq(Settings.read(p).device().equals("endpoint-test")&&Settings.read(p).volume()==67,"settings persistence");Files.delete(p);
        new Settings("exclude-game",true,67,true,true).save(p);eq(Settings.read(p).forceOpen(),"force-open setting persists");
        Files.writeString(p,"volume=67\nstereo=true\n");eq(!Settings.read(p).forceOpen(),"older configs default force-open to off");Files.delete(p);
        System.out.println("ALL AUDIO TESTS PASSED");
        if(args.length>0&&args[0].equals("--native")){
            var devices=Wasapi.devices();System.out.println("Active render endpoints: "+devices.size());
            for(var d:devices)System.out.println(d);
            if(!devices.isEmpty()){
                long end=System.nanoTime()+1_000_000_000L;
                int[] frames={0};
                Wasapi.capture(args.length>1?"exclude-game":devices.get(0).id(),()->System.nanoTime()<end,new Wasapi.Sink(){
                    public void format(int r,int c,int bit){System.out.println("NATIVE FORMAT "+r+" Hz / "+c+" channels / "+bit+" bit");}
                    public void samples(float[] s,int r){frames[0]+=s.length/2;}
                });
                System.out.println("NATIVE OPEN/CLOSE PASSED; frames="+frames[0]+" (silence may produce zero)");
            }
        }
    }
}
