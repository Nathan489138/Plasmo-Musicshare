package local.plasmomusic.mixin;

import local.plasmomusic.MusicAddon;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent.ProcessedSamples;

/** Feed PV's existing meter and explicit microphone test with the same mix as transmission. */
@Mixin(targets="su.plo.voice.client.gui.settings.MicrophoneTestController",remap=false)
public abstract class MicrophoneMonitorMixin {
    @Unique private short[] uncensoredMicrophone;
    @Unique private int previewRate;
    @Unique private boolean previewStereo;
    @Shadow public abstract boolean isActive();
    @Redirect(method="onAudioCaptureProcessed",at=@At(value="INVOKE",target="Lsu/plo/voice/api/client/event/audio/capture/AudioCaptureProcessedEvent$ProcessedSamples;getSamples(Z)[S"),require=1)
    private short[] musicMonitor(ProcessedSamples processed,boolean stereo,AudioCaptureProcessedEvent event){
        short[] mic=processed.getSamples(stereo);
        uncensoredMicrophone=mic;
        previewRate=(int)event.getDevice().getFormat().getSampleRate();previewStereo=stereo;
        MusicAddon app=MusicAddon.instance;
        return app==null?mic:app.monitorSamples(mic,(int)event.getDevice().getFormat().getSampleRate(),stereo,isActive());
    }
    @Redirect(method="onAudioCaptureProcessed",at=@At(value="INVOKE",target="Lsu/plo/voice/api/client/audio/source/LoopbackSource;write([S)V"),require=1)
    private void musicPreviewDelay(su.plo.voice.api.client.audio.source.LoopbackSource source,short[] samples){
        MusicAddon app=MusicAddon.instance;
        source.write(app==null?samples:app.delayedPreview(samples,previewRate,previewStereo));
    }
    @Redirect(method="onAudioCaptureProcessed",at=@At(value="INVOKE",target="Lsu/plo/voice/api/util/AudioUtil;calculateHighestAudioLevel([S)D"),require=1)
    private double censorDetection(short[] outgoing){
        double level=su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(outgoing);
        MusicAddon app=MusicAddon.instance;
        if(app!=null&&app.censorEnabled&&uncensoredMicrophone!=null)
            level=Math.max(level,su.plo.voice.api.util.AudioUtil.calculateHighestAudioLevel(uncensoredMicrophone));
        return level;
    }
}
