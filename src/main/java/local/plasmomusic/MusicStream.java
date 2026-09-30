package local.plasmomusic;

/** Direct PCM stream for Plasmo Voice's proximity encoder; no Soundboard classes. */
final class MusicStream {
    private final AudioBuffer buffer;
    private final SingingReverb reverb=new SingingReverb();

    MusicStream(AudioBuffer buffer){this.buffer=buffer;}

    short[] read(int frames,int rate,boolean stereo,Settings settings,EffectSettings options){
        return read(frames,rate,stereo,settings,options,StudioSettings.defaults());
    }
    short[] read(int frames,int rate,boolean stereo,Settings settings,EffectSettings options,StudioSettings studio){
        float gain=settings.volume()/100f;
        short[] dry=buffer.take(frames,rate,stereo,gain);
        return reverb.process(dry,rate,stereo,studio.musicRoom(),studio.musicReverb());
    }
}
