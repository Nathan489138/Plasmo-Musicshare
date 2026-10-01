package local.plasmomusic;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Program identities persist; transient PIDs never become saved capture targets. */
public record CaptureSelection(boolean global,Set<String> programs) {
    public CaptureSelection {
        TreeSet<String> normalized=new TreeSet<>();
        for(String program:programs)if(program!=null&&!program.isBlank())normalized.add(normalize(program));
        programs=Collections.unmodifiableSet(normalized);
    }
    public static CaptureSelection defaults(){return new CaptureSelection(true,Set.of());}
    public static String normalize(String path){return path.replace('/','\\').toLowerCase(Locale.ROOT);}
    public String key(){return global?"exclude-game":"selected:"+String.join("|",programs);}
    public static CaptureSelection read(Path file)throws IOException {
        Properties p=new Properties();if(Files.exists(file))try(var input=Files.newInputStream(file)){p.load(input);}
        Set<String> programs=new HashSet<>();
        int count;try{count=Math.max(0,Math.min(4096,Integer.parseInt(p.getProperty("program.count","0"))));}catch(NumberFormatException ignored){count=0;}
        for(int i=0;i<count;i++){String path=p.getProperty("program."+i,"");if(!path.isBlank())programs.add(path);}
        return new CaptureSelection(!p.getProperty("mode","global").equals("selected"),programs);
    }
    public void save(Path file)throws IOException {
        Files.createDirectories(file.getParent());Properties p=new Properties();
        p.setProperty("mode",global?"global":"selected");p.setProperty("program.count",Integer.toString(programs.size()));
        int i=0;for(String path:programs)p.setProperty("program."+i++,path);
        Path tmp=Files.createTempFile(file.getParent(),"capture-",".tmp");
        try{try(var output=Files.newOutputStream(tmp)){p.store(output,"Musicshare selected executable paths, no command-line arguments");}
            try{Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(tmp);}
    }
}
