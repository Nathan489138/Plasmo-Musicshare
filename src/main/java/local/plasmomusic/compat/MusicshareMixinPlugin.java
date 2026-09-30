package local.plasmomusic.compat;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

/** Gate all four hooks together, before any target is transformed. */
public final class MusicshareMixinPlugin implements IMixinConfigPlugin {
    private boolean compatible;
    @Override public void onLoad(String mixinPackage) {
        String error;
        try {
            error=VoiceCompatibility.check(name->MixinService.getService().getBytecodeProvider().getClassNode(name.replace('/','.'),false));
        } catch(Exception | LinkageError failure) {
            error=failure.getClass().getSimpleName()+": "+failure.getMessage();
        }
        compatible=error.isEmpty();
        // Mixin's plugin loader and the game loader may hold different class copies.
        // A shared JVM property communicates the result without loading PV classes.
        System.setProperty("plasmo.musicshare.compatibility.error",error);
        if(compatible)System.out.println("[Plasmo Musicshare] Plasmo Voice compatibility contracts verified");
        else System.err.println("[Plasmo Musicshare] Disabled incompatible integration: "+error);
    }
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){return compatible;}
    @Override public String getRefMapperConfig(){return null;}
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
