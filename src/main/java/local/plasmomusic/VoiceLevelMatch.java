package local.plasmomusic;

/** Bounded, continuous RMS compensation for voice timbre loss, before delay/reverb. */
final class VoiceLevelMatch {
    private final double envelope,smoothing;
    private double reference,processed,gain=1;
    VoiceLevelMatch(int rate){envelope=1-Math.exp(-1.0/(rate*.06));smoothing=1-Math.exp(-1.0/(rate*.02));}
    float sample(float dry,float wet){
        reference+=envelope*(dry*dry-reference);
        processed+=envelope*(wet*wet-processed);
        // Do not raise the noise floor in a silent input or divide by empty FFT startup windows.
        if(reference>1e-10&&processed>1e-12){
            double desired=Math.max(.25,Math.min(32,Math.sqrt(reference/processed)));
            gain+=smoothing*(desired-gain);
        }
        return (float)(wet*gain);
    }
}
