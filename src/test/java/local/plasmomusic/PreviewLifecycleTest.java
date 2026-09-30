package local.plasmomusic;

import java.util.concurrent.atomic.AtomicInteger;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.audio.source.ClientSourceManager;
import su.plo.voice.api.client.audio.source.LoopbackSource;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureEvent;
import su.plo.voice.api.event.EventBus;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.event.gui.MicrophoneTestStartedEvent;
import su.plo.voice.client.event.gui.MicrophoneTestStoppedEvent;
import su.plo.voice.client.gui.settings.MicrophoneTestController;

/** Real native test-controller lifecycle with fake output, no hardware access. */
public final class PreviewLifecycleTest {
    public static void run() throws Exception {
        MusicAddon app=new MusicAddon();app.settings=new Settings("exclude-game",true,50,true);
        MusicAddon previous=MusicAddon.instance;MusicAddon.instance=app;
        AtomicInteger opened=new AtomicInteger(),closed=new AtomicInteger();
        LoopbackSource source=MixTest.proxy(LoopbackSource.class,(m,a)->{
            if(m.getName().equals("initialize"))opened.incrementAndGet();
            if(m.getName().equals("close"))closed.incrementAndGet();return null;
        });
        ClientSourceManager sources=MixTest.proxy(ClientSourceManager.class,(m,a)->m.getName().equals("createLoopbackSource")?source:null);
        EventBus events=MixTest.proxy(EventBus.class,(m,a)->{
            if(m.getName().equals("fire")){
                if(a[0] instanceof MicrophoneTestStartedEvent e)app.onPreviewStarted(e);
                if(a[0] instanceof MicrophoneTestStoppedEvent e)app.onPreviewStopped(e);
            }return null;
        });
        VoiceClientConfig config=new VoiceClientConfig();
        PlasmoVoiceClient client=MixTest.proxy(PlasmoVoiceClient.class,(m,a)->switch(m.getName()){
            case "getSourceManager"->sources;case "getEventBus"->events;case "getConfig"->config;default->null;
        });
        MicrophoneTestController controller=new MicrophoneTestController(client,config);
        AudioCapture capture=MixTest.proxy(AudioCapture.class,(m,a)->null);
        InputDevice device=MixTest.proxy(InputDevice.class,(m,a)->null);
        try{
            controller.start();controller.start();
            MixTest.check(controller.isActive()&&app.previewActive&&opened.get()==1,"native preview starts once and preserves share switch");
            var event=new AudioCaptureEvent(capture,device,new short[960]);controller.onAudioCapture(event);
            MixTest.check(event.isFlushActivations()&&app.settings.enabled(),"native preview suspends network activations without toggling sharing");
            short[] marker=new short[960];java.util.Arrays.fill(marker,(short)8000);
            for(int i=0;i<51;i++)app.delayedPreview(marker,48000,false);
            controller.stop();
            MixTest.check(!controller.isActive()&&!app.previewActive&&closed.get()==1,"preview stop closes output and exits local mode");
            event=new AudioCaptureEvent(capture,device,new short[960]);controller.onAudioCapture(event);
            MixTest.check(!event.isFlushActivations()&&app.settings.enabled(),"normal capture resumes after preview");
            controller.start();
            short[] restarted=app.delayedPreview(new short[960],48000,false);
            for(short value:restarted)if(value!=0)throw new AssertionError("stale preview samples after restart");
            MixTest.check(true,"preview restart clears delayed old speech");controller.stop();
        }finally{MusicAddon.instance=previous;}
    }
}
