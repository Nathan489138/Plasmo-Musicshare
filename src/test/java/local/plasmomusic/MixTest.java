package local.plasmomusic;
import su.plo.voice.api.client.*;
import su.plo.voice.api.client.audio.capture.*;
import su.plo.voice.api.client.audio.device.*;
import su.plo.voice.api.client.connection.*;
import su.plo.voice.api.client.socket.*;
import su.plo.voice.api.audio.codec.*;
import su.plo.voice.api.encryption.*;
import su.plo.voice.client.audio.capture.VoiceAudioCapture;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sound.sampled.AudioFormat;

/** Execute the actual transformed PV method with fake transports: no network or microphone access. */
public class MixTest {
    interface Answer {Object answer(Method method,Object[] args);}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,Answer answer){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
        Object r=answer.answer(m,a);if(r!=null)return r;
        if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;
        if(m.getReturnType()==long.class)return 0L;if(m.getReturnType()==Optional.class)return Optional.empty();return null;
    });}
    static void set(Object object,String name,Object value)throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void run()throws Exception {
        List<PlayerAudioPacket> packets=new ArrayList<>();AtomicInteger ends=new AtomicInteger(),filters=new AtomicInteger();
        AtomicReference<short[]> encodedSamples=new AtomicReference<>();AtomicBoolean mute=new AtomicBoolean(),supportsStereo=new AtomicBoolean(true);
        VoiceClientConfig config=new VoiceClientConfig();
        config.getVoice().getActivationThreshold().set(-40.0);
        AudioEncoder encoder=proxy(AudioEncoder.class,(m,a)->{if(m.getName().equals("encode")){encodedSamples.set(((short[])a[0]).clone());return new byte[]{11};}return null;});
        UdpClient udp=proxy(UdpClient.class,(m,a)->{if(m.getName().equals("sendPacket"))packets.add((PlayerAudioPacket)a[0]);return null;});
        UdpClientManager manager=proxy(UdpClientManager.class,(m,a)->m.getName().equals("getClient")?Optional.of(udp):null);
        ServerConnection connection=proxy(ServerConnection.class,(m,a)->{if(m.getName().equals("sendPacket"))ends.incrementAndGet();return null;});
        AudioCapture dummyCapture=proxy(AudioCapture.class,(m,a)->switch(m.getName()){case "isActive"->true;case "isServerMuted"->mute.get();default->null;});
        ServerInfo info=proxy(ServerInfo.class,(m,a)->null);
        PlasmoVoiceClient client=proxy(PlasmoVoiceClient.class,(m,a)->switch(m.getName()){
            case "getConfig"->config;case "getAudioCapture"->dummyCapture;case "getServerInfo"->Optional.of(info);
            case "getUdpClientManager"->manager;case "getServerConnection"->Optional.of(connection);
            case "getDeviceManager"->proxy(DeviceManager.class,(method,args)->null);default->null;
        });
        MusicAddon app=new MusicAddon();set(app,"voice",client);set(app,"capturing",true);MusicAddon.instance=app;
        app.settings=new Settings("exclude-game",true,50,true);
        VoiceAudioCapture capture=new VoiceAudioCapture(client,config);set(capture,"monoEncoder",encoder);set(capture,"stereoEncoder",encoder);
        AtomicInteger encrypted=new AtomicInteger();capture.setEncryption(proxy(Encryption.class,(m,a)->{if(m.getName().equals("encrypt")){encrypted.incrementAndGet();return a[0];}return null;}));
        InputDevice device=proxy(InputDevice.class,(m,a)->switch(m.getName()){
            case "getFormat"->new AudioFormat(48000,16,1,true,false);
            case "processFilters"->{filters.incrementAndGet();yield ((short[])a[0]).clone();}default->null;
        });
        ClientActivation activation=proxy(ClientActivation.class,(m,a)->switch(m.getName()){
            case "getId"->VoiceActivation.PROXIMITY_ID;case "getTranslation"->"pv.activation.proximity";
            case "getDistance"->12;case "isStereoSupported"->supportsStereo.get();default->null;
        });
        Class<?> ec=Class.forName("su.plo.voice.client.audio.capture.VoiceAudioCapture$EncodedCapture");
        Constructor<?> ctor=ec.getDeclaredConstructor(InputDevice.class,short[].class);ctor.setAccessible(true);
        Method process=VoiceAudioCapture.class.getDeclaredMethod("processActivation",InputDevice.class,ClientActivation.class,ClientActivation.Result.class,short[].class,ec);process.setAccessible(true);
        short[] mic=new short[960];Arrays.fill(mic,(short)1000);float[] music=new float[4000];Arrays.fill(music,.25f);
        app.buffer.offer(music,48000);process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,mic,ctor.newInstance(device,mic));
        check(packets.size()==1&&encodedSamples.get()[0]==4096&&filters.get()==0,"PTT released: music sends, microphone excluded");
        Field streamField=MusicAddon.class.getDeclaredField("stream");streamField.setAccessible(true);
        check(streamField.get(app) instanceof MusicStream,"direct PCM stream supplies Plasmo Voice without Soundboard");
        check(packets.get(0).isStereo()&&packets.get(0).getDistance()==12&&encodedSamples.get().length==1920,"stereo packet and proximity distance");
        var hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,false);
        config.getVoice().getActivationThreshold().set(-40.0);
        app.recordSent(encodedSamples.get(),true);app.onHud(hud);
        check(hud.isRender(),"music transmission shows native proximity microphone icon without PTT");
        config.getVoice().getActivationThreshold().set(-20.0);
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,true);app.onHud(hud);
        check(!hud.isRender(),"raising PV slider hides HUD even if native activation requested rendering");
        config.getVoice().getActivationThreshold().set(-40.0);
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,false);app.onHud(hud);
        check(hud.isRender(),"lowering PV slider restores the same music frame in HUD");
        app.recordSent(new short[1920],true);
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,true);app.onHud(hud);
        check(!hud.isRender()&&app.settings.enabled(),"silent sharing hides HUD while leaving sharing enabled");
        app.lastSentNanos=System.nanoTime()-1_000_000_000L;
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,false);app.onHud(hud);
        check(!hud.isRender(),"stale transmission does not keep microphone icon lit");
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,true);app.onHud(hud);
        check(!hud.isRender(),"native activation without fresh audible samples does not keep HUD lit");
        var otherActivation=proxy(ClientActivation.class,(m,a)->m.getName().equals("getId")?UUID.randomUUID():null);
        app.lastSentNanos=System.nanoTime();
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(otherActivation,false);app.onHud(hud);
        check(!hud.isRender(),"music does not light unrelated voice channel icons");
        app.buffer.offer(music,48000);process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,mic,ctor.newInstance(device,mic));
        check(encodedSamples.get()[0]==5096&&filters.get()==1,"PTT pressed: processed microphone plus music");
        check(encrypted.get()==2,"original PV encryption path retained");
        var monitor=new su.plo.voice.client.gui.settings.MicrophoneTestController(client,config);
        var event=new su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent(capture,device,mic,
            (su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent.ProcessedSamples)ctor.newInstance(device,mic));
        long beforeRead=app.buffer.availableFramesForTest();
        app.recordSent(encodedSamples.get(),true); // Class loading above may exceed the stale-frame timeout.
        monitor.onAudioCaptureProcessed(event);
        Field db=monitor.getClass().getDeclaredField("microphoneDB");db.setAccessible(true);
        short[] expectedMono=new short[960]; for(int i=0;i<960;i++)expectedMono[i]=encodedSamples.get()[i*2];
        check(Math.abs(db.getDouble(monitor)-su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(expectedMono))<.001,
            "real PV microphone meter measures transmitted music plus microphone");
        check(app.buffer.availableFramesForTest()==beforeRead,"meter does not consume an extra audio frame");
        AtomicReference<short[]> monitored=new AtomicReference<>();
        set(monitor,"source",proxy(su.plo.voice.api.client.audio.source.LoopbackSource.class,(m,a)->{if(m.getName().equals("write"))monitored.set((short[])a[0]);return null;}));
        app.buffer.offer(music,48000);monitor.onAudioCaptureProcessed(event);
        check(monitored.get()!=null&&java.util.stream.IntStream.range(0,monitored.get().length).allMatch(i->monitored.get()[i]==0),"explicit microphone preview starts silent while delay fills");
        for(int i=0;i<50;i++){app.buffer.offer(music,48000);monitor.onAudioCaptureProcessed(event);}
        check(monitored.get()[0]==Math.round(5096*.7),"delayed native preview hears music plus microphone at local-only volume");
        supportsStereo.set(false);app.buffer.offer(music,48000);process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,mic,ctor.newInstance(device,mic));
        check(encodedSamples.get().length==960&&!packets.get(2).isStereo(),"server mono fallback");
        app.settings=new Settings("exclude-game",false,50,true);
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,mic,ctor.newInstance(device,mic));
        check(packets.size()==3&&ends.get()==1,"disable sends end packet and stops music");
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,false);app.onHud(hud);
        check(!hud.isRender()&&app.lastSentNanos==0,"stopping sharing clears music HUD immediately");
        short[] spoken=new short[960];Arrays.fill(spoken,(short)20000);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(app.isMicrophoneTransmitting(),"native PV microphone path still supplies local speaking state with sharing off");
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,false);app.onHud(hud);
        check(hud.isRender(),"sharing off: audible microphone shows HUD");
        config.getVoice().getActivationThreshold().set(-10.0);
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,true);app.onHud(hud);
        check(!hud.isRender(),"sharing off: PV slider also hides below-threshold microphone HUD");
        config.getVoice().getActivationThreshold().set(-40.0);
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(!app.isMicrophoneTransmitting(),"native PV microphone end removes local speaking state");
        app.settings=new Settings("exclude-game",true,50,true);mute.set(true);
        check(!app.allowed(),"server mute blocks music");
        app.lastSentNanos=System.nanoTime();hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,false);app.onHud(hud);
        check(!hud.isRender(),"server mute does not show a transmitting music icon");
        mute.set(false);
        app.buffer.offer(music,48000);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(app.isMicrophoneTransmitting(),"sharing also tracks the active microphone for immediate handoff");
        app.settings=new Settings("exclude-game",false,50,true);
        check(app.isMicrophoneTransmitting(),"turning music off preserves the latest speaking state before the next capture frame");
        app.settings=new Settings("exclude-game",true,50,true);
        process.invoke(capture,device,activation,ClientActivation.Result.END,null,ctor.newInstance(device,spoken));
        check(app.lastSentNanos==0&&!app.isMicrophoneTransmitting(),"priority END with null samples clears both local transmission states immediately");
        app.buffer.offer(music,48000);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        Method cleanup=VoiceAudioCapture.class.getDeclaredMethod("cleanup");cleanup.setAccessible(true);
        cleanup.invoke(capture);
        check(!app.isMicrophoneTransmitting()&&!app.isMusicTransmitting()&&app.outgoingDecibels()==-127,
            "capture cleanup immediately clears microphone, music and outgoing meter state");
        app.settings=new Settings("exclude-game",false,50,true);app.setCensorEnabled(true);
        config.getVoice().getActivationThreshold().set(-30.0);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        short[] censored=encodedSamples.get().clone();
        check(java.util.stream.IntStream.range(0,censored.length).allMatch(i->Math.abs((int)censored[i])<=3403)
            &&java.util.stream.IntStream.range(0,censored.length).anyMatch(i->censored[i]!=0),
            "censor alone replaces original speech with supplied sound even while music is off");
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,false);app.onHud(hud);
        check(hud.isRender(),"censored voice HUD follows original microphone threshold even for a quieter beep");
        var censorEvent=new su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent(capture,device,spoken,
            (su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent.ProcessedSamples)ctor.newInstance(device,spoken));
        monitor.onAudioCaptureProcessed(censorEvent);
        check(Math.abs(db.getDouble(monitor)-su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(spoken))<.001,
            "PV microphone detection retains original voice level while transmitted speech is censored");
        check(monitored.get()!=null&&java.util.stream.IntStream.range(0,monitored.get().length)
            .allMatch(i->Math.abs((int)monitored.get()[i])<=3403),"explicit microphone test plays beep, not uncensored speech");
        config.getVoice().getActivationThreshold().set(-10.0);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(java.util.stream.IntStream.range(0,encodedSamples.get().length).allMatch(i->encodedSamples.get()[i]==0),
            "below slider threshold: censor outputs silence, never original voice");
        config.getVoice().getActivationThreshold().set(-30.0);
        app.settings=new Settings("exclude-game",true,50,true);app.buffer.clear();app.buffer.offer(music,48000);
        short[] expectedBeep=new VoiceCensor().replace(activation.getId(),spoken,48000,true,-30);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        short[] withMusic=encodedSamples.get();
        check(java.util.stream.IntStream.range(0,960).allMatch(i->withMusic[i]==4096+expectedBeep[i]),
            "music is unchanged: output equals original music plus beep, without original speech");
        config.getVoice().getActivationThreshold().set(-10.0);
        int beforeQuietMusic=packets.size();
        app.buffer.offer(music,48000);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(packets.size()==beforeQuietMusic,
            "below-threshold censored voice and music end remote stream instead of keeping icon lit");
        app.settings=new Settings("exclude-game",false,50,true);int beforeInactive=packets.size();
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(packets.size()==beforeInactive,"voice censor respects PTT and never sends when microphone activation is off");
        config.getVoice().getActivationThreshold().set(-30.0);supportsStereo.set(true);
        config.getVoice().getStereoCapture().set(true);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        short[] stereoBeep=encodedSamples.get();
        check(stereoBeep.length==1920&&java.util.stream.IntStream.range(0,960).allMatch(i->stereoBeep[i*2]==stereoBeep[i*2+1]),
            "stereo microphone censor duplicates beep into correct left and right channels");
        mute.set(true);check(!app.censorAllowed(),"server mute also blocks voice censor");mute.set(false);
        app.censorEnabled=false;app.settings=new Settings("exclude-game",false,50,true);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(encodedSamples.get()[0]==20000,"disabling censor restores native microphone speech immediately");
        app.studio=StudioSettings.defaults().voice(StudioSettings.Style.ROBOT,100,StudioSettings.Room.OFF,25);
        app.settings=new Settings("exclude-game",true,50,true);app.buffer.clear();app.buffer.offer(music,48000);
        ChannelEffects reference=new ChannelEffects();short[] voiceFx=reference.process(spoken,48000,false,StudioSettings.Style.ROBOT,100,StudioSettings.Room.OFF,25);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(java.util.stream.IntStream.range(0,960).allMatch(i->encodedSamples.get()[i*2]==Math.max(-32768,Math.min(32767,4096+voiceFx[i]))),"voice character effects change microphone without changing music samples; full-scale sums retain PCM limiting");
        app.settings=new Settings("exclude-game",false,50,true);
        app.studio=StudioSettings.defaults().voice(StudioSettings.Style.NONE,100,StudioSettings.Room.STUDIO,35);
        short[] impulse=new short[960];impulse[0]=7000;
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,impulse,ctor.newInstance(device,impulse));
        int beforeTail=packets.size();
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,new short[960],ctor.newInstance(device,new short[960]));
        check(packets.size()==beforeTail+1,"singing reverb tail continues after microphone activation ends");
        app.setCensorEnabled(true);
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(java.util.stream.IntStream.range(0,encodedSamples.get().length).allMatch(i->Math.abs((int)encodedSamples.get()[i])<=3403),"F8 censor takes priority and removes old voice-effect and reverb tails");
        app.studio=StudioSettings.defaults();app.censorEnabled=false;
        app.settings=new Settings("exclude-game",true,50,true);config.getVoice().getActivationThreshold().set(-40.0);
        short[] silence=new short[960];
        app.buffer.clear();app.buffer.offer(music,48000);
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,silence,ctor.newInstance(device,silence));
        int beforeSilence=packets.size(),beforeEnd=ends.get();
        // Clear the PCM reader too, so no old ring-buffer audio remains in the test.
        set(app,"stream",null);app.buffer.clear();
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,silence,ctor.newInstance(device,silence));
        check(packets.size()==beforeSilence&&ends.get()==beforeEnd+1,"silent sharing sends one END for remote speaker icon");
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,silence,ctor.newInstance(device,silence));
        check(packets.size()==beforeSilence&&ends.get()==beforeEnd+1&&app.settings.enabled(),"continued silence sends no packets and keeps sharing enabled");
        config.getVoice().getActivationThreshold().set(-10.0);app.buffer.offer(music,48000);
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,silence,ctor.newInstance(device,silence));
        check(packets.size()==beforeSilence,"raising native threshold also suppresses remote stream");
        config.getVoice().getActivationThreshold().set(-40.0);app.buffer.offer(music,48000);
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,silence,ctor.newInstance(device,silence));
        check(packets.size()==beforeSilence+1,"music resumes remote stream after crossing same slider gate");
        set(app,"stream",null);app.buffer.clear();
        process.invoke(capture,device,activation,ClientActivation.Result.ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(packets.size()==beforeSilence+2,"ordinary speaking still sends during silent music sharing");
        short[] rightOnly=new short[1920];for(int i=0;i<960;i++)rightOnly[i*2+1]=20000;
        check(app.proximityAboveThreshold(rightOnly,true),"remote gate sees right-only stereo music like local gate");
        app.setForceOpen(true);config.getVoice().getActivationThreshold().set(-10.0);
        set(app,"stream",null);app.buffer.clear();float[] faint=new float[4000];Arrays.fill(faint,.001f);app.buffer.offer(faint,48000);
        int beforeForce=packets.size();
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(packets.size()==beforeForce+1&&encodedSamples.get()[0]>0&&encodedSamples.get()[0]<100,"force-open sends quiet music below threshold without mixing inactive microphone");
        hud=new su.plo.voice.api.client.event.render.HudActivationRenderEvent(activation,true);app.onHud(hud);
        check(!hud.isRender(),"force-open does not change local HUD threshold logic");
        set(app,"stream",null);app.buffer.clear();
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,spoken,ctor.newInstance(device,spoken));
        check(packets.size()==beforeForce+2&&java.util.stream.IntStream.range(0,encodedSamples.get().length).allMatch(i->encodedSamples.get()[i]==0),"force-open keeps stream alive through silence and never leaks unactivated speech");
        app.setForceOpen(false);int endsBeforeForceOff=ends.get();
        process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,silence,ctor.newInstance(device,silence));
        check(packets.size()==beforeForce+2&&ends.get()==endsBeforeForceOff+1,"disabling force-open restores threshold END on next frame");
        app.setForceOpen(true);mute.set(true);check(!app.allowed(),"force-open still respects server mute");mute.set(false);
        config.getVoice().getMicrophoneDisabled().set(true);check(!app.allowed(),"force-open still respects disabled microphone");config.getVoice().getMicrophoneDisabled().set(false);
        app.setEnabled(false);check(app.settings.forceOpen()&&!app.allowed(),"sharing off stops force-open without losing its saved option");
        int beforeOff=packets.size();process.invoke(capture,device,activation,ClientActivation.Result.NOT_ACTIVATED,silence,ctor.newInstance(device,silence));
        check(packets.size()==beforeOff,"force-open does not send after sharing is turned off");
        // Bypassing an effects rack must not retain speech for a later activation.
        app.updateStudio(StudioSettings.defaults().select(StudioSettings.Style.ECHO));
        app.processVoice(activation.getId(),spoken,48000,true);check(app.voiceHasTail(activation.getId()),"echo rack has pending speech before bypass");
        app.updateStudio(StudioSettings.defaults());app.updateStudio(StudioSettings.defaults().select(StudioSettings.Style.ECHO));
        long oldEchoEnergy=0;for(int i=0;i<30;i++){short[] clean=app.processVoice(activation.getId(),silence,48000,false);for(short value:clean)oldEchoEnergy+=(long)value*value;}
        check(oldEchoEnergy==0,"effect bypass and re-enable cannot replay old queued speech");
        MusicAddon.instance=null;
    }
}
