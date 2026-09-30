package local.plasmomusic;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional bridge: keep Talking Heads' local avatar animation in step with transmitted music. */
final class TalkingHeadsCompat {
    private static volatile Method applyHeadVolume;
    private static volatile Method getTalkingHeadsConfig,isEnabled,isPlasmoEnabled;
    private static volatile boolean lookedUp;
    private static final AtomicBoolean reportedFailure=new AtomicBoolean();

    static void publish(double decibels){
        if(!Double.isFinite(decibels))return;
        try{
            Method method=applyHeadVolume;
            if(!lookedUp){
                synchronized(TalkingHeadsCompat.class){
                    if(!lookedUp){
                        try{
                            Class<?> utils=Class.forName("me.zipestudio.talkingheads.utils.talkingheads.AudioUtils",false,TalkingHeadsCompat.class.getClassLoader());
                            applyHeadVolume=utils.getMethod("applyHeadVolume",UUID.class,double.class);
                            Class<?> config=Class.forName("me.zipestudio.talkingheads.config.LeafyConfig",false,TalkingHeadsCompat.class.getClassLoader());
                            getTalkingHeadsConfig=config.getMethod("getInstance");
                            isEnabled=config.getMethod("isEnableMod");
                            isPlasmoEnabled=config.getMethod("isUsePlasmoVoice");
                        }catch(ClassNotFoundException ignored){} // Talking Heads is an optional integration.
                        finally{lookedUp=true;}
                        method=applyHeadVolume;
                    }
                }
            }
            if(method==null)return;
            Object config=getTalkingHeadsConfig.invoke(null);
            if(!Boolean.TRUE.equals(isEnabled.invoke(config))||!Boolean.TRUE.equals(isPlasmoEnabled.invoke(config)))return;
            var client=net.minecraft.class_310.method_1551();
            if(client==null||client.field_1724==null)return;
            method.invoke(null,client.field_1724.method_5667(),decibels);
        }catch(Throwable error){
            if(reportedFailure.compareAndSet(false,true))System.err.println("[Plasmo Music] Talking Heads integration unavailable: "+error);
        }
    }
    private TalkingHeadsCompat(){}
}
