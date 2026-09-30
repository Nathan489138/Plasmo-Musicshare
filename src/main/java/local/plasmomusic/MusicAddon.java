package local.plasmomusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import su.plo.voice.api.addon.*;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.event.EventPriority;
import su.plo.voice.api.client.event.connection.UdpClientPacketReceivedEvent;
import su.plo.voice.proto.packets.udp.clientbound.SelfAudioInfoPacket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Addon(id="plasmo-system-music",name="Plasmo Musicshare",version="1.2.15",authors={"Local"})
public final class MusicAddon implements ClientModInitializer,AddonInitializer {
    @InjectPlasmoVoice private PlasmoVoiceClient voice;
    public static volatile MusicAddon instance;
    public final AudioBuffer buffer=new AudioBuffer();
    private final AudioActivity activity=new AudioActivity();
    private final AudioActivity transmissionGate=new AudioActivity();
    private final AudioActivity microphoneActivity=new AudioActivity();
    private final AnimationLevel animation=new AnimationLevel();
    private boolean talkingHeadsBridgeActive;
    private volatile long lastMicrophoneNanos;
    private volatile double microphoneDecibels=-127;
    public volatile Settings settings=new Settings("",false,50,true);
    public volatile EffectSettings effects=EffectSettings.defaults();
    public volatile boolean censorEnabled;
    public volatile StudioSettings studio=StudioSettings.defaults();
    public volatile boolean previewActive;
    private final java.util.concurrent.ConcurrentHashMap<UUID,ChannelEffects> voiceEffects=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile ChannelEffects previewVoiceEffects=new ChannelEffects();
    private volatile MusicStream previewMusicStream=new MusicStream(buffer);
    private final PreviewDelay previewDelay=new PreviewDelay();
    private final VoiceCensor censor=new VoiceCensor();
    private final VoiceCensor monitorCensor=new VoiceCensor();
    private final Path file=FabricLoader.getInstance().getConfigDir().resolve("plasmo-system-music.properties");
    private final Path effectsFile=file.resolveSibling("plasmo-system-music-effects.properties");
    private final Path studioFile=file.resolveSibling("plasmo-musicshare-studio.properties");
    private final Path statusFile=file.resolveSibling("plasmo-system-music-status.txt");
    private volatile Thread capture;
    private volatile boolean capturing,closed;
    private volatile String status="音乐未开启",format="";
    public volatile long lastSentNanos;
    private volatile long retryAtNanos;
    @FunctionalInterface interface CaptureBackend {
        void capture(String id,java.util.function.BooleanSupplier running,Wasapi.Sink sink) throws InterruptedException;
    }
    private CaptureBackend captureBackend=Wasapi::capture;
    private String openedDevice="";
    private MusicStream stream;
    private final Object streamLock=new Object();
    public volatile String displayStatus="音乐已关闭";
    private ScheduledExecutorService control;
    public interface CaptureBridge { void musicStop(); }
    @Override public void onInitializeClient() {
        if(!System.getProperty("os.name","").startsWith("Windows")) {System.err.println("[Plasmo System Music] Windows only");return;}
        instance=this;
        GameControls.register(this);
        try {Settings old=Settings.read(file);settings=new Settings("exclude-game",false,old.volume(),old.stereo(),old.forceOpen());settings.save(file);}
        catch(Exception e){System.err.println("[Plasmo System Music] "+e);}
        try {effects=EffectSettings.read(effectsFile);}
        catch(Exception e){System.err.println("[Plasmo System Music] effects: "+e);}
        try{studio=StudioSettings.read(studioFile);}catch(Exception e){System.err.println("[Plasmo Musicshare] studio: "+e);}
        PlasmoVoiceClient.getAddonsLoader().load(this);
    }
    @Override public void onAddonInitialize() {
        closed=false;
        instance=this;
        control=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Plasmo-Music-Control");t.setDaemon(true);return t;});
        control.scheduleWithFixedDelay(this::tick,0,250,TimeUnit.MILLISECONDS);
    }
    private synchronized void tick() {
        if(closed)return;
        try {
            // In-game settings are authoritative; never replay a stale on-disk switch.
            Settings next=settings;
            boolean connected=voice.getServerInfo().isPresent();
            if(capture!=null&&!capture.isAlive())capture=null;
            boolean need=(next.enabled()||previewActive&&studio.previewMusic())&&connected&&"exclude-game".equals(next.device());
            if(capture!=null&&(!need||!openedDevice.equals(next.device())))stopCapture();
            if(need&&capture==null&&System.nanoTime()>=retryAtNanos)startCapture(next.device());
            String state=!connected?"等待连接 Plasmo Voice 服务器":!next.enabled()?"音乐已关闭":status;
            if(capturing&&next.enabled())state=!allowed()?"采集中；语音被静音、禁用或麦克风尚未就绪，暂不发送":
                System.nanoTime()-lastSentNanos<1_000_000_000L?"正在发送附近音乐":"采集中；等待附近语音帧（检查麦克风 / 语音权限）";
            displayStatus=state;
            if(previewActive)displayStatus="本机试听中 · 延迟 "+studio.previewDelay()+" 秒 · 暂停对外发送";
            Files.writeString(statusFile,state+"\n"+format+"\n采集电平："+Math.round(buffer.peak*100)+"%\n发送音量："+next.volume()+"%\n"+
                "Plasmo 麦克风："+voice.getConfig().getVoice().getInputDevice().value()+"\n"+
                "Plasmo 耳机："+voice.getConfig().getVoice().getOutputDevice().value());
        }catch(Exception e){status="控制错误："+e.getMessage();System.err.println("[Plasmo System Music] "+e);}
    }
    private synchronized void startCapture(String id) {
        openedDevice=id; status="正在打开系统音源…";buffer.clear();
        Thread t=new Thread(()->{
            try {captureBackend.capture(id,()->!closed&&!Thread.currentThread().isInterrupted(),new Wasapi.Sink(){
                public void format(int rate,int channels,int bits){format=rate+" Hz / "+channels+" 声道 / "+bits+" bit → 自动重采样";capturing=true;status="正在采集并发送附近音乐";}
                public void samples(float[] stereo,int rate){buffer.offer(stereo,rate);}
            });}
            catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            catch(Throwable e){status="设备暂不可用，2 秒后重试："+e.getMessage();System.err.println("[Plasmo System Music] "+e);}
            finally{capturing=false;buffer.clear();retryAtNanos=System.nanoTime()+2_000_000_000L;}
        },"Plasmo-Music-WASAPI");
        t.setDaemon(true);capture=t;t.start();
    }
    private synchronized void stopCapture() {
        capturing=false;
        stopMusicStream();
        Thread t=capture;if(t==null)return;t.interrupt();
        try {t.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        if(!t.isAlive()){capture=null;capturing=false;buffer.clear();}
    }
    public boolean allowed() {
        try{return settings.enabled()&&capturing&&voice!=null&&voice.getServerInfo().isPresent()
            &&voice.getAudioCapture().isActive()&&!voice.getAudioCapture().isServerMuted()
            &&!voice.getConfig().getVoice().getDisabled().value()&&!voice.getConfig().getVoice().getMicrophoneDisabled().value();}
        catch(Exception e){return false;}
    }
    public synchronized void toggle() {
        setEnabled(!settings.enabled());if(settings.device().isEmpty())openPanel();
    }
    public void openPanel(){GameControls.open(this);}
    public synchronized void update(Settings next){
        if(!"exclude-game".equals(next.device()))next=new Settings("exclude-game",next.enabled(),next.volume(),next.stereo(),next.forceOpen());
        Settings previous=settings;
        settings=next;
        if(previous.enabled()!=next.enabled()||!previous.device().equals(next.device())){
            stopMusicStream();retryAtNanos=0;
        }
        try{next.save(file);}catch(Exception e){displayStatus="保存失败："+e.getMessage();}
    }
    public synchronized void setEnabled(boolean value){Settings s=settings;update(new Settings(s.device(),value,s.volume(),s.stereo(),s.forceOpen()));GameControls.message(value?"音乐共享已开启（附近玩家）":"音乐共享已关闭");}
    public synchronized void updateEffects(EffectSettings next){
        effects=next;
        try{next.save(effectsFile);}catch(Exception e){displayStatus="音效保存失败："+e.getMessage();}
    }
    public synchronized void updateStudio(StudioSettings next){
        StudioSettings previous=studio;studio=next;
        // A bypassed rack is not processed. Discard it now so switching back
        // cannot resume old echoes or stretched speech from before bypass.
        if(!previous.dsp().equals(next.dsp())||previous.voiceActive()!=next.voiceActive()
            ||(previous.voiceStrength()==0)!=(next.voiceStrength()==0))voiceEffects.clear();
        if(previous.previewDelay()!=next.previewDelay()||previous.previewMusic()!=next.previewMusic()
            ||previous.voiceStyle()!=next.voiceStyle()||previous.musicStyle()!=next.musicStyle()
            ||previous.voiceRoom()!=next.voiceRoom()||previous.musicRoom()!=next.musicRoom())previewDelay.clear();
        try{next.save(studioFile);}catch(Exception e){displayStatus="保存音效失败："+e.getMessage();}
    }
    public boolean voiceEffectsAllowed(){return studio.voiceActive()&&voiceReady();}
    public synchronized void setForceOpen(boolean value){Settings s=settings;update(new Settings(s.device(),s.enabled(),s.volume(),s.stereo(),value));}
    public short[] processVoice(UUID channel,short[] samples,int rate,boolean activated){
        if(censorEnabled)return censorMicrophone(channel,samples,rate,activated);
        StudioSettings s=studio;short[] input=activated?samples:new short[samples.length];
        return voiceEffects.computeIfAbsent(channel,id->new ChannelEffects()).processVoice(input,rate,false,s);
    }
    public boolean voiceHasTail(UUID channel){ChannelEffects fx=voiceEffects.get(channel);return !censorEnabled&&fx!=null&&fx.hasTail();}
    public void toggleCensor(){setCensorEnabled(!censorEnabled);}
    public synchronized void setCensorEnabled(boolean enabled){
        censorEnabled=enabled;censor.reset();monitorCensor.reset();voiceEffects.clear();previewVoiceEffects=new ChannelEffects();previewDelay.clear();
        GameControls.message(enabled?"人声屏蔽已开启（松开快捷键恢复）":"人声屏蔽已关闭");
    }
    public boolean censorAllowed(){return censorEnabled&&voiceReady();}
    public boolean customTransmissionAllowed(){return allowed()||censorAllowed()||voiceEffectsAllowed();}
    public boolean normalStereoCapture(){return voice.getConfig().getVoice().getStereoCapture().value();}
    public short[] censorMicrophone(UUID channel,short[] samples,int rate,boolean activated){
        return censor.replace(channel,samples,rate,activated,speakerThresholdDb());
    }
    public void resetCensorChannel(UUID channel){censor.reset(channel);voiceEffects.remove(channel);}
    public void resetCensorPlayback(){censor.reset();monitorCensor.reset();voiceEffects.clear();previewDelay.clear();}
    private void stopMusicStream(){
        synchronized(streamLock){
            stream=null;
            buffer.clear();clearMusicTransmission();
        }
    }
    public void clearMusicTransmission(){
        synchronized(streamLock){activity.clear();animation.clear();lastSentNanos=0;sentFrame=null;}
    }
    public short[] musicFrame(int frames,int rate,boolean stereo){
        Settings expected=settings;
        synchronized(streamLock){
            if(!expected.enabled()||!settings.enabled())return null;
            if(stream==null)stream=new MusicStream(buffer);
            short[] frame=stream.read(frames,rate,stereo,expected,effects,studio);
            return settings.enabled()?frame:null;
        }
    }
    private record SentFrame(short[] samples,boolean stereo,long time) {}
    private volatile SentFrame sentFrame;
    public void recordSent(short[] samples,boolean stereo){
        recordSent(samples,stereo,AnimationLevel.decibels(samples),-127);
    }
    public void recordSent(short[] samples,boolean stereo,double musicDb,double microphoneDb){
        long now=System.nanoTime();
        sentFrame=new SentFrame(samples.clone(),stereo,now);
        activity.accept(samples,stereo,now);
        animation.accept(musicDb,microphoneDb,now);
        lastSentNanos=now;
    }
    public void recordMicrophone(short[] samples,boolean activated){
        if(!activated||samples==null){
            microphoneActivity.clear();lastMicrophoneNanos=0;microphoneDecibels=-127;
            return;
        }
        long now=System.nanoTime();
        microphoneActivity.accept(samples,now);
        microphoneDecibels=AnimationLevel.decibels(samples);
        lastMicrophoneNanos=now;
    }
    public double outgoingDecibels(){return activity.decibels(System.nanoTime());}
    public double speakerThresholdDb(){
        try{return voice.getConfig().getVoice().getActivationThreshold().value();}
        catch(Exception e){return Double.POSITIVE_INFINITY;}
    }
    public boolean proximityAboveThreshold(short[] samples,boolean stereo){
        long now=System.nanoTime();transmissionGate.accept(samples,stereo,now);
        return transmissionGate.iconActive(now,speakerThresholdDb())
            ||censorEnabled&&isMicrophoneTransmitting()&&microphoneActivity.iconActive(now,speakerThresholdDb());
    }
    private boolean voiceReady(){
        try{return voice!=null&&voice.getServerInfo().isPresent()&&voice.getAudioCapture().isActive()
            &&!voice.getAudioCapture().isServerMuted()&&!voice.getConfig().getVoice().getDisabled().value()
            &&!voice.getConfig().getVoice().getMicrophoneDisabled().value();}
        catch(Exception e){return false;}
    }
    public boolean isMicrophoneTransmitting(){
        return voiceReady()&&lastMicrophoneNanos!=0&&System.nanoTime()-lastMicrophoneNanos<250_000_000L;
    }
    double talkingHeadsLevel(long now){
        double threshold=speakerThresholdDb();
        if(censorEnabled&&isMicrophoneTransmitting()&&microphoneActivity.iconActive(now,threshold))
            return talkingHeadsInput(microphoneDecibels);
        if(isMusicTransmitting()){
            if(!activity.iconActive(now,threshold))return -127;
            double level=animation.decibels(now);
            return talkingHeadsInput(level<=-100?activity.decibels(now):level);
        }
        if(isMicrophoneTransmitting())return microphoneActivity.iconActive(now,threshold)?
            talkingHeadsInput(microphoneDecibels):-127;
        return -127;
    }
    private static double talkingHeadsInput(double level){
        // Talking Heads removes its profile at -40 dB. A lower PV slider must
        // still permit motion for frames that exceed that slider's threshold.
        return Math.max(-39.5,Math.min(0,level));
    }
    public void refreshTalkingHeads(){
        long now=System.nanoTime();
        boolean sending=isMusicTransmitting()||isMicrophoneTransmitting();
        if(sending||talkingHeadsBridgeActive){
            TalkingHeadsCompat.publish(talkingHeadsLevel(now));
            talkingHeadsBridgeActive=sending;
        }
    }
    public short[] monitorSamples(short[] microphone,int rate,boolean stereo,boolean testing){
        if(testing){
            int frames=microphone.length/(stereo?2:1);short[] mono=new short[frames];
            for(int i=0;i<frames;i++)mono[i]=stereo?(short)(((int)microphone[i*2]+microphone[i*2+1])/2):microphone[i];
            StudioSettings s=studio;
            if(censorEnabled){
                short[] replacement=monitorCensor.replace(new UUID(0,0),mono,rate,true,speakerThresholdDb());
                microphone=new short[frames*(stereo?2:1)];AudioBuffer.mix(microphone,replacement,stereo);
            }else microphone=previewVoiceEffects.processVoice(microphone.clone(),rate,stereo,s);
            if(s.previewMusic()&&(settings.enabled()||previewActive)&&capturing){
                synchronized(streamLock){
                    short[] music=previewMusicStream.read(frames,rate,stereo,settings,effects,studio);
                    for(int i=0;i<microphone.length;i++)microphone[i]=(short)Math.max(-32768,Math.min(32767,(int)microphone[i]+music[i]));
                }
            }
            return microphone;
        }
        if(!allowed())return microphone;
        SentFrame frame=sentFrame;
        if(frame==null||System.nanoTime()-frame.time()>200_000_000L)return microphone;
        int frames=frame.samples().length/(frame.stereo()?2:1);
        short[] result=new short[frames*(stereo?2:1)];
        for(int i=0;i<frames;i++){
            int l=frame.samples()[i*(frame.stereo()?2:1)];
            int r=frame.stereo()?frame.samples()[i*2+1]:l;
            if(stereo){result[i*2]=(short)l;result[i*2+1]=(short)r;}else result[i]=(short)((l+r)/2);
        }
        return result;
    }
    public short[] delayedPreview(short[] samples,int rate,boolean stereo){StudioSettings s=studio;return previewDelay.process(samples,rate,stereo,s.previewDelay(),s.previewVolume());}
    @EventSubscribe public void onPreviewStarted(su.plo.voice.client.event.gui.MicrophoneTestStartedEvent e){
        previewActive=true;clearMusicTransmission();recordMicrophone(null,false);voiceEffects.clear();previewDelay.clear();monitorCensor.reset();previewVoiceEffects=new ChannelEffects();previewMusicStream=new MusicStream(buffer);
    }
    @EventSubscribe public void onPreviewStopped(su.plo.voice.client.event.gui.MicrophoneTestStoppedEvent e){previewActive=false;previewDelay.clear();}
    @EventSubscribe public void onProcessed(AudioCaptureProcessedEvent e){
        if(!voiceReady())voiceEffects.clear();
        if(!customTransmissionAllowed()&&e.getCapture() instanceof CaptureBridge b)b.musicStop();
    }
    @EventSubscribe(priority=EventPriority.HIGHEST) public void onSelfAudioInfo(UdpClientPacketReceivedEvent event){
        // Apply the slider gate after Talking Heads' own packet handler.
        if(event.getPacket() instanceof SelfAudioInfoPacket)
            TalkingHeadsCompat.publish(talkingHeadsLevel(System.nanoTime()));
    }
    @EventSubscribe(priority=EventPriority.HIGHEST) public void onHud(su.plo.voice.api.client.event.render.HudActivationRenderEvent e){
        if(e.getActivation().getId().equals(su.plo.voice.proto.data.audio.capture.VoiceActivation.PROXIMITY_ID)
            )e.setRender(!e.getActivation().isDisabled()&&localAudioAboveThreshold(System.nanoTime()));
    }
    public boolean isMusicTransmitting(){
        return allowed()&&lastSentNanos!=0&&System.nanoTime()-lastSentNanos<250_000_000L;
    }
    private boolean localAudioAboveThreshold(long now){
        double threshold=speakerThresholdDb();
        if(censorEnabled)return isMusicTransmitting()&&activity.iconActive(now,threshold)
            ||isMicrophoneTransmitting()&&microphoneActivity.iconActive(now,threshold);
        return isMusicTransmitting()?activity.iconActive(now,threshold):
            isMicrophoneTransmitting()&&microphoneActivity.iconActive(now,threshold);
    }
    public su.plo.voice.client.render.voice.EntityVoiceIconState selfVoiceIcon(){
        long now=System.nanoTime();
        if(!localAudioAboveThreshold(now))return null;
        var info=voice.getServerInfo().orElse(null);
        if(info==null||info.getPlayerIconVisibility().contains(su.plo.voice.proto.data.config.PlayerIconVisibility.HIDE_SOURCE_ICON))return null;
        var line=voice.getSourceLineManager().getLineById(su.plo.voice.proto.data.audio.line.VoiceSourceLine.PROXIMITY_ID).orElse(null);
        if(line==null)return null;
        var icon=net.minecraft.class_2960.method_12829(line.getIcon());
        if(icon==null)return null;
        return new su.plo.voice.client.render.voice.EntityVoiceIconState(icon,
            su.plo.voice.client.extension.MathKt.toVec3(info.getPlayerIconOffset()),null);
    }
    public synchronized void onGameDisconnect(){
        System.out.println("[Plasmo Music] Stopping sharing: local Minecraft connection disconnected");
        Settings s=settings;update(new Settings(s.device(),false,s.volume(),s.stereo(),s.forceOpen()));
        stopCapture();
        previewActive=false;previewDelay.clear();voiceEffects.clear();
        censorEnabled=false;censor.reset();monitorCensor.reset();
        recordMicrophone(null,false);
    }
    @Override public void onAddonShutdown(){closed=true;previewActive=false;resetCensorPlayback();if(control!=null)control.shutdownNow();stopCapture();recordMicrophone(null,false);instance=null;}
}
