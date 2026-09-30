package local.plasmomusic;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Optional effects apply only to the shared music, never to microphone samples. */
public record EffectSettings(boolean clean, boolean limiter, int pitch) {
    public static EffectSettings defaults() { return new EffectSettings(false,false,0); }
    public EffectSettings { pitch=Math.max(-4,Math.min(4,pitch)); }

    public static EffectSettings read(Path file) throws IOException {
        Properties p=new Properties();
        if(Files.exists(file))try(var in=Files.newInputStream(file)){p.load(in);}
        // Legacy reverb, radio and echo keys are intentionally ignored.
        int pitch;
        try{pitch=Integer.parseInt(p.getProperty("pitch","0"));}
        catch(NumberFormatException e){pitch=0;}
        return new EffectSettings(Boolean.parseBoolean(p.getProperty("clean","false")),
            Boolean.parseBoolean(p.getProperty("limiter","false")),pitch);
    }
    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Properties p=new Properties();
        p.setProperty("clean",Boolean.toString(clean));
        p.setProperty("limiter",Boolean.toString(limiter));
        p.setProperty("pitch",Integer.toString(pitch));
        Path tmp=Files.createTempFile(file.getParent(),"music-effects-",".tmp");
        try{
            try(var out=Files.newOutputStream(tmp)){p.store(out,"Plasmo System Music optional effects (music only)");}
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
    public EffectSettings withClean(boolean value){return new EffectSettings(value,limiter,pitch);}
    public EffectSettings withLimiter(boolean value){return new EffectSettings(clean,value,pitch);}
    public EffectSettings withPitch(int value){return new EffectSettings(clean,limiter,value);}
}
