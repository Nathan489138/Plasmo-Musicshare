package local.plasmomusic;
/** Bounded sample-accurate delay, applied exclusively before native local loopback playback. */
final class PreviewDelay {
    private short[] ring=new short[0];private int rate,channels,seconds,index;
    synchronized short[] process(short[] samples,int nextRate,boolean stereo,int nextSeconds,int volume){
        if(nextRate<=0){clear();return new short[samples.length];}
        volume=Math.max(0,Math.min(100,volume));
        int nextChannels=stereo?2:1;nextSeconds=Math.max(0,Math.min(10,nextSeconds));
        if(rate!=nextRate||channels!=nextChannels||seconds!=nextSeconds){
            rate=nextRate;channels=nextChannels;seconds=nextSeconds;index=0;ring=new short[rate*channels*seconds];
        }
        short[] out=new short[samples.length];
        if(nextSeconds==0){for(int i=0;i<samples.length;i++)out[i]=(short)Math.round(samples[i]*volume/100.0);return out;}
        for(int i=0;i<samples.length;i++){out[i]=(short)Math.round(ring[index]*volume/100.0);ring[index]=samples[i];index=(index+1)%ring.length;}
        return out;
    }
    synchronized void clear(){ring=new short[0];rate=channels=seconds=index=0;}
}
