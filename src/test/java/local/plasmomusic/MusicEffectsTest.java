package local.plasmomusic;

/** Checks that optional effects stay out of the original path and remain bounded. */
public final class MusicEffectsTest {
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    private static double rms(short[] samples,int start){
        double sum=0;for(int i=start;i<samples.length;i++){double x=samples[i]/32768.0;sum+=x*x;}
        return Math.sqrt(sum/(samples.length-start));
    }
    private static double rms(float[] samples,int start){
        double sum=0;for(int i=start;i<samples.length;i++)sum+=samples[i]*samples[i];
        return Math.sqrt(sum/(samples.length-start));
    }
    private static int crossings(short[] samples,int begin,int end){
        int count=0;for(int i=begin+1;i<end;i++)if(samples[i-1]<=0&&samples[i]>0)count++;
        return count;
    }
    public static void main(String[] args) throws Exception {
        EffectSettings defaults=EffectSettings.defaults();
        check(!MusicEffects.active(defaults),"all effects default to bypass");
        var config=java.nio.file.Path.of("build/test-effects.properties");
        EffectSettings chosen=defaults.withClean(true).withPitch(-3);
        chosen.save(config);
        check(chosen.equals(EffectSettings.read(config)),"effects survive restart independently of F8 settings");
        java.nio.file.Files.writeString(config,"clean=true\nlimiter=false\npitch=-3\nreverb=true\nreverbMix=60\nradio=true\necho=true\n");
        check(chosen.equals(EffectSettings.read(config)),"removed legacy effects are ignored on upgrade");
        chosen.save(config);
        String saved=java.nio.file.Files.readString(config);
        check(!saved.contains("reverb")&&!saved.contains("radio")&&!saved.contains("echo"),"removed effects are not written back");
        MusicEffects limiter=new MusicEffects();
        float[] loud=new float[4800];java.util.Arrays.fill(loud,1.5f);
        short[] limited=limiter.process(loud,48000,false,defaults.withLimiter(true));
        int peak=0;for(short s:limited)peak=Math.max(peak,Math.abs((int)s));
        check(peak<32200&&peak>30000,"music peaks softened without overflow");

        MusicEffects clean=new MusicEffects();
        float[] harsh=new float[4800];for(int i=0;i<harsh.length;i++)harsh[i]=(float)(.5*Math.sin(2*Math.PI*18000*i/48000));
        short[] filtered=clean.process(harsh,48000,false,defaults.withClean(true));
        check(rms(filtered,2400)<.2,"harsh highs reduced");

        MusicEffects pitch=new MusicEffects();
        EffectSettings raised=defaults.withPitch(2);
        short[] shifted=new short[48000*2];
        for(int offset=0;offset<shifted.length;offset+=960){
            float[] sine=new float[960];
            for(int i=0;i<sine.length;i++)sine[i]=(float)(.4*Math.sin(2*Math.PI*440*(offset+i)/48000));
            short[] part=pitch.process(sine,48000,false,raised);
            System.arraycopy(part,0,shifted,offset,part.length);
        }
        int cycles=crossings(shifted,48000,96000);
        check(cycles>450&&cycles<550,"pitch rises roughly two semitones without speeding playback: "+cycles);

        float[] high=new float[9600];
        for(int i=0;i<4800;i++){
            float value=(float)(.6*Math.sin(2*Math.PI*32000*i/96000));
            high[2*i]=high[2*i+1]=value;
        }
        AudioBuffer ordinary=new AudioBuffer(),quality=new AudioBuffer();
        ordinary.offer(high,96000);quality.offer(high,96000);
        double alias=rms(ordinary.takeFloat(4500,48000,false,1,false),200);
        double protectedRms=rms(quality.takeFloat(4500,48000,false,1,true),200);
        check(protectedRms<alias*.35,"anti-aliasing suppresses downsampled high frequencies");
        System.out.println("MUSIC EFFECTS TESTS PASSED");
    }
}
