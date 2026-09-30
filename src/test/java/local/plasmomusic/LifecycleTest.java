package local.plasmomusic;

import su.plo.voice.api.client.*;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.connection.*;
import su.plo.voice.client.config.VoiceClientConfig;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercise device failure/retry with a fake backend; never open a real audio device. */
public final class LifecycleTest {
    private static Object field(MusicAddon app,String name)throws Exception {
        Field field=MusicAddon.class.getDeclaredField(name);field.setAccessible(true);return field.get(app);
    }
    private static void await(CountDownLatch latch,String label)throws Exception {
        MixTest.check(latch.await(3,TimeUnit.SECONDS),label);
    }
    public static void run()throws Exception {
        VoiceClientConfig config=new VoiceClientConfig();
        AudioCapture audio=MixTest.proxy(AudioCapture.class,(m,a)->m.getName().equals("isActive")?true:null);
        ServerInfo info=MixTest.proxy(ServerInfo.class,(m,a)->null);
        PlasmoVoiceClient voice=MixTest.proxy(PlasmoVoiceClient.class,(m,a)->switch(m.getName()){
            case "getConfig"->config;
            case "getAudioCapture"->audio;
            case "getServerInfo"->Optional.of(info);
            default->null;
        });
        MusicAddon app=new MusicAddon();MixTest.set(app,"voice",voice);
        // Since 1.1.6 the only valid capture route excludes this Minecraft process.
        String selected="exclude-game";
        app.settings=new Settings(selected,true,63,true);
        AtomicInteger attempts=new AtomicInteger();
        List<String> devices=new CopyOnWriteArrayList<>();
        CountDownLatch failed=new CountDownLatch(1),restarted=new CountDownLatch(1);
        CountDownLatch holdCapture=new CountDownLatch(1),stopped=new CountDownLatch(1);
        MusicAddon.CaptureBackend backend=(id,running,sink)->{
            devices.add(id);
            if(attempts.incrementAndGet()==1){
                failed.countDown();
                throw new IllegalStateException("simulated audio device invalidation");
            }
            try{
                sink.format(48000,2,16);
                restarted.countDown();
                holdCapture.await();
            }finally{stopped.countDown();}
        };
        MixTest.set(app,"captureBackend",backend);
        Method tick=MusicAddon.class.getDeclaredMethod("tick");tick.setAccessible(true);
        MusicAddon previous=MusicAddon.instance;
        try{
            tick.invoke(app);
            await(failed,"fake device failure was exercised");
            Thread first=(Thread)field(app,"capture");
            MixTest.check(first!=null,"failed capture keeps a thread handle for lifecycle cleanup");
            first.join(3000);
            MixTest.check(!first.isAlive()&&!(Boolean)field(app,"capturing"),"failed device capture terminates cleanly");
            MixTest.check((Long)field(app,"retryAtNanos")!=0,"failed device schedules a delayed retry");

            // Fix the deadline far ahead so this assertion is independent of machine speed.
            MixTest.set(app,"retryAtNanos",System.nanoTime()+60_000_000_000L);
            tick.invoke(app);tick.invoke(app);
            MixTest.check(attempts.get()==1&&field(app,"capture")==null,"device retry respects backoff and reaps the dead thread");

            MixTest.set(app,"retryAtNanos",0L);
            tick.invoke(app);
            await(restarted,"device capture restarts after retry deadline");
            MixTest.check(attempts.get()==2&&app.allowed(),"successful retry restores capture without toggling sharing");
            MixTest.check(app.settings.enabled()&&app.settings.device().equals(selected)
                &&devices.equals(List.of(selected,selected)),"retry preserves the explicitly selected device and sharing switch");

            app.settings=new Settings(selected,false,63,true);
            tick.invoke(app);
            await(stopped,"switching sharing off interrupts the replacement capture");
            MixTest.set(app,"retryAtNanos",0L);
            tick.invoke(app);tick.invoke(app);
            MixTest.check(attempts.get()==2&&field(app,"capture")==null&&!app.allowed(),"disabled sharing never retries capture even after deadline");
            MixTest.check(app.settings.device().equals(selected),"stopping capture leaves the device selection unchanged");
        }finally{
            holdCapture.countDown();
            app.onAddonShutdown();
            MusicAddon.instance=previous;
        }
    }
}
