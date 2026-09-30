package local.plasmomusic.mixin;

import local.plasmomusic.MusicAddon;
import local.plasmomusic.MusicTabWidget;
import local.plasmomusic.EffectsTabWidget;
import net.minecraft.class_2960;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.client.BaseVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;

/** Register the music page alongside Plasmo Voice's built-in settings tabs. */
@Mixin(value=VoiceSettingsScreen.class, remap=false)
public abstract class VoiceSettingsMusicTabMixin {
    @Shadow @Final private BaseVoiceClient voiceClient;
    @Shadow @Final private VoiceClientConfig config;

    @Inject(method="init()V", at=@At(value="INVOKE",
        target="Lsu/plo/voice/client/gui/settings/VoiceSettingsNavigation;init()V"), require=1)
    private void plasmoMusic$addTab(CallbackInfo ci) {
        MusicAddon app = MusicAddon.instance;
        if (app == null) return;
        VoiceSettingsScreen screen = (VoiceSettingsScreen)(Object)this;
        screen.getNavigation().addTab(McTextComponent.literal("音乐共享"),
            class_2960.method_12829("plasmovoice:textures/icons/tabs/volume.png"),
            new MusicTabWidget(screen, voiceClient, config, app));
        screen.getNavigation().addTab(McTextComponent.literal("音乐音效"),class_2960.method_12829("plasmovoice:textures/icons/tabs/volume.png"),new EffectsTabWidget(screen,voiceClient,config,app,false));
        screen.getNavigation().addTab(McTextComponent.literal("本机试听"),class_2960.method_12829("plasmovoice:textures/icons/tabs/volume.png"),new EffectsTabWidget(screen,voiceClient,config,app,true));
    }
}
