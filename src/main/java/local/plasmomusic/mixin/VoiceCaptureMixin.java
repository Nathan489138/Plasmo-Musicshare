package local.plasmomusic.mixin;

import local.plasmomusic.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.voice.api.audio.codec.AudioEncoder;
import su.plo.voice.api.client.audio.capture.ClientActivation;
import su.plo.voice.api.client.audio.device.InputDevice;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureProcessedEvent.ProcessedSamples;
import su.plo.voice.proto.data.audio.capture.VoiceActivation;
import java.util.*;

/** Version-pinned injection: preserve PV mic gating, encryption, sequence numbers and server rules. */
@Mixin(targets="su.plo.voice.client.audio.capture.VoiceAudioCapture",remap=false)
public abstract class VoiceCaptureMixin implements MusicAddon.CaptureBridge {
    @Shadow private AudioEncoder monoEncoder;
    @Shadow private AudioEncoder stereoEncoder;
    @Shadow @Final private Set<UUID> activationStreams;
    @Shadow private byte[] encode(AudioEncoder encoder,short[] samples){throw new AssertionError();}
    @Shadow private void sendVoicePacket(ClientActivation activation,boolean stereo,byte[] data){throw new AssertionError();}
    @Shadow private void sendVoiceEndPacket(ClientActivation activation){throw new AssertionError();}
    @Unique private ClientActivation musicActivation;

    @Inject(method="processActivation",at=@At("HEAD"),cancellable=true,require=1)
    private void musicMix(InputDevice device,ClientActivation activation,ClientActivation.Result result,short[] raw,
                          @Coerce Object encoded,CallbackInfo ci) {
        boolean proximity=activation.getId().equals(VoiceActivation.PROXIMITY_ID);
        MusicAddon app=MusicAddon.instance;
        if(!proximity&&(app==null||!app.censorEnabled&&!app.studio.voiceActive()))return;
        // Keep microphone state current even while music is sharing, so toggling
        // music off can immediately hand the icon/animation back to normal speech.
        if(app!=null&&proximity){
            if(result.isActivated()&&!activation.isDisabled()&&raw!=null)
                app.recordMicrophone(((ProcessedSamples)encoded).getMono(),true);
            else app.recordMicrophone(null,false);
        }
        boolean music=app!=null&&proximity&&app.allowed();
        boolean censor=app!=null&&app.censorAllowed();
        boolean voiceFx=app!=null&&app.voiceEffectsAllowed();
        if(app==null||(!music&&!censor&&!voiceFx)||activation.isDisabled()||raw==null) {
            if(app!=null&&(activation.isDisabled()||raw==null))app.resetCensorChannel(activation.getId());
            // A PV priority activation can suppress proximity; honor its null-sample END as well.
            if(proximity&&musicActivation!=null&&!result.isActivated())musicStop();
            else if(proximity){
                musicActivation=null;
                // END counts as activated in PV, but null samples suppress proximity.
                // Let PV send its own END; only clear our local visualization here.
                if(app!=null&&raw==null)app.clearMusicTransmission();
            }
            return;
        }
        boolean stereo=(music?app.settings.stereo():app.normalStereoCapture())&&activation.isStereoSupported();
        int frames=raw.length/Math.max(1,device.getFormat().getChannels());
        int rate=(int)device.getFormat().getSampleRate();
        short[] mixed=music?app.musicFrame(frames,rate,stereo):new short[frames*(stereo?2:1)];
        if(mixed==null){musicStop();return;}
        double musicDb=music?AnimationLevel.decibels(mixed):-127;
        double microphoneDb=-127;
        short[] mic=result.isActivated()?((ProcessedSamples)encoded).getMono():new short[frames];
        short[] outgoingMic=censor||voiceFx?app.processVoice(activation.getId(),mic,rate,result.isActivated()):mic;
        boolean tail=voiceFx&&app.voiceHasTail(activation.getId());
        if(!music&&!result.isActivated()&&!tail){
            if(activationStreams.remove(activation.getId()))sendVoiceEndPacket(activation);
            if(proximity){musicActivation=null;app.clearMusicTransmission();}
            ci.cancel();return;
        }
        if(result.isActivated()||tail) {
            // Microphone uses PV's normal filters and activation; music bypasses speech denoising.
            microphoneDb=AnimationLevel.decibels(mic);
            AudioBuffer.mix(mixed,outgoingMic,stereo);
        }
        // Remote PV icons follow stream activity, so end the stream below the
        // same slider gate used locally instead of feeding silent packets forever.
        if(music&&!app.settings.forceOpen()&&!app.proximityAboveThreshold(mixed,stereo)){
            if(activationStreams.remove(activation.getId()))sendVoiceEndPacket(activation);
            musicActivation=null;app.clearMusicTransmission();ci.cancel();return;
        }
        AudioEncoder encoder=stereo?activation.getStereoEncoder().orElse(stereoEncoder):activation.getMonoEncoder().orElse(monoEncoder);
        byte[] packet=encode(encoder,mixed);
        if(packet!=null){
            sendVoicePacket(activation,stereo,packet);activationStreams.add(activation.getId());
            if(proximity){musicActivation=activation;app.recordSent(mixed,stereo,musicDb,microphoneDb);}
        }
        if(!music&&result==ClientActivation.Result.END&&!tail){
            sendVoiceEndPacket(activation);activationStreams.remove(activation.getId());
            if(proximity){musicActivation=null;app.clearMusicTransmission();app.recordMicrophone(null,false);}
        }
        ci.cancel();
    }
    @Override public void musicStop() {
        if(MusicAddon.instance!=null)MusicAddon.instance.clearMusicTransmission();
        if(musicActivation!=null){sendVoiceEndPacket(musicActivation);activationStreams.remove(musicActivation.getId());musicActivation=null;}
    }
    @Inject(method="cleanup",at=@At("HEAD"),require=1)
    private void musicCleanup(CallbackInfo ci){
        musicActivation=null;
        MusicAddon app=MusicAddon.instance;
        if(app!=null){app.clearMusicTransmission();app.recordMicrophone(null,false);app.resetCensorPlayback();}
    }
}
