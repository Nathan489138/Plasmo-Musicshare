package local.plasmomusic;
import java.util.*;
public final class VoiceCensorTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        UUID id=UUID.randomUUID();short[] loud=new short[960];Arrays.fill(loud,(short)20000);
        double threshold=su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(loud);
        VoiceCensor censor=new VoiceCensor();short[] first=censor.replace(id,loud,48000,true,threshold);
        check(Arrays.stream(toInts(first)).anyMatch(x->x!=0),"equal threshold plays supplied beep");
        check(Arrays.stream(toInts(censor.replace(id,loud,48000,true,threshold+.01))).allMatch(x->x==0),"below threshold is silent");
        check(Arrays.equals(first,censor.replace(id,loud,48000,true,threshold)),"new audible burst restarts sound");
        check(Arrays.stream(toInts(censor.replace(id,loud,48000,false,threshold))).allMatch(x->x==0),"unactivated microphone never produces beep");
        short[] different=loud.clone();Arrays.fill(different,(short)-23000);
        check(Arrays.equals(new VoiceCensor().replace(id,loud,48000,true,-30),new VoiceCensor().replace(id,different,48000,true,-30)),"raw voice samples never leak into output");
        VoiceCensor split=new VoiceCensor(),whole=new VoiceCensor();
        short[] a=split.replace(id,loud,44100,true,-30),b=split.replace(id,loud,44100,true,-30);
        short[] longer=new short[1920];Arrays.fill(longer,(short)20000);
        short[] expected=whole.replace(id,longer,44100,true,-30);
        check(Arrays.equals(a,Arrays.copyOfRange(expected,0,960))&&Arrays.equals(b,Arrays.copyOfRange(expected,960,1920)),"resampling remains continuous across frames");
        for(int i=0;i<1000;i++)censor.replace(id,loud,48000,true,-30);
        System.out.println("VOICE CENSOR TESTS PASSED");
    }
    static int[] toInts(short[] samples){int[] ints=new int[samples.length];for(int i=0;i<ints.length;i++)ints[i]=samples[i];return ints;}
}
