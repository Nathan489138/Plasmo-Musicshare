package local.plasmomusic;
import be.tarsos.dsp.*;
import be.tarsos.dsp.effects.*;
import be.tarsos.dsp.filters.*;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

/** Voice-only TarsosDSP rack. Dry speech stays immediate in the ancestor preset. */
final class CharacterEffects {
    private int rate,channels,tailSamples,echoMs,feedback;private long written;
    private boolean enabled;private DspSettings config;
    private PitchStream[] pitch;private TempoStream[] tempo;
    private DelayEffect[] echoes;private FlangerStream[] flangers;private IIRFilter[] filters;
    private float[][] behind;private int behindIndex;private double phase;
    private double[] dryPower,deepPower,referencePower;
    short[] process(short[] input,int rate,boolean stereo,StudioSettings.Style style,int strength){
        return process(input,rate,stereo,strength,320,40,DspSettings.preset(style));
    }
    short[] processVoice(short[] input,int rate,boolean stereo,StudioSettings s){
        return process(input,rate,stereo,s.voiceStrength(),s.echoDelay(),s.echoFeedback(),s.dsp());
    }
    private short[] process(short[] input,int nextRate,boolean stereo,int strength,int ms,int decay,DspSettings d){
        int count=stereo?2:1;
        if(rate!=nextRate||channels!=count||!d.equals(config)||echoMs!=ms||feedback!=decay||enabled!=(strength>0)){
            rate=nextRate;channels=count;config=d;echoMs=ms;feedback=decay;enabled=strength>0;tailSamples=0;phase=0;written=0;behindIndex=0;
            pitch=new PitchStream[count];tempo=new TempoStream[count];echoes=new DelayEffect[count];flangers=new FlangerStream[count];filters=new IIRFilter[count];
            behind=new float[count][Math.max(1,rate*ms/1000)];dryPower=new double[count];deepPower=new double[count];referencePower=new double[count];
            for(int c=0;c<count;c++){
                if(d.pitch()!=0)pitch[c]=new PitchStream(Math.pow(2,d.pitch()/12.0),rate);
                if(d.stretch()&&d.speed()!=100)tempo[c]=new TempoStream(rate,d.speed());
                if(d.delay())echoes[c]=new DelayEffect(ms/1000.0,decay/100.0,rate);
                if(d.flanger())flangers[c]=new FlangerStream(d,rate);
                float frequency=Math.min(d.cutoff(),rate*.4f);
                filters[c]=switch(d.filter()){case LOW->new LowPassFS(frequency,rate);case HIGH->new HighPass(frequency,rate);case BAND->new BandPass(frequency,Math.min(frequency*.8f,rate*.15f),rate);default->null;};
            }
        }
        if(strength==0||!d.active()){tailSamples=0;return input;}
        int frames=input.length/count;float[][] processed=new float[count][frames];
        boolean active=false;
        for(int i=0;i<frames;i++){
            for(int c=0;c<count;c++){
                float x=input[i*count+c]/32768f;if(Math.abs(x)>.00008)active=true;
                if(tempo[c]!=null)x=tempo[c].sample(x);
                if(pitch[c]!=null)x=pitch[c].sample(x);
                if(d.robot())x=(float)(.15*x+.85*Math.tanh(x*Math.sin(phase)*2)/1.5);
                processed[c][i]=x;
            }
            phase+=2*Math.PI*75/rate;if(phase>=2*Math.PI)phase-=2*Math.PI;
        }
        for(int c=0;c<count;c++){
            AudioEvent event=new AudioEvent(new TarsosDSPAudioFormat(rate,16,1,true,false));event.setFloatBuffer(processed[c]);event.setBytesProcessed(written*2);
            if(filters[c]!=null)filters[c].process(event);
            if(flangers[c]!=null)for(int i=0;i<frames;i++)processed[c][i]=flangers[c].sample(processed[c][i]);
        }
        // A delayed lower-register branch: no immediate low voice is mixed into the original.
        if(d.ancestor()){
            for(int i=0;i<frames;i++){
                for(int c=0;c<count;c++){
                    double dry=input[i*count+c]/32768.0,x=processed[c][i];
                    dryPower[c]+=.0005*(dry*dry-dryPower[c]);deepPower[c]+=.0005*(x*x-deepPower[c]);
                    if(Math.abs(dry)>.00008)referencePower[c]=dryPower[c];
                    double norm=Math.max(.25,Math.min(4,Math.sqrt(referencePower[c]/Math.max(.00000001,deepPower[c]))));
                    float delayed=behind[c][behindIndex];behind[c][behindIndex]=(float)(x*norm);processed[c][i]=delayed;
                }
                behindIndex=(behindIndex+1)%behind[0].length;
            }
        }
        float[][] preDelay=new float[count][];
        for(int c=0;c<count;c++){
            preDelay[c]=processed[c].clone();
            if(echoes[c]!=null){AudioEvent e=new AudioEvent(new TarsosDSPAudioFormat(rate,16,1,true,false));e.setFloatBuffer(processed[c]);echoes[c].process(e);}
        }
        short[] output=new short[input.length];double wet=strength/100.0;
        for(int i=0;i<frames;i++)for(int c=0;c<count;c++){
            double dry=input[i*count+c]/32768.0,x=processed[c][i];
            if(d.ancestor())x=dry+(d.delay()?x*d.delayGain()/100.0:0);
            else if(d.delay())x=preDelay[c][i]+(x-preDelay[c][i])*d.delayGain()/100.0;
            output[i*count+c]=AudioBuffer.pcm((float)(dry*(1-wet)+x*wet));
        }
        written+=frames;
        if(active){
            double duration=d.delay()?ms/1000.0*(Math.ceil(Math.log(.00008)/Math.log(decay/100.0))+(d.ancestor()?1:0))+.15:.15;
            if(tempo[0]!=null)duration+=.65;
            tailSamples=(int)(duration*rate);
        }else tailSamples=Math.max(0,tailSamples-frames);
        return output;
    }
    boolean hasTail(){return tailSamples>0;}

    /** Fixed blocks keep the native effect's timestamp independent of capture frame size. */
    private static final class FlangerStream {
        private final FlangerEffect effect;private final AudioEvent event;
        private final float[] input=new float[256],output=new float[256];private int index;private long samples;
        FlangerStream(DspSettings d,int rate){effect=new FlangerEffect(d.flangerDepth()/1000.0,d.flangerWet()/100.0,rate,d.flangerRate()/100.0);event=new AudioEvent(new TarsosDSPAudioFormat(rate,16,1,true,false));}
        float sample(float value){float result=output[index];input[index++]=value;
            if(index==256){event.setBytesProcessed(samples*2);event.setFloatBuffer(input);effect.process(event);System.arraycopy(input,0,output,0,256);samples+=256;index=0;}return result;}
    }


    /** Adapt arbitrary capture frames to the overlapped windows required by PitchShifter. */
    private static final class PitchStream {
        private static final int SIZE=2048,HOP=256;
        private final PitchShifter shifter;private final AudioEvent event;
        private final float[] history=new float[SIZE],output=new float[HOP];
        private int index,pending,read;
        PitchStream(double factor,int rate){shifter=new PitchShifter(factor,rate,SIZE,SIZE-HOP);event=new AudioEvent(new TarsosDSPAudioFormat(rate,16,1,true,false));event.setOverlap(SIZE-HOP);}
        float sample(float input){
            float result=output[read];output[read]=0;read=(read+1)%HOP;
            history[index]=input;index=(index+1)%SIZE;
            if(++pending==HOP){
                pending=0;float[] window=new float[SIZE];
                for(int i=0;i<SIZE;i++)window[i]=history[(index+i)%SIZE];
                event.setFloatBuffer(window);shifter.process(event);
                System.arraycopy(event.getFloatBuffer(),SIZE-HOP,output,0,HOP);read=0;
            }
            return result;
        }
    }
}
