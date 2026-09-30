package local.plasmomusic;
import net.minecraft.*;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class GameControls {
    private static boolean openRequested;
    private static class_304 openKey,toggleKey;
    private static boolean censorHeld;
    public static void register(MusicAddon app){
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->app.onGameDisconnect());
        var category=class_304.class_11900.method_74698(class_2960.method_60655("plasmo-system-music","controls"));
        openKey=KeyBindingHelper.registerKeyBinding(new class_304("key.plasmomusic.censor",297,category));
        toggleKey=KeyBindingHelper.registerKeyBinding(new class_304("key.plasmomusic.toggle",298,category));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            app.refreshTalkingHeads();
            if(poll(app))client.method_1507(new MusicScreen(app,client.field_1755));
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("music")
            .executes(ctx->{openRequested=true;return 1;})
            .then(literal("on").executes(ctx->{app.setEnabled(true);return 1;}))
            .then(literal("off").executes(ctx->{app.setEnabled(false);return 1;}))
            .then(literal("status").executes(ctx->{message(app.displayStatus);return 1;}))));
        System.out.println("[Plasmo Musicshare] controls registered: 快捷键 voice censor, 快捷键 music, /music settings");
    }
    static boolean poll(MusicAddon app){
        while(openKey.method_1436()){} // Drain edge events; censor follows held state.
        boolean held=openKey.method_1434()&&class_310.method_1551().field_1755==null;
        if(held!=censorHeld){censorHeld=held;app.setCensorEnabled(held);}
        while(toggleKey.method_1436())app.toggle();
        boolean open=openRequested;openRequested=false;return open;
    }
    public static void open(MusicAddon app){class_310.method_1551().execute(()->openRequested=true);}
    public static void message(String message){
        class_310 client=class_310.method_1551();
        if(client!=null)client.execute(()->{if(client.field_1724!=null)client.field_1724.method_7353(class_2561.method_43470("[音乐共享] "+message),false);});
    }
}
