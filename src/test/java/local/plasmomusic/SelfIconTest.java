package local.plasmomusic;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.audio.line.*;
import su.plo.voice.api.client.connection.ServerInfo;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.render.voice.*;
import su.plo.voice.proto.data.config.PlayerIconVisibility;

/** Verify PV's transformed local-player extraction without a world, listeners or GL context. */
public final class SelfIconTest {
    public static class Player extends class_746 {
        private Player(){super(null,null,null,null,null,null,false);}
    }
    public static void run()throws Exception {
        class_155.method_36208();class_2966.method_12851();
        var unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        Player local=(Player)unsafe.allocateInstance(Player.class),other=(Player)unsafe.allocateInstance(Player.class);
        VoiceClientConfig config=new VoiceClientConfig();
        AtomicBoolean muted=new AtomicBoolean(),connected=new AtomicBoolean(true),hidden=new AtomicBoolean();
        var offset=new su.plo.slib.api.position.Pos3d(.1,.25,-.2);
        ServerInfo info=MixTest.proxy(ServerInfo.class,(m,a)->switch(m.getName()){
            case "getPlayerIconOffset"->offset;
            case "getPlayerIconVisibility"->hidden.get()?Set.of(PlayerIconVisibility.HIDE_SOURCE_ICON):Set.of();
            default->null;
        });
        AudioCapture capture=MixTest.proxy(AudioCapture.class,(m,a)->switch(m.getName()){
            case "isActive"->true;case "isServerMuted"->muted.get();default->null;
        });
        ClientSourceLine line=MixTest.proxy(ClientSourceLine.class,(m,a)->m.getName().equals("getIcon")?"plasmovoice:textures/icons/speaker.png":null);
        ClientSourceLineManager lines=MixTest.proxy(ClientSourceLineManager.class,(m,a)->m.getName().equals("getLineById")?Optional.of(line):null);
        PlasmoVoiceClient voice=MixTest.proxy(PlasmoVoiceClient.class,(m,a)->switch(m.getName()){
            case "getServerInfo"->connected.get()?Optional.of(info):Optional.empty();
            case "getConfig"->config;case "getAudioCapture"->capture;case "getSourceLineManager"->lines;
            case "getSourceManager"->throw new AssertionError("Local icon must not depend on received sources or listeners");
            default->null;
        });
        MusicAddon app=new MusicAddon();MixTest.set(app,"voice",voice);MixTest.set(app,"capturing",true);
        app.settings=new Settings("exclude-game",true,50,true);
        MusicAddon previous=MusicAddon.instance;MusicAddon.instance=app;
        try{
            Method extract=EntityIconStateExtractor.class.getDeclaredMethod("extractPlayer",class_1309.class,class_746.class);extract.setAccessible(true);
            Method hook=Arrays.stream(EntityIconStateExtractor.class.getDeclaredMethods()).filter(m->m.getName().contains("musicSelfIcon")).findFirst().orElseThrow();hook.setAccessible(true);
            short[] loud=new short[960];Arrays.fill(loud,(short)20000);
            app.recordSent(loud,false);
            EntityVoiceIconState state=(EntityVoiceIconState)extract.invoke(EntityIconStateExtractor.INSTANCE,local,local);
            MixTest.check(state!=null&&state.getIconLocation().toString().equals("plasmovoice:textures/icons/speaker.png"),"transformed PV extractor shows self speaker icon without any received sources");
            MixTest.check(state.getIconOffset().equals(new class_243(.1,.25,-.2))&&state.getPercentText()==null,"self icon preserves server offset and native speaker style");
            short[] silent=new short[960];
            app.recordSent(silent,false);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)==null,"silent outgoing music removes the local speaker icon");
            checkSuppressed(hook,local,local,"silent sharing suppresses PV self-icon fallback");
            short[] quiet=new short[960];Arrays.fill(quiet,(short)600);
            app.recordSent(quiet,false);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)==null,"below-threshold PCM does not show the local speaker icon");
            MixTest.check(app.talkingHeadsLevel(System.nanoTime())==-127,"music below PV slider does not animate Talking Heads");
            short[] medium=new short[960];Arrays.fill(medium,(short)5000);
            app.recordSent(medium,false);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)!=null,"PV activation slider default governs the local icon");
            MixTest.check(app.talkingHeadsLevel(System.nanoTime())>-40,"music above PV slider animates Talking Heads");
            config.getVoice().getActivationThreshold().set(-20.0);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)==null,"raising PV activation slider hides the same frame live");
            MixTest.check(app.talkingHeadsLevel(System.nanoTime())==-127,"raising PV slider stops the same Talking Heads animation live");
            config.getVoice().getActivationThreshold().set(-30.0);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)!=null,"lowering PV activation slider restores the same frame live");
            app.recordSent(loud,false,-127,-20);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)!=null,"audible microphone speech still shows the local speaker icon");
            app.recordSent(new short[960],false);
            checkUnchanged(hook,other,local,"other players keep original PV icon handling");
            app.settings=new Settings("exclude-game",false,50,true);
            app.recordMicrophone(null,false);
            checkSuppressed(hook,local,local,"sharing off with no speech has no local speaker icon");
            app.recordMicrophone(loud,true);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)!=null,"ordinary microphone speech shows the local speaker with music off");
            MixTest.check(app.talkingHeadsLevel(System.nanoTime())>-40,"ordinary microphone speech animates Talking Heads above slider");
            config.getVoice().getActivationThreshold().set(-5.0);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)==null,"PV slider hides ordinary microphone icon below threshold");
            MixTest.check(app.talkingHeadsLevel(System.nanoTime())==-127,"PV slider gates ordinary Talking Heads speech animation");
            config.getVoice().getActivationThreshold().set(-30.0);
            app.recordMicrophone(null,false);
            checkSuppressed(hook,local,local,"microphone deactivation removes the local speaker icon");
            short[] veryQuiet=new short[960];Arrays.fill(veryQuiet,(short)150);
            config.getVoice().getActivationThreshold().set(-60.0);
            app.recordMicrophone(veryQuiet,true);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)!=null,"low PV slider allows quiet real microphone speech");
            MixTest.check(app.talkingHeadsLevel(System.nanoTime())>-40,"Talking Heads still moves above a low PV slider threshold");
            config.getVoice().getActivationThreshold().set(-30.0);
            app.recordMicrophone(null,false);
            app.settings=new Settings("exclude-game",true,50,true);app.lastSentNanos=System.nanoTime()-1_000_000_000L;
            checkSuppressed(hook,local,local,"stale transmission removes the self icon");
            app.lastSentNanos=System.nanoTime();muted.set(true);
            checkSuppressed(hook,local,local,"server mute prevents a self transmitting icon");
            muted.set(false);connected.set(false);
            checkSuppressed(hook,local,local,"disconnect prevents a self transmitting icon");
            connected.set(true);hidden.set(true);app.lastSentNanos=System.nanoTime();
            checkSuppressed(hook,local,local,"server source-icon hiding is preserved");
            hidden.set(false);app.recordSent(loud,false);
            MixTest.check(extract.invoke(EntityIconStateExtractor.INSTANCE,local,local)!=null,"self icon returns when sending resumes");
            var entityState=new class_10017();entityState.field_53330=1.8f;
            var render=new su.plo.lib.mod.client.render.entity.LivingEntityRenderState(entityState);
            MixTest.check(render.getNameTagAttachment().method_10214()>1.7,"native head attachment exists even when local name tag is hidden");
        }finally{MusicAddon.instance=previous;}
    }
    private static void checkUnchanged(Method hook,Player entity,Player local,String message)throws Exception {
        var result=new CallbackInfoReturnable<EntityVoiceIconState>("extractPlayer",true);
        hook.invoke(EntityIconStateExtractor.INSTANCE,entity,local,result);
        MixTest.check(!result.isCancelled(),message);
    }
    private static void checkSuppressed(Method hook,Player entity,Player local,String message)throws Exception {
        var result=new CallbackInfoReturnable<EntityVoiceIconState>("extractPlayer",true);
        hook.invoke(EntityIconStateExtractor.INSTANCE,entity,local,result);
        MixTest.check(result.isCancelled()&&result.getReturnValue()==null,message);
    }
}
