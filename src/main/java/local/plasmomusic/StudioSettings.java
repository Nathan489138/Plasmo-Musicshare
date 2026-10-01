package local.plasmomusic;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Voice presets and independent singing reverb; legacy music preset keys are discarded. */
public record StudioSettings(Style voiceStyle,int voiceStrength,Style musicStyle,int musicStrength,
                             Room voiceRoom,int voiceReverb,Room musicRoom,int musicReverb,int previewDelay,int previewVolume,boolean previewMusic,
                             int echoDelay,int echoFeedback,int ancestorFirst,int ancestorSecond,DspSettings dsp){
    public enum Style {
        NONE("原声"),ECHO("回声"),ROBOT("机器人声"),CHILD("小孩"),VILLAIN("反派"),UNDERWATER("水下低通"),CUSTOM("自定义");
        public final String label;Style(String label){this.label=label;}
    }
    public enum Room {
        OFF("关闭",0,0),SMALL("小房间",.45,8),STUDIO("录音棚 · 唱歌",1.1,15),STAGE("舞台",1.7,22),HALL("大厅",2.8,35);
        public final String label;final double decay,preDelay;
        Room(String label,double decay,double preDelay){this.label=label;this.decay=decay;this.preDelay=preDelay;}
    }
    public StudioSettings{
        voiceStrength=clamp(voiceStrength);musicStyle=Style.NONE;musicStrength=0;
        voiceReverb=clamp(voiceReverb);musicReverb=clamp(musicReverb);previewDelay=Math.max(0,Math.min(10,previewDelay));previewVolume=clamp(previewVolume);
        echoDelay=Math.max(100,Math.min(800,echoDelay));echoFeedback=Math.max(5,Math.min(65,echoFeedback));ancestorFirst=clamp(ancestorFirst);ancestorSecond=clamp(ancestorSecond);
    }
    private static int clamp(int v){return Math.max(0,Math.min(100,v));}
    public static StudioSettings defaults(){return new StudioSettings(Style.NONE,100,Style.NONE,0,Room.OFF,25,Room.OFF,15,1,70,true,320,40,45,35,DspSettings.defaults());}
    public boolean voiceActive(){return dsp.active()&&voiceStrength>0||voiceRoom!=Room.OFF&&voiceReverb>0;}
    public StudioSettings voice(Style style,int strength,Room room,int reverb){return new StudioSettings(style,strength,Style.NONE,0,room,reverb,musicRoom,musicReverb,previewDelay,previewVolume,previewMusic,echoDelay,echoFeedback,ancestorFirst,ancestorSecond,style==voiceStyle?dsp:DspSettings.preset(style));}
    public StudioSettings music(Style ignored,int ignoredStrength,Room room,int reverb){return new StudioSettings(voiceStyle,voiceStrength,Style.NONE,0,voiceRoom,voiceReverb,room,reverb,previewDelay,previewVolume,previewMusic,echoDelay,echoFeedback,ancestorFirst,ancestorSecond,dsp);}
    public StudioSettings preview(int delay,int volume,boolean music){return new StudioSettings(voiceStyle,voiceStrength,Style.NONE,0,voiceRoom,voiceReverb,musicRoom,musicReverb,delay,volume,music,echoDelay,echoFeedback,ancestorFirst,ancestorSecond,dsp);}
    public StudioSettings preset(int delay,int feedback,int first,int second){return new StudioSettings(voiceStyle,voiceStrength,Style.NONE,0,voiceRoom,voiceReverb,musicRoom,musicReverb,previewDelay,previewVolume,previewMusic,delay,feedback,first,second,dsp);}
    public StudioSettings select(Style style){return new StudioSettings(style,100,Style.NONE,0,voiceRoom,voiceReverb,musicRoom,musicReverb,previewDelay,previewVolume,previewMusic,320,40,ancestorFirst,ancestorSecond,DspSettings.preset(style));}
    public StudioSettings panel(DspSettings panel){return new StudioSettings(Style.CUSTOM,voiceStrength,Style.NONE,0,voiceRoom,voiceReverb,musicRoom,musicReverb,previewDelay,previewVolume,previewMusic,echoDelay,echoFeedback,ancestorFirst,ancestorSecond,panel);}
    private static int number(Properties p,String key,int fallback){try{return Integer.parseInt(p.getProperty(key));}catch(Exception e){return fallback;}}
    private static <E extends Enum<E>> E choice(Properties p,String key,E fallback){try{return Enum.valueOf(fallback.getDeclaringClass(),p.getProperty(key));}catch(Exception e){return fallback;}}
    public static StudioSettings read(Path path)throws IOException{
        Properties p=new Properties();if(Files.exists(path))try(var in=Files.newInputStream(path)){p.load(in);}
        String schema=p.getProperty("voice.schema"),oldStyle=p.getProperty("voice.style","");
        boolean modern="2".equals(schema)||"3".equals(schema)||"4".equals(schema);
        boolean replaced=modern&&(oldStyle.equals("BABY")||oldStyle.equals("ANCESTOR"));
        Style style=!modern?Style.NONE:oldStyle.equals("BABY")?Style.CHILD:oldStyle.equals("ANCESTOR")?Style.VILLAIN:choice(p,"voice.style",Style.NONE);
        DspSettings panel=replaced?DspSettings.preset(style):DspSettings.read(p,style);
        // Update the previous stock robot's overly narrow filter, preserving custom racks.
        if(!"4".equals(schema)&&style==Style.ROBOT&&panel.equals(new DspSettings(true,25,100,4,false,100,0,DspSettings.Filter.BAND,1500,false,100,true,false)))panel=DspSettings.preset(style);
        return new StudioSettings(style,number(p,"voice.strength",100),Style.NONE,0,
            choice(p,"voice.room",Room.OFF),number(p,"voice.reverb",25),choice(p,"music.room",Room.OFF),number(p,"music.reverb",15),
            number(p,"preview.delay",1),number(p,"preview.volume",70),Boolean.parseBoolean(p.getProperty("preview.music","true")),
            number(p,"voice.echo.ms",320),number(p,"voice.echo.feedback",40),number(p,"voice.ancestor.first",45),number(p,"voice.ancestor.second",35),panel);
    }
    public void save(Path path)throws IOException{
        Files.createDirectories(path.getParent());Properties p=new Properties();
        dsp.write(p);p.setProperty("voice.schema","4");p.setProperty("voice.style",voiceStyle.name());p.setProperty("voice.strength",""+voiceStrength);
        p.setProperty("voice.room",voiceRoom.name());p.setProperty("voice.reverb",""+voiceReverb);p.setProperty("music.room",musicRoom.name());p.setProperty("music.reverb",""+musicReverb);
        p.setProperty("preview.delay",""+previewDelay);p.setProperty("preview.volume",""+previewVolume);p.setProperty("preview.music",""+previewMusic);
        p.setProperty("voice.echo.ms",""+echoDelay);p.setProperty("voice.echo.feedback",""+echoFeedback);p.setProperty("voice.ancestor.first",""+ancestorFirst);p.setProperty("voice.ancestor.second",""+ancestorSecond);
        Path temp=Files.createTempFile(path.getParent(),"music-studio-",".tmp");
        try{try(var out=Files.newOutputStream(temp)){p.store(out,"Plasmo Musicshare voice presets and singing reverb");}
            try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}
