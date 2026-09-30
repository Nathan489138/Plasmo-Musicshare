package local.plasmomusic;

/** Compare musical contrast and speech response with Talking Heads' native dB input. */
public final class AnimationLevelTest {
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    public static void main(String[] args){
        AnimationLevel level=new AnimationLevel();
        long now=1_000_000_000L;
        for(int i=0;i<30;i++){level.accept(-22,-127,now);now+=20_000_000L;}
        double base=level.decibels(now);
        level.accept(-14,-127,now);now+=20_000_000L;
        double beat=level.decibels(now);
        check(beat>base+6,"music beat is visibly stronger than its steady baseline");
        level.accept(-30,-127,now);now+=20_000_000L;
        double quiet=level.decibels(now);
        check(quiet<beat-8,"quiet music frame releases promptly rather than staying flat");

        level.accept(-10,-23,now);now+=20_000_000L;
        check(Math.abs(level.decibels(now)+23)<.001,"speech follows native raw microphone dB even over louder music");
        level.accept(-10,-127,now);now+=20_000_000L;
        check(level.decibels(now)>-23,"music animation returns when speech stops");
        level.accept(-127,-127,now);now+=20_000_000L;
        check(level.decibels(now)==-127,"silence clears the head animation");
        level.accept(-20,-127,now);
        check(level.decibels(now+300_000_000L)==-127,"stale packets cannot hold an animation");
        level.clear();check(level.decibels(now)==-127,"stopping sharing clears animation state");
        short[] sound=new short[960];java.util.Arrays.fill(sound,(short)16384);
        check(Math.abs(AnimationLevel.decibels(sound)+6.02)<.1,"raw PCM maps to native decibels");
        System.out.println("TALKING HEADS ANIMATION TESTS PASSED");
    }
}
