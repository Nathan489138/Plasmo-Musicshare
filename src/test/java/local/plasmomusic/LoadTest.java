package local.plasmomusic;
import net.fabricmc.loader.impl.launch.knot.Knot;
import net.fabricmc.api.EnvType;
public final class LoadTest {
    public static void main(String[] args)throws Exception {
        boolean withHeads;
        try(var mods=java.nio.file.Files.list(java.nio.file.Path.of(args[0],"mods"))){
            if(mods.anyMatch(path->path.getFileName().toString().toLowerCase().contains("soundboard")))
                throw new AssertionError("independent load test must not contain Soundboard");
        }
        try(var mods=java.nio.file.Files.list(java.nio.file.Path.of(args[0],"mods"))){
            withHeads=mods.anyMatch(path->path.getFileName().toString().toLowerCase().contains("talking-heads"));
        }
        Knot knot=new Knot(EnvType.CLIENT);
        ClassLoader target=knot.init(new String[]{"--version","1.21.11","--gameDir",args[0],"--accessToken","0"});
        Class<?> capture=Class.forName("su.plo.voice.client.audio.capture.VoiceAudioCapture",false,target);
        if(capture.getDeclaredMethod("musicStop")==null)throw new AssertionError("Mixin not applied");
        Class<?> addon=Class.forName("local.plasmomusic.MusicAddon",false,target);
        addon.getDeclaredMethods();
        Class<?> voiceSettings=Class.forName("su.plo.voice.client.gui.settings.VoiceSettingsScreen",false,target);
        boolean hasMusicTab=false;
        for(var method:voiceSettings.getDeclaredMethods())
            if(method.getName().contains("plasmoMusic$addTab"))hasMusicTab=true;
        if(!hasMusicTab)throw new AssertionError("Plasmo Voice music settings tab mixin not applied");
        Class.forName("local.plasmomusic.MusicTabWidget",false,target).getDeclaredMethods();
        Class.forName("local.plasmomusic.EffectsTabWidget",false,target).getDeclaredMethods();
        System.out.println("PASS FABRIC/KNOT LOAD: real PV 2.1.17 transformed without Soundboard; settings mixin verified");
        if(withHeads){
            Class<?> utils=Class.forName("me.zipestudio.talkingheads.utils.talkingheads.AudioUtils",true,target);
            java.util.UUID player=java.util.UUID.randomUUID();
            utils.getMethod("applyHeadVolume",java.util.UUID.class,double.class).invoke(null,player,-20.0);
            Class<?> manager=Class.forName("me.zipestudio.talkingheads.client.THManager",true,target);
            @SuppressWarnings("unchecked") var profiles=(java.util.Map<java.util.UUID,Object>)manager.getField("PLAYERS_MAP").get(null);
            Object profile=profiles.get(player);
            double nativeScale=(double)profile.getClass().getMethod("getPlayerVolume").invoke(profile);
            if(Math.abs(nativeScale-.8*(1-20.0/60))>.001)throw new AssertionError("Talking Heads native dB mapping changed");
            utils.getMethod("applyHeadVolume",java.util.UUID.class,double.class).invoke(null,player,-127.0);
            if(profiles.containsKey(player))throw new AssertionError("Talking Heads did not clear silence");
            Class<?> compat=Class.forName("local.plasmomusic.TalkingHeadsCompat",true,target);
            var publish=compat.getDeclaredMethod("publish",double.class);publish.setAccessible(true);publish.invoke(null,-20.0);
            var method=compat.getDeclaredField("applyHeadVolume");method.setAccessible(true);
            if(method.get(null)==null)throw new AssertionError("Talking Heads API was not discovered");
            System.out.println("PASS TALKING HEADS 1.1.3: native dB scaling and optional bridge resolved");
        }
        if (args.length > 2 && "settings-only".equals(args[2])) return;
        knot.addToClassPath(java.nio.file.Path.of(args[1]));
        Class.forName("local.plasmomusic.MixTest",true,target).getMethod("run").invoke(null);
        Class.forName("local.plasmomusic.ControlsTest",true,target).getMethod("run").invoke(null);
        Class.forName("local.plasmomusic.PreviewLifecycleTest",true,target).getMethod("run").invoke(null);
        Class.forName("local.plasmomusic.LifecycleTest",true,target).getMethod("run").invoke(null);
        Class.forName("local.plasmomusic.SelfIconTest",true,target).getMethod("run").invoke(null);
    }
}
