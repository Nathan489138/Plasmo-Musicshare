package local.plasmomusic;
import java.util.*;
import java.nio.file.*;
public final class StudioEffectsTest {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    static short[] tone(int frames){short[] out=new short[frames];for(int i=0;i<frames;i++)out[i]=(short)(4500*Math.sin(2*Math.PI*220*i/48000)+1800*Math.sin(2*Math.PI*570*i/48000));return out;}
    static long energy(short[] data){long total=0;for(short x:data)total+=(long)x*x;return total;}
    public static void main(String[] args)throws Exception{
        short[] signal=tone(24000);
        check(Arrays.equals(signal,new ChannelEffects().process(signal,48000,false,StudioSettings.Style.NONE,100,StudioSettings.Room.OFF,25)),"all new effects off preserve every dry sample");
        Set<Integer> signatures=new HashSet<>();
        for(StudioSettings.Style style:StudioSettings.Style.values())if(style!=StudioSettings.Style.NONE&&style!=StudioSettings.Style.CUSTOM){
            short[] output=new ChannelEffects().process(signal,48000,false,style,100,StudioSettings.Room.OFF,25);
            check(!Arrays.equals(signal,output)&&energy(output)>100000&&signatures.add(Arrays.hashCode(output)),style.label+" produces an audible, distinct signal");
            check(Arrays.equals(signal,new ChannelEffects().process(signal,48000,false,style,0,StudioSettings.Room.OFF,25)),style.label+" at 0% returns exact original samples");
        }
        ChannelEffects whole=new ChannelEffects(),split=new ChannelEffects();
        short[] expected=whole.process(signal,48000,false,StudioSettings.Style.VILLAIN,80,StudioSettings.Room.STUDIO,25),actual=new short[signal.length];
        for(int i=0;i<signal.length;i+=960){short[] part=split.process(Arrays.copyOfRange(signal,i,i+960),48000,false,StudioSettings.Style.VILLAIN,80,StudioSettings.Room.STUDIO,25);System.arraycopy(part,0,actual,i,part.length);}
        check(Arrays.equals(expected,actual),"character effects and reverb stay continuous across capture frames");
        short[] pure=new short[48000];for(int i=0;i<pure.length;i++)pure[i]=(short)(6000*Math.sin(2*Math.PI*220*i/48000));
        short[] baby=new ChannelEffects().process(pure,48000,false,StudioSettings.Style.CHILD,100,StudioSettings.Room.OFF,0);
        int crossings=0;for(int i=24001;i<baby.length;i++)if(baby[i-1]<=0&&baby[i]>0)crossings++;
        check(crossings*2>265&&crossings*2<290,"TarsosDSP child raises 220 Hz input by approximately four semitones");
        ChannelEffects echo=new ChannelEffects();short[] echoPulse=new short[960];echoPulse[0]=10000;
        echo.process(echoPulse,48000,false,StudioSettings.Style.ECHO,100,StudioSettings.Room.OFF,0);
        check(echo.hasTail(),"echo tracks pending repeats even during the initial quiet gap");
        long echoes=0;for(int i=0;i<30;i++)echoes+=energy(echo.process(new short[960],48000,false,StudioSettings.Style.ECHO,100,StudioSettings.Room.OFF,0));
        check(echoes>0,"TarsosDSP echo continues after speech stops");
        for(int i=0;i<300;i++)echo.process(new short[960],48000,false,StudioSettings.Style.ECHO,100,StudioSettings.Room.OFF,0);
        check(!echo.hasTail(),"echo eventually finishes instead of keeping stream active forever");
        echo.process(echoPulse,48000,false,StudioSettings.Style.ECHO,100,StudioSettings.Room.OFF,0);
        echo.process(new short[960],48000,false,StudioSettings.Style.ECHO,0,StudioSettings.Room.OFF,0);
        long stale=0;for(int i=0;i<30;i++)stale+=energy(echo.process(new short[960],48000,false,StudioSettings.Style.ECHO,100,StudioSettings.Room.OFF,0));
        check(stale==0,"zero strength clears queued old echoes before re-enabling");
        for(StudioSettings.Room room:StudioSettings.Room.values())if(room!=StudioSettings.Room.OFF){
            ChannelEffects fx=new ChannelEffects();short[] pulse=new short[960];pulse[0]=6000;
            short[] first=fx.process(pulse,48000,false,StudioSettings.Style.NONE,100,room,35);
            check(first[0]==6000&&fx.hasTail(),room.label+" preserves immediate dry voice and tracks delayed tail");
            long middle=0;for(int i=0;i<20;i++)middle+=energy(fx.process(new short[960],48000,false,StudioSettings.Style.NONE,100,room,35));
            check(middle>0,room.label+" has audible reverberation after the input stops");
            for(int i=0;i<600;i++)fx.process(new short[960],48000,false,StudioSettings.Style.NONE,100,room,35);
            check(!fx.hasTail(),room.label+" tail eventually decays instead of keeping transmission alive");
            check(Arrays.equals(signal,fx.process(signal,48000,false,StudioSettings.Style.NONE,100,StudioSettings.Room.OFF,35)),"turning reverb off restores exact dry voice");
        }
        short[] stereo=new short[48000];for(int i=0;i<24000;i++)stereo[i*2]=signal[i];
        short[] result=new ChannelEffects().process(stereo,48000,true,StudioSettings.Style.ROBOT,90,StudioSettings.Room.HALL,30);
        check(java.util.stream.IntStream.range(0,24000).allMatch(i->result[i*2+1]==0),"left-only music does not leak into right channel");
        PreviewDelay delay=new PreviewDelay();short[] impulse=new short[960];impulse[0]=10000;
        check(energy(delay.process(impulse,48000,false,1,70))==0,"preview never plays immediately");
        for(int i=1;i<50;i++)check(energy(delay.process(new short[960],48000,false,1,70))==0,"preview buffers until selected delay");
        check(delay.process(new short[960],48000,false,1,70)[0]==7000,"preview starts exactly one second later at local-only gain");
        delay.process(impulse,48000,false,1,70);
        check(energy(delay.process(new short[960],48000,false,2,70))==0,"changing preview delay discards old queued voice");
        delay.clear();check(energy(delay.process(new short[960],44100,true,10,100))==0,"preview restart and device-format changes start silent");
        check(delay.process(impulse,48000,false,0,70)[0]==7000,"no-delay audition plays immediately at local-only gain");
        check(energy(delay.process(new short[960],48000,false,1,70))==0,"switching from no-delay to delayed audition starts fresh");
        check(energy(delay.process(impulse,0,false,1,70))==0,"temporarily invalid device sample rate clears preview safely");
        check(energy(delay.process(new short[960],48000,false,1,70))==0,"valid device format resumes preview with a fresh delay buffer");
        Path config=Files.createTempDirectory("studio-test").resolve("studio.properties");
        StudioSettings options=StudioSettings.defaults().voice(StudioSettings.Style.CHILD,65,StudioSettings.Room.STUDIO,32).music(StudioSettings.Style.NONE,80,StudioSettings.Room.SMALL,12).preview(3,60,false);
        options.save(config);check(options.equals(StudioSettings.read(config)),"voice/music presets and delayed preview settings persist independently");
        Files.writeString(config,"voice.style=BABY\nmusic.style=ALIEN\nvoice.room=STUDIO\npreview.delay=0\n");
        StudioSettings migrated=StudioSettings.read(config);
        check(migrated.voiceStyle()==StudioSettings.Style.NONE&&migrated.musicStyle()==StudioSettings.Style.NONE&&migrated.voiceRoom()==StudioSettings.Room.STUDIO&&migrated.previewDelay()==0,"legacy presets are removed while singing reverb and audition settings survive");
        Files.delete(config);Files.delete(config.getParent());
        System.out.println("STUDIO EFFECTS TESTS PASSED");
    }
}
