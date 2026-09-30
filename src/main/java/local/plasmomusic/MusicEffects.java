package local.plasmomusic;

/** Stateful, music-only effects. Each direct music stream owns one instance. */
final class MusicEffects {
    private static final double TWO_PI=2*Math.PI;
    private int rate,channels;
    private long written;
    private double previousPitchRatio=1;
    private float[][] history;
    private int historyMask;
    private double phase,cleanMix,pitchMix;
    private double[] cleanLow,cleanLow2;

    static boolean active(EffectSettings s){
        return s.clean()||s.limiter()||s.pitch()!=0;
    }
    boolean hasTail(){return cleanMix>.0001||pitchMix>.0001;}
    private void ensure(int newRate,int newChannels){
        if(rate==newRate&&channels==newChannels)return;
        rate=newRate;channels=newChannels;written=0;phase=0;previousPitchRatio=1;
        cleanMix=pitchMix=0;
        cleanLow=new double[channels];cleanLow2=new double[channels];
        int size=1;while(size<rate/2)size<<=1;
        history=new float[channels][size];historyMask=size-1;
    }
    /** Keep pitch history warm while every optional effect is bypassed. */
    void observe(short[] input,int sampleRate,boolean stereo){
        ensure(sampleRate,stereo?2:1);
        for(int i=0;i<input.length/channels;i++){
            for(int c=0;c<channels;c++)history[c][(int)(written&historyMask)]=input[i*channels+c]/32768f;
            written++;
        }
    }
    short[] process(float[] input,int sampleRate,boolean stereo,EffectSettings settings){
        ensure(sampleRate,stereo?2:1);
        int frames=input.length/channels;
        short[] output=new short[input.length];
        if(settings.pitch()!=0)previousPitchRatio=Math.pow(2,settings.pitch()/12.0);
        double pitchRatio=previousPitchRatio;
        double phaseStep=Math.abs(pitchRatio-1)/Math.max(1,rate*.075);
        double cleanAlpha=1-Math.exp(-TWO_PI*10000/rate);
        double smooth=1-Math.exp(-1/(rate*.012));
        for(int i=0;i<frames;i++){
            cleanMix=approach(cleanMix,settings.clean()?1:0,smooth);
            pitchMix=approach(pitchMix,settings.pitch()!=0?1:0,smooth);
            for(int c=0;c<channels;c++)history[c][(int)(written&historyMask)]=input[i*channels+c];
            written++;
            for(int c=0;c<channels;c++){
                double x=input[i*channels+c];
                if(pitchMix>.0001){
                    double shifted=pitchSample(c,phase,pitchRatio);
                    x=x*(1-pitchMix)+shifted*pitchMix;
                }
                cleanLow[c]+=cleanAlpha*(x-cleanLow[c]);
                cleanLow2[c]+=cleanAlpha*(cleanLow[c]-cleanLow2[c]);
                x=x*(1-cleanMix)+cleanLow2[c]*cleanMix;
                if(settings.limiter())x=limit(x);
                output[i*channels+c]=AudioBuffer.pcm((float)x);
            }
            phase+=phaseStep;if(phase>=1)phase-=Math.floor(phase);
        }
        return output;
    }
    private static double approach(double current,double target,double alpha){return current+(target-current)*alpha;}
    private static double limit(double x){
        double a=Math.abs(x);
        if(a<=.82)return x;
        double softened=.82+.16*(1-Math.exp(-(a-.82)/.16));
        return Math.copySign(softened,x);
    }
    private double pitchSample(int channel,double p,double ratio){
        double other=p+.5;if(other>=1)other-=1;
        double w=.5-.5*Math.cos(TWO_PI*p);
        return readTap(channel,p,ratio)*w+readTap(channel,other,ratio)*(1-w);
    }
    private double readTap(int channel,double p,double ratio){
        double min=rate*.025,range=rate*.075;
        double delay=ratio>1?min+(1-p)*range:min+p*range;
        double at=written-1-delay;
        if(at<0)return 0;
        long start=(long)Math.floor(at);double fraction=at-start;
        float[] ring=history[channel];
        return ring[(int)(start&historyMask)]*(1-fraction)+ring[(int)((start+1)&historyMask)]*fraction;
    }
}
