package local.plasmomusic;

/** Fast Talking Heads level, separate from the slower icon gate and HUD meter. */
public final class AnimationLevel {
    private static final long STALE_NANOS=250_000_000L;
    private double musicFast, musicBaseline, shown=-127;
    private long lastFrame;

    public static double decibels(short[] samples){
        if(samples==null||samples.length==0)return -127;
        double sum=0;
        for(short sample:samples){double value=sample/32768.0;sum+=value*value;}
        double rms=Math.sqrt(sum/samples.length);
        return rms>0?Math.max(-127,20*Math.log10(rms)):-127;
    }

    synchronized void accept(double musicDb,double microphoneDb,long now){
        if(musicDb<=-55){
            // Silence removes the animation promptly.
            shown=microphoneDb>-38?Math.min(-3,microphoneDb):-127;
            musicFast=musicBaseline=-127;
            lastFrame=now;
            return;
        }
        if(lastFrame==0||now-lastFrame>STALE_NANOS||musicBaseline<=-55){
            musicFast=musicBaseline=musicDb;
        }else{
            musicFast+= (musicDb-musicFast)*(musicDb>musicFast?.72:.48);
            musicBaseline+=(musicDb-musicBaseline)*.025;
        }
        // Talking Heads' native API accepts raw dB and uses its own -40 dB gate.
        // Enhance differences around a song's recent loudness without flattening peaks.
        double animatedMusic=Math.max(-39.5,Math.min(-3,musicFast+2.2*(musicFast-musicBaseline)+3));
        // Once actual speech is audible, let the uncompressed microphone frame drive
        // the head just as Talking Heads does for ordinary Plasmo Voice speech.
        shown=microphoneDb>-38?Math.min(-3,microphoneDb):animatedMusic;
        lastFrame=now;
    }

    synchronized double decibels(long now){
        return lastFrame!=0&&now-lastFrame<STALE_NANOS?shown:-127;
    }
    synchronized void clear(){musicFast=musicBaseline=0;shown=-127;lastFrame=0;}
}
