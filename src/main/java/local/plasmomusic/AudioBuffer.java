package local.plasmomusic;

/** Bounded stereo FIFO with linear resampling; never waits on the voice capture thread. */
public final class AudioBuffer {
    private final float[] data=new float[384000*2];
    private long read,write; private double phase; private int rate=48000;
    private long lastWrite;
    public volatile float peak;
    synchronized long availableFramesForTest(){return write-read;}
    public synchronized void clear() {read=write=0;phase=0;lastWrite=0;peak=0;}
    public synchronized void offer(float[] values,int sampleRate) {
        if(rate!=sampleRate) {clear();rate=sampleRate;}
        float p=0;
        for(int i=0;i<values.length/2;i++) {
            int index=(int)(write++%(data.length/2))*2;
            data[index]=values[i*2];data[index+1]=values[i*2+1];
            p=Math.max(p,Math.max(Math.abs(values[i*2]),Math.abs(values[i*2+1])));
        }
        // Drop backlog instead of replaying old music after game pauses or clock drift.
        if(write-read>rate/5) {read=write-rate/12;phase=0;}
        lastWrite=System.nanoTime();peak=p;
    }
    public synchronized short[] take(int frames,int targetRate,boolean stereo,float gain) {
        short[] out=new short[frames*(stereo?2:1)];
        if(System.nanoTime()-lastWrite>300_000_000L) {read=write;phase=0;peak=0;return out;}
        double step=(double)rate/targetRate;
        for(int i=0;i<frames;i++) {
            if(read+1>=write) break;
            int a=(int)(read%(data.length/2))*2, b=(int)((read+1)%(data.length/2))*2;
            float l=(float)(data[a]+(data[b]-data[a])*phase),r=(float)(data[a+1]+(data[b+1]-data[a+1])*phase);
            if(stereo) {out[i*2]=pcm(l*gain);out[i*2+1]=pcm(r*gain);} else out[i]=pcm((l+r)*.5f*gain);
            phase+=step; int skip=(int)phase;read+=skip;phase-=skip;
            if(read>write) {read=write;phase=0;}
        }
        return out;
    }
    /** Float path for optional effects: retain peaks above 0 dB until the limiter runs. */
    public synchronized float[] takeFloat(int frames,int targetRate,boolean stereo,float gain,boolean antiAlias) {
        float[] out=new float[frames*(stereo?2:1)];
        if(System.nanoTime()-lastWrite>300_000_000L) {read=write;phase=0;peak=0;return out;}
        double step=(double)rate/targetRate;
        double cutoff=Math.min(1.0,1.0/step)*.94;
        for(int i=0;i<frames;i++) {
            if(read+1>=write)break;
            float l,r;
            if(antiAlias&&step>1.0){
                double left=0,right=0,weight=0;
                int taps=16;
                for(int k=-7;k<=8;k++){
                    double distance=k-phase;
                    double normalized=distance/8.0;
                    double window=.42+.5*Math.cos(Math.PI*normalized)+.08*Math.cos(2*Math.PI*normalized);
                    double z=Math.PI*cutoff*distance;
                    double sinc=Math.abs(z)<1e-9?1:Math.sin(z)/z;
                    double coefficient=cutoff*sinc*window;
                    long frame=read+k;
                    if(frame<0||frame>=write)continue;
                    int index=(int)(frame%(data.length/2))*2;
                    left+=data[index]*coefficient;right+=data[index+1]*coefficient;weight+=coefficient;
                }
                l=weight!=0?(float)(left/weight):0;r=weight!=0?(float)(right/weight):0;
            }else{
                int a=(int)(read%(data.length/2))*2,b=(int)((read+1)%(data.length/2))*2;
                l=(float)(data[a]+(data[b]-data[a])*phase);
                r=(float)(data[a+1]+(data[b+1]-data[a+1])*phase);
            }
            if(stereo){out[i*2]=l*gain;out[i*2+1]=r*gain;}else out[i]=(l+r)*.5f*gain;
            phase+=step;int skip=(int)phase;read+=skip;phase-=skip;
            if(read>write){read=write;phase=0;}
        }
        return out;
    }
    static short pcm(float x) {return (short)Math.max(-32768,Math.min(32767,Math.round(x*32768)));}
    public static void mix(short[] music,short[] mono,boolean stereo) {
        for(int i=0;i<music.length;i++) {
            int j=stereo?i/2:i;
            if(j<mono.length) music[i]=(short)Math.max(-32768,Math.min(32767,music[i]+(int)mono[j]));
        }
    }
}
