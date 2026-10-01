package local.plasmomusic;
import java.util.*;
import java.nio.file.*;

public final class VoicePresetTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);System.out.println("PASS "+message);}
    static short[] speech(int rate,double level){short[] x=new short[rate*2];for(int i=0;i<x.length;i++)x[i]=(short)(level*(Math.sin(2*Math.PI*180*i/rate)+.35*Math.sin(2*Math.PI*540*i/rate)+.15*Math.sin(2*Math.PI*2700*i/rate)));return x;}
    static double rms(short[] x,int offset){double sum=0;for(int i=offset;i<x.length;i++)sum+=(double)x[i]*x[i];return Math.sqrt(sum/(x.length-offset));}
    static double amplitude(short[] x,int rate,int frequency){double a=0,b=0;for(int i=rate;i<x.length;i++){a+=x[i]*Math.sin(2*Math.PI*frequency*i/rate);b+=x[i]*Math.cos(2*Math.PI*frequency*i/rate);}return Math.hypot(a,b)/(x.length-rate);}
    public static void main(String[] args)throws Exception{
        for(int rate:List.of(44100,48000))for(double level:List.of(500.0,5000.0))for(var style:List.of(StudioSettings.Style.ROBOT,StudioSettings.Style.CHILD,StudioSettings.Style.VILLAIN,StudioSettings.Style.UNDERWATER)){
            short[] input=speech(rate,level),output=new ChannelEffects().processVoice(input,rate,false,StudioSettings.defaults().select(style));
            double ratio=rms(output,rate)/rms(input,rate);
            check(ratio>.75&&ratio<1.4,style+" at "+rate+" Hz / level "+level+" retains original RMS within about 3 dB: "+ratio);
        }
        int rate=48000;short[] bright=new short[rate*2];for(int i=0;i<bright.length;i++)bright[i]=(short)(3000*(Math.sin(2*Math.PI*240*i/rate)+Math.sin(2*Math.PI*4000*i/rate)));
        short[] underwater=new ChannelEffects().processVoice(bright,rate,false,StudioSettings.defaults().select(StudioSettings.Style.UNDERWATER));
        check(amplitude(underwater,rate,4000)<amplitude(underwater,rate,240)*.08,"underwater remains strongly low-pass after volume compensation");
        for(var style:StudioSettings.Style.values()){
            ChannelEffects fx=new ChannelEffects();StudioSettings s=StudioSettings.defaults().select(style);
            check(Arrays.equals(new short[960],fx.processVoice(new short[960],rate,false,s)),style+" never generates sound from initial silence");
            fx.processVoice(speech(rate,5000),rate,false,s);
            short[] tail=null;for(int i=0;i<300;i++)tail=fx.processVoice(new short[960],rate,false,s);
            check(!fx.hasTail()&&Arrays.equals(new short[960],tail),style+" compensation does not sustain a silent stream or boost residual noise");
        }
        Path p=Files.createTempDirectory("voice-presets").resolve("studio.properties");
        for(String old:List.of("BABY","ANCESTOR")){
            Files.writeString(p,"voice.schema=3\nvoice.style="+old+"\ndsp.pitch=-12\nvoice.room=STUDIO\npreview.delay=5\n");
            var s=StudioSettings.read(p);var expected=old.equals("BABY")?StudioSettings.Style.CHILD:StudioSettings.Style.VILLAIN;
            check(s.voiceStyle()==expected&&s.dsp().equals(DspSettings.preset(expected))&&s.voiceRoom()==StudioSettings.Room.STUDIO&&s.previewDelay()==5,"old "+old+" migrates to replacement preset and preserves reverb/preview");
        }
        for(var style:StudioSettings.Style.values()){var s=StudioSettings.defaults().select(style);s.save(p);check(s.equals(StudioSettings.read(p)),style+" preset persists in schema 4");}
        Files.delete(p);Files.delete(p.getParent());
    }
}
