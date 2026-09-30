package local.plasmomusic;
import su.plo.voice.client.config.hotkey.ConfigHotkeys;
import su.plo.voice.api.client.config.hotkey.Hotkey;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import net.minecraft.*;

public final class ControlsTest {
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
    public static void run()throws Exception{
        ConfigHotkeys keys=new ConfigHotkeys();
        Hotkey.Key key=new Hotkey.Key(Hotkey.Type.KEYSYM,297);
        Hotkey returned=keys.register("regression.f8",List.of(key),"regression",true);
        Hotkey actual=keys.getHotkey("regression.f8").orElseThrow();
        AtomicInteger oldCalls=new AtomicInteger(),newCalls=new AtomicInteger();
        returned.addPressListener(a->oldCalls.incrementAndGet());actual.addPressListener(a->newCalls.incrementAndGet());
        keys.getPressedKeys().add(key);actual.updateState(Hotkey.Action.DOWN);
        check(returned!=actual&&oldCalls.get()==0&&newCalls.get()==1,"reproduced v1.0 bug: PV register returns detached default key");
        // Emulate the pre-GameOptions Fabric client initializer phase, with no window/world/network.
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        class_310 mockClient=(class_310)unsafe.allocateInstance(class_310.class);
        var singleton=class_310.class.getDeclaredField("field_1700");singleton.setAccessible(true);singleton.set(null,mockClient);
        var thread=class_310.class.getDeclaredField("field_1696");thread.setAccessible(true);thread.set(mockClient,Thread.currentThread());
        MusicAddon app=new MusicAddon();GameControls.register(app);
        app.update(new Settings("exclude-game",true,50,true));
        // Deliver remote-player events to the addon's annotated subscribers, as PV does.
        for(int i=0;i<100;i++){
            var event=new su.plo.voice.api.client.event.connection.VoicePlayerDisconnectedEvent(UUID.randomUUID());
            for(var method:MusicAddon.class.getDeclaredMethods()){
                if(method.isAnnotationPresent(su.plo.voice.api.event.EventSubscribe.class)&&method.getParameterCount()==1
                    &&method.getParameterTypes()[0].isInstance(event))method.invoke(app,event);
            }
        }
        check(app.settings.enabled(),"100 other-player voice disconnects leave sharing enabled");
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(null,mockClient);
        check(!app.settings.enabled(),"local Minecraft disconnect disables sharing via Fabric event");
        var f8=class_3675.class_307.field_1668.method_1447(297);
        class_304.method_1416(f8,true);class_304.method_1420(f8);
        check(!GameControls.poll(app)&&app.censorEnabled,"F8 held enables censor without opening settings");
        class_304.method_1420(f8);
        check(!GameControls.poll(app)&&app.censorEnabled,"F8 repeat while held does not toggle censor");
        class_304.method_1416(f8,false);GameControls.poll(app);
        check(!app.censorEnabled,"releasing F8 immediately restores speech");
        GameControls.open(app);check(GameControls.poll(app),"settings remain accessible through explicit panel request");
        class_304.method_1420(class_3675.class_307.field_1668.method_1447(298));GameControls.poll(app);
        check(app.settings.enabled(),"F9 native key event enables music");
        class_304.method_1420(class_3675.class_307.field_1668.method_1447(298));GameControls.poll(app);
        check(!app.settings.enabled(),"F9 second event disables music");
        Class.forName("local.plasmomusic.MusicScreen",false,ControlsTest.class.getClassLoader()).getDeclaredMethods();
        app.settings=new Settings("exclude-game",true,100,true);
        float[] pcm=new float[8000];Arrays.fill(pcm,.5f);app.buffer.offer(pcm,48000);
        short[] full=app.musicFrame(960,48000,false);
        check(full!=null&&full[0]==16384,"direct music stream reads captured PCM without Soundboard");
        app.settings=new Settings("exclude-game",true,50,true);app.buffer.offer(pcm,48000);
        short[] quiet=app.musicFrame(960,48000,false);
        check(quiet[0]>0&&quiet[0]<full[0],"music volume slider changes the direct stream live");
        app.settings=new Settings("exclude-game",true,0,true);app.buffer.offer(pcm,48000);
        check(app.musicFrame(960,48000,false)[0]==0,"capture slider zero silences music immediately");
        for(int i=0;i<30000;i++)app.musicFrame(960,48000,false);
        check(app.settings.enabled(),"ten minutes of silent frames do not terminate sharing");
        app.setEnabled(false);check(app.musicFrame(960,48000,false)==null,"switching off stops the direct stream");
        app.setEnabled(true);app.buffer.offer(pcm,48000);
        check(app.musicFrame(960,48000,false)!=null&&app.settings.enabled(),"re-enabling starts a fresh direct stream");
        app.setEnabled(false);app.setEnabled(true);app.buffer.offer(pcm,48000);
        check(app.musicFrame(960,48000,false)!=null&&app.settings.enabled(),"rapid off/on without a control tick stays usable");
        app.effects=EffectSettings.defaults().withLimiter(true);
        app.settings=new Settings("exclude-game",true,150,true);
        app.buffer.clear();float[] loud=new float[4000];Arrays.fill(loud,1.2f);app.buffer.offer(loud,48000);
        short[] limited=app.musicFrame(960,48000,false);
        check(limited[0]==32767,"removed legacy music effects cannot alter the music stream");
        app.effects=EffectSettings.defaults();
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            synchronized(app){
                var read=pool.submit(()->app.musicFrame(960,48000,false));
                check(read.get(2,java.util.concurrent.TimeUnit.SECONDS)!=null,"audio read proceeds while control/settings monitor is held");
            }
        }finally{pool.shutdownNow();}
    }
}
