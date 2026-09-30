package local.plasmomusic;

import java.io.IOException;
import java.util.*;
import su.plo.voice.api.util.AudioUtil;

/** Replaces microphone samples only. Music never passes through this processor. */
public final class VoiceCensor {
    private static final int SOURCE_RATE=48000;
    private static final short[] sound=loadSound();
    private final Map<UUID,Double> positions=new HashMap<>();
    private static short[] loadSound(){
        try(var in=VoiceCensor.class.getResourceAsStream("/assets/plasmo-system-music/censor/bi.pcm")){
            if(in==null)throw new IOException("Missing censor sound");
            byte[] bytes=in.readAllBytes();
            if(bytes.length<2||(bytes.length&1)!=0)throw new IOException("Invalid censor sound");
            short[] sound=new short[bytes.length/2];
            for(int i=0;i<sound.length;i++)sound[i]=(short)((bytes[i*2]&255)|(bytes[i*2+1]<<8));
            return sound;
        }catch(IOException e){throw new IllegalStateException("Cannot load censor sound",e);}
    }
    public synchronized short[] replace(UUID channel,short[] microphone,int rate,boolean activated,double threshold){
        short[] output=new short[microphone.length];
        if(!activated||AudioUtil.calculateHighestAudioLevel(microphone)<threshold){positions.remove(channel);return output;}
        double position=positions.getOrDefault(channel,0.0),step=(double)SOURCE_RATE/rate;
        for(int i=0;i<output.length;i++){
            int index=(int)position;double fraction=position-index;
            output[i]=(short)Math.round(sound[index]*(1-fraction)+sound[(index+1)%sound.length]*fraction);
            position+=step;if(position>=sound.length)position%=sound.length;
        }
        positions.put(channel,position);return output;
    }
    public synchronized void reset(){positions.clear();}
    public synchronized void reset(UUID channel){positions.remove(channel);}
}
