package local.plasmomusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.class_2561;

/** Keep the disabled entrypoint independent of Plasmo Voice classes. */
public final class MusicshareClient implements ClientModInitializer {
    public static final String COMPATIBILITY_ERROR="plasmo.musicshare.compatibility.error";
    @Override public void onInitializeClient() {
        String error=System.getProperty(COMPATIBILITY_ERROR,"compatibility check did not run");
        if(error.isEmpty()) {
            new MusicAddon().onInitializeClient();
            return;
        }
        String message="[Plasmo Musicshare] 当前 Plasmo Voice 接口不兼容，附属功能已停用。请更新 Musicshare 或使用兼容版本。详情见日志。";
        System.err.println(message+" "+error);
        boolean[] notified={false};
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!notified[0]&&client.field_1724!=null) {
                client.field_1724.method_7353(class_2561.method_43470(message),false);
                notified[0]=true;
            }
        });
    }
}
