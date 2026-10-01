package local.plasmomusic;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;

public record Settings(String device,boolean enabled,int volume,boolean stereo,boolean forceOpen) {
    public static final int MAX_VOLUME=500;
    public Settings(String device,boolean enabled,int volume,boolean stereo){this(device,enabled,volume,stereo,false);}
    public Settings{volume=Math.max(0,Math.min(MAX_VOLUME,volume));}
    public static Settings read(Path file) throws IOException {
        Properties p=new Properties();
        if(Files.exists(file)) try(var in=Files.newInputStream(file)){p.load(in);}
        int v; try {v=Integer.parseInt(p.getProperty("volume","50"));}catch(NumberFormatException e){v=50;}
        return new Settings(p.getProperty("device","exclude-game"),Boolean.parseBoolean(p.getProperty("enabled","false")),
            v,Boolean.parseBoolean(p.getProperty("stereo","true")),Boolean.parseBoolean(p.getProperty("forceOpen","false")));
    }
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent()); Properties p=new Properties();
        p.setProperty("device",device);p.setProperty("enabled",Boolean.toString(enabled));p.setProperty("volume",Integer.toString(volume));p.setProperty("stereo",Boolean.toString(stereo));
        p.setProperty("forceOpen",Boolean.toString(forceOpen));
        Path tmp=Files.createTempFile(file.getParent(),"music-",".tmp");
        try {try(var out=Files.newOutputStream(tmp)){p.store(out,"Plasmo System Music - explicit render endpoint, never microphone");}
            try {Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
