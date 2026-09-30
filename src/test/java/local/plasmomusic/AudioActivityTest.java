package local.plasmomusic;

/** Exercises transitions that previously left the self icon on during silence. */
public final class AudioActivityTest {
    private static short[] frame(int amplitude) {
        short[] samples=new short[960];
        for(int i=0;i<samples.length;i++) samples[i]=(short)((i&1)==0?amplitude:-amplitude);
        return samples;
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        AudioActivity activity=new AudioActivity();
        long now=1_000_000_000L;
        for(int i=0;i<20;i++) {activity.accept(frame(0),now);now+=20_000_000L;}
        check(!activity.iconActive(now,-34),"silent packets must not show the icon");
        activity.accept(frame(600),now);now+=20_000_000L;
        check(!activity.iconActive(now,-34),"audio below slider threshold must not show the icon");
        activity.accept(frame(5000),now);now+=20_000_000L;
        check(activity.iconActive(now,-34),"audio above slider threshold must show the icon");
        check(!activity.iconActive(now,-25),"moving the PV slider hides an unchanged frame immediately");
        check(activity.iconActive(now,-40),"lowering the PV slider restores an unchanged frame immediately");
        activity.accept(frame(0),now);now+=20_000_000L;
        check(!activity.iconActive(now,-60),"first silent frame must hide the icon without a release hold");

        activity.accept(frame(5000),now);now+=20_000_000L;
        check(activity.decibels(now)<-16,"attack must not jump to the raw packet level");
        for(int i=0;i<20;i++) {activity.accept(frame(5000),now);now+=20_000_000L;}
        check(activity.iconActive(now,-30),"audible speech or music must show the icon");

        activity.accept(frame(0),now);now+=20_000_000L;
        check(!activity.iconActive(now,-60),"icon must disappear on the first silent frame");
        for(int i=0;i<35;i++) {activity.accept(frame(0),now);now+=20_000_000L;}
        check(!activity.iconActive(now,-60),"icon must disappear after the sound fades");
        activity.accept(frame(5000),now);
        check(!activity.iconActive(now+300_000_000L,-60),"stale frames must never keep the icon on");
        short[] leftOnly=new short[1920];
        for(int i=0;i<960;i++)leftOnly[i*2]=5000;
        activity.accept(leftOnly,true,now);
        check(activity.iconActive(now,-30),"stereo music on one channel follows the same PV slider threshold");
        java.util.Random random=new java.util.Random(127);
        for(int frames:new int[]{960,960,480,1920,960}){
            short[] interleaved=new short[frames*2],left=new short[frames],right=new short[frames];
            for(int i=0;i<frames;i++){
                left[i]=(short)(random.nextInt(10001)-5000);right[i]=(short)(random.nextInt(10001)-5000);
                interleaved[i*2]=left[i];interleaved[i*2+1]=right[i];
            }
            double nativeLevel=Math.max(su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(left),
                su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(right));
            activity.accept(interleaved,true,now);
            check(activity.iconActive(now,nativeLevel-.000001)&&!activity.iconActive(now,nativeLevel+.000001),
                "stereo frame resizing must preserve exact native PV slider decisions");
            activity.accept(new short[frames*2],true,now);
            check(!activity.iconActive(now,-60),"reused channel buffers must never retain previous audio");
        }
        activity.clear();
        check(activity.decibels(now)==-127,"stopping sharing must reset the meter");
        System.out.println("Audio activity transitions OK");
    }
}
