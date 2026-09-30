package local.plasmomusic;
import java.util.*;

public final class DspPanelTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    private static short[] tone(int count,int hz){short[] data=new short[count];for(int i=0;i<count;i++)data[i]=(short)(5000*Math.sin(2*Math.PI*hz*i/48000));return data;}
    private static double rms(short[] data,int from,int to){double sum=0;for(int i=from;i<to;i++)sum+=(double)data[i]*data[i];return Math.sqrt(sum/(to-from));}
    private static short[] run(short[] input,DspSettings d){return new ChannelEffects().processVoice(input,48000,false,StudioSettings.defaults().panel(d));}
    public static void main(String[] args)throws Exception{
        short[] dry=tone(24000,220);
        check(Arrays.equals(dry,run(dry,DspSettings.defaults())),"DSP panel bypass preserves exact original PCM");
        DspSettings flanger=new DspSettings(true,60,70,7,false,100,0,DspSettings.Filter.OFF,4000,false,100,false,false);
        check(!Arrays.equals(dry,run(dry,flanger)),"native TarsosDSP flanger changes voice");
        ChannelEffects whole=new ChannelEffects(),split=new ChannelEffects();StudioSettings settings=StudioSettings.defaults().panel(flanger);
        short[] a=whole.processVoice(dry,48000,false,settings),b=new short[dry.length];
        for(int i=0;i<dry.length;i+=960)System.arraycopy(split.processVoice(Arrays.copyOfRange(dry,i,i+960),48000,false,settings),0,b,i,960);
        int delta=0;for(int i=0;i<a.length;i++)delta=Math.max(delta,Math.abs(a[i]-b[i]));
        check(delta<=1,"flanger LFO stays continuous across capture frame boundaries");
        for(DspSettings.Filter filter:List.of(DspSettings.Filter.LOW,DspSettings.Filter.HIGH,DspSettings.Filter.BAND)){
            int frequency=filter==DspSettings.Filter.HIGH?220:8000;
            short[] in=tone(24000,frequency);
            short[] out=run(in,new DspSettings(false,40,50,6,false,100,0,filter,1000,false,100,false,false));
            check(rms(out,12000,24000)<rms(in,12000,24000)*.5,"TarsosDSP "+filter+" rejects frequencies outside its pass band");
        }
        for(int speed:List.of(75,150)){
            TempoStream tempo=new TempoStream(48000,speed);float peak=0;
            for(int i=0;i<48000*20;i++){float x=tempo.sample((float)(.15*Math.sin(2*Math.PI*220*i/48000)));if(!Float.isFinite(x))throw new AssertionError("nonfinite WSOLA output");peak=Math.max(peak,Math.abs(x));if(tempo.queuedForTest()>24000)throw new AssertionError("unbounded tempo queue");}
            check(peak>.05,"WSOLA "+speed+"% processes live speech with a bounded half-second queue");
        }
        short[] stretched=run(dry,new DspSettings(false,40,50,6,false,100,0,DspSettings.Filter.OFF,4000,true,75,false,false));
        int crossings=0;for(int i=10001;i<20000;i++)if(stretched[i-1]<=0&&stretched[i]>0)crossings++;
        double hz=crossings*48000.0/9999;
        check(hz>205&&hz<235,"time stretching changes timing while preserving approximately 220 Hz pitch");
        short[] speech=new short[48000];System.arraycopy(dry,0,speech,0,dry.length);
        StudioSettings ancestor=StudioSettings.defaults().select(StudioSettings.Style.ANCESTOR);
        short[] output=new ChannelEffects().processVoice(speech,48000,false,ancestor);
        check(Arrays.equals(Arrays.copyOf(output,15360),Arrays.copyOf(speech,15360)),"ancestor preserves ordinary immediate voice before the delayed echo");
        check(rms(output,26000,34000)>rms(dry,12000,20000)*1.2,"ancestor low echo after speech is measurably louder than original voice");
        check(ancestor.dsp().delayGain()==180&&ancestor.dsp().pitch()<0,"ancestor preset selects louder low-register delayed branch");
        java.nio.file.Path path=java.nio.file.Files.createTempDirectory("dsp-panel").resolve("settings.properties");
        settings.save(path);check(settings.equals(StudioSettings.read(path)),"custom DSP panel settings survive save and reload");
        java.nio.file.Files.delete(path);java.nio.file.Files.delete(path.getParent());
    }
}
