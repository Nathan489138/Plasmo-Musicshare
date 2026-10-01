package local.plasmomusic;
import java.util.Properties;

/** Only the voice channel consumes this panel; all processing defaults to bypass. */
public record DspSettings(boolean flanger,int flangerWet,int flangerRate,int flangerDepth,
                          boolean delay,int delayGain,int pitch,Filter filter,int cutoff,
                          boolean stretch,int speed,boolean robot,boolean ancestor){
    public enum Filter {OFF("关闭"),LOW("低通 · 柔和"),HIGH("高通 · 清薄"),BAND("带通 · 窄频");public final String label;Filter(String label){this.label=label;}}
    public DspSettings{flangerWet=bound(flangerWet,0,100);flangerRate=bound(flangerRate,10,300);flangerDepth=bound(flangerDepth,1,15);delayGain=bound(delayGain,0,250);pitch=bound(pitch,-12,12);cutoff=bound(cutoff,100,12000);speed=bound(speed,75,150);}
    private static int bound(int n,int lo,int hi){return Math.max(lo,Math.min(hi,n));}
    public static DspSettings defaults(){return new DspSettings(false,40,50,6,false,100,0,Filter.OFF,4000,false,100,false,false);}
    public static DspSettings preset(StudioSettings.Style s){return switch(s){
        case ECHO->new DspSettings(false,40,50,6,true,100,0,Filter.OFF,4000,false,100,false,false);
        case ROBOT->new DspSettings(true,25,100,4,false,100,0,Filter.LOW,6500,false,100,true,false);
        case CHILD->new DspSettings(false,40,50,6,false,100,4,Filter.LOW,8500,false,100,false,false);
        case VILLAIN->new DspSettings(false,40,50,6,true,55,-5,Filter.LOW,4500,false,100,false,false);
        case UNDERWATER->new DspSettings(false,40,50,6,false,100,0,Filter.LOW,650,false,100,false,false);
        default->defaults();};}
    public boolean active(){return flanger&&flangerWet>0||delay&&delayGain>0||pitch!=0||filter!=Filter.OFF||stretch&&speed!=100||robot||ancestor;}
    private static int n(Properties p,String key,int fallback){try{return Integer.parseInt(p.getProperty("dsp."+key));}catch(Exception e){return fallback;}}
    private static boolean b(Properties p,String key,boolean fallback){return Boolean.parseBoolean(p.getProperty("dsp."+key,""+fallback));}
    static DspSettings read(Properties p,StudioSettings.Style style){
        DspSettings d=preset(style);Filter f=d.filter;try{f=Filter.valueOf(p.getProperty("dsp.filter"));}catch(Exception ignored){}
        return new DspSettings(b(p,"flanger",d.flanger),n(p,"flanger.wet",d.flangerWet),n(p,"flanger.rate",d.flangerRate),n(p,"flanger.depth",d.flangerDepth),b(p,"delay",d.delay),n(p,"delay.gain",d.delayGain),n(p,"pitch",d.pitch),f,n(p,"cutoff",d.cutoff),b(p,"stretch",d.stretch),n(p,"speed",d.speed),b(p,"robot",d.robot),b(p,"ancestor",d.ancestor));
    }
    void write(Properties p){
        p.setProperty("dsp.flanger",""+flanger);p.setProperty("dsp.flanger.wet",""+flangerWet);p.setProperty("dsp.flanger.rate",""+flangerRate);p.setProperty("dsp.flanger.depth",""+flangerDepth);
        p.setProperty("dsp.delay",""+delay);p.setProperty("dsp.delay.gain",""+delayGain);p.setProperty("dsp.pitch",""+pitch);p.setProperty("dsp.filter",filter.name());p.setProperty("dsp.cutoff",""+cutoff);
        p.setProperty("dsp.stretch",""+stretch);p.setProperty("dsp.speed",""+speed);p.setProperty("dsp.robot",""+robot);p.setProperty("dsp.ancestor",""+ancestor);
    }
}
