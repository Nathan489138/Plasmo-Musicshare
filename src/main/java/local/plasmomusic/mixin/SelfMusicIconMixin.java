package local.plasmomusic.mixin;

import local.plasmomusic.MusicAddon;
import net.minecraft.class_1297;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import su.plo.voice.client.render.voice.EntityVoiceIconState;

/** PV skips the local player. Add only its sending state, with no received audio source. */
@Mixin(targets="su.plo.voice.client.render.voice.EntityIconStateExtractor",remap=false)
public abstract class SelfMusicIconMixin {
    @Inject(method={
        "extractPlayer(Lnet/minecraft/class_1309;Lnet/minecraft/class_746;)Lsu/plo/voice/client/render/voice/EntityVoiceIconState;",
        "extractPlayer(Lnet/minecraft/class_1297;Lnet/minecraft/class_746;)Lsu/plo/voice/client/render/voice/EntityVoiceIconState;"
    },at=@At("HEAD"),cancellable=true,require=1)
    private void musicSelfIcon(@Coerce class_1297 entity,class_746 localPlayer,CallbackInfoReturnable<EntityVoiceIconState> ci){
        if(localPlayer==null||entity!=localPlayer)return;
        MusicAddon app=MusicAddon.instance;
        if(app==null)return;
        // PV skips the local player even for ordinary microphone speech. Use
        // whichever outgoing path is active and suppress the icon below threshold.
        ci.setReturnValue(app.selfVoiceIcon());
    }
}
