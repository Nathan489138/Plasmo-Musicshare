package local.plasmomusic;

/** Smooth the displayed meter; gate the icon using PV's native audio-level calculation. */
final class AudioActivity {
    private static final long FRAME_TIMEOUT_NS = 250_000_000L;

    private double envelope;
    private double latestFrameDb = -127.0;
    private long lastFrameNanos;
    private short[] left=new short[0],right=new short[0];

    synchronized void accept(short[] samples, long now) {
        accept(samples,false,now);
    }

    synchronized void accept(short[] samples, boolean stereo, long now) {
        double sum = 0;
        for (short sample : samples) {
            double value = sample / 32768.0;
            sum += value * value;
        }
        double rms = samples.length == 0 ? 0 : Math.sqrt(sum / samples.length);
        if(stereo && samples.length>=2){
            int frames=samples.length/2;
            // PV divides each window by the full array length. Resize exactly on
            // format/frame-size changes so buffer reuse preserves its threshold.
            if(left.length!=frames){left=new short[frames];right=new short[frames];}
            for(int i=0;i<frames;i++){left[i]=samples[i*2];right[i]=samples[i*2+1];}
            latestFrameDb=Math.max(su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(left),
                su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(right));
        }else latestFrameDb=su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(samples);
        double elapsed = lastFrameNanos == 0 ? 0.02 : Math.max(0.005, Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0));
        double timeConstant = rms > envelope ? 0.07 : 0.22;
        envelope += (rms - envelope) * (1.0 - Math.exp(-elapsed / timeConstant));
        lastFrameNanos = now;

    }

    synchronized double decibels(long now) {
        return isFresh(now) ? decibels(envelope) : -127.0;
    }

    synchronized boolean iconActive(long now,double thresholdDb) {
        return isFresh(now) && latestFrameDb >= thresholdDb;
    }

    synchronized void clear() {
        envelope = 0;
        latestFrameDb = -127.0;
        lastFrameNanos = 0;
    }

    private boolean isFresh(long now) {
        return lastFrameNanos != 0 && now - lastFrameNanos < FRAME_TIMEOUT_NS;
    }

    private static double decibels(double rms) {
        return rms > 0 ? Math.max(-127.0, 20.0 * Math.log10(rms)) : -127.0;
    }
}
