package local.plasmomusic;

import java.util.*;
import su.plo.config.entry.*;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.tab.TabWidget;
import su.plo.voice.client.gui.settings.widget.DropDownWidget;

/** Separate effects and singing/preview pages, using PV's own widgets and test controller. */
public final class EffectsTabWidget extends TabWidget {
    private enum DelayChoice {
        NONE,ONE,TWO,THREE,FOUR,FIVE,SIX,SEVEN,EIGHT,NINE,TEN;
        String label(){return ordinal()==0?"无延迟":ordinal()+" 秒";}
    }
    private final MusicAddon app;private final VoiceSettingsScreen screen;private final boolean singing;
    private boolean syncing;
    private StudioSettings shown;
    private final List<Runnable> dropdownSyncs=new ArrayList<>();
    private final EnumConfigEntry<StudioSettings.Style> voiceStyle=new EnumConfigEntry<>(StudioSettings.Style.class,StudioSettings.Style.NONE);
    private final EnumConfigEntry<StudioSettings.Room> voiceRoom=new EnumConfigEntry<>(StudioSettings.Room.class,StudioSettings.Room.OFF),musicRoom=new EnumConfigEntry<>(StudioSettings.Room.class,StudioSettings.Room.OFF);
    private final IntConfigEntry voiceStrength=new IntConfigEntry(100,0,100),voiceWet=new IntConfigEntry(25,0,100),musicWet=new IntConfigEntry(15,0,100);
    private final IntConfigEntry echoDelay=new IntConfigEntry(320,100,800),echoFeedback=new IntConfigEntry(40,5,65);
    private final BooleanConfigEntry flanger=new BooleanConfigEntry(false),echo=new BooleanConfigEntry(false),stretch=new BooleanConfigEntry(false),robot=new BooleanConfigEntry(false),ancestor=new BooleanConfigEntry(false);
    private final IntConfigEntry flangerWet=new IntConfigEntry(40,0,100),flangerRate=new IntConfigEntry(50,10,300),flangerDepth=new IntConfigEntry(6,1,15),echoGain=new IntConfigEntry(100,0,250),pitch=new IntConfigEntry(0,-12,12),cutoff=new IntConfigEntry(4000,100,12000),speed=new IntConfigEntry(100,75,150);
    private final EnumConfigEntry<DspSettings.Filter> filter=new EnumConfigEntry<>(DspSettings.Filter.class,DspSettings.Filter.OFF);
    private final EnumConfigEntry<DelayChoice> delay=new EnumConfigEntry<>(DelayChoice.class,DelayChoice.ONE);
    private final IntConfigEntry previewVolume=new IntConfigEntry(70,0,100);
    private final BooleanConfigEntry preview=new BooleanConfigEntry(false),previewMusic=new BooleanConfigEntry(true);
    public EffectsTabWidget(VoiceSettingsScreen screen,PlasmoVoiceClient voice,VoiceClientConfig config,MusicAddon app,boolean singing){
        super(screen,voice,config);this.app=app;this.screen=screen;this.singing=singing;sync();
        voiceStyle.addChangeListener(v->{if(!syncing){app.updateStudio(v==StudioSettings.Style.CUSTOM?app.studio.panel(app.studio.dsp()):app.studio.select(v));sync();updateOptionEntries(w->true);}});voiceStrength.addChangeListener(v->saveVoice());voiceRoom.addChangeListener(v->saveVoice());voiceWet.addChangeListener(v->saveVoice());
        musicRoom.addChangeListener(v->saveMusic());musicWet.addChangeListener(v->saveMusic());
        echoDelay.addChangeListener(v->savePreset());echoFeedback.addChangeListener(v->savePreset());
        flanger.addChangeListener(v->savePanel());echo.addChangeListener(v->savePanel());stretch.addChangeListener(v->savePanel());robot.addChangeListener(v->savePanel());ancestor.addChangeListener(v->savePanel());
        flangerWet.addChangeListener(v->savePanel());flangerRate.addChangeListener(v->savePanel());flangerDepth.addChangeListener(v->savePanel());echoGain.addChangeListener(v->savePanel());pitch.addChangeListener(v->savePanel());filter.addChangeListener(v->savePanel());cutoff.addChangeListener(v->savePanel());speed.addChangeListener(v->savePanel());
        delay.addChangeListener(v->savePreview());previewVolume.addChangeListener(v->savePreview());previewMusic.addChangeListener(v->savePreview());
        preview.addChangeListener(v->{if(!syncing){if(v)screen.getTestController().start();else screen.getTestController().stop();}});
    }
    private static McTextComponent text(String value){return McTextComponent.literal(value);}
    @Override protected <E extends Enum<E>> OptionEntry<DropDownWidget> createDropDownEntry(McTextComponent label,McTextComponent tip,Class<E> type,List<McTextComponent> names,EnumConfigEntry<E> entry,boolean scroll){
        var row=super.createDropDownEntry(label,tip,type,names,entry,scroll);
        DropDownWidget widget=(DropDownWidget)row.widgets().get(0);
        dropdownSyncs.add(()->widget.setText(names.get(entry.value().ordinal())));return row;
    }
    private void saveVoice(){if(!syncing)app.updateStudio(app.studio.voice(voiceStyle.value(),voiceStrength.value(),voiceRoom.value(),voiceWet.value()));}
    private void saveMusic(){if(!syncing)app.updateStudio(app.studio.music(StudioSettings.Style.NONE,0,musicRoom.value(),musicWet.value()));}
    private void savePreset(){if(!syncing)app.updateStudio(app.studio.preset(echoDelay.value(),echoFeedback.value(),app.studio.ancestorFirst(),app.studio.ancestorSecond()));}
    private void savePanel(){if(!syncing)app.updateStudio(app.studio.panel(new DspSettings(flanger.value(),flangerWet.value(),flangerRate.value(),flangerDepth.value(),echo.value(),echoGain.value(),pitch.value(),filter.value(),cutoff.value(),stretch.value(),speed.value(),robot.value(),ancestor.value())));}
    private void savePreview(){if(!syncing)app.updateStudio(app.studio.preview(delay.value().ordinal(),previewVolume.value(),previewMusic.value()));}
    @Override public void init(){
        dropdownSyncs.clear();super.init();sync();
        if(!singing){
            addEntry(new CategoryEntry(text("TarsosDSP 人声面板 · 不处理音乐")));
            addEntry(createDropDownEntry(text("预设"),text("选择预设会填入面板参数；手动调整后为自定义；原声清空面板效果"),StudioSettings.Style.class,Arrays.stream(StudioSettings.Style.values()).map(s->text(s.label)).toList(),voiceStyle,true));
            addEntry(createIntSliderWidget(text("人声音效强度"),text("0% 为原声；唱歌建议先试听再开启"),voiceStrength,"%"));
            addEntry(new CategoryEntry(text("镶边")));
            addEntry(createToggleEntry(text("开启镶边"),text("随时间扫动的短延迟效果"),flanger));
            addEntry(createIntSliderWidget(text("镶边混合量"),text("0% 原声，100% 延迟声"),flangerWet,"%"));
            addEntry(createIntSliderWidget(text("扫动速率"),text("50 对应 0.5 Hz，100 对应 1 Hz"),flangerRate," ×0.01 Hz"));
            addEntry(createIntSliderWidget(text("镶边深度"),text("最大延迟长度"),flangerDepth," ms"));
            addEntry(new CategoryEntry(text("延迟 / 回声")));
            addEntry(createToggleEntry(text("开启延迟"),text("保留原声并加入衰减回声"),echo));
            addEntry(createIntSliderWidget(text("回声间隔"),text("普通回声与极阴老祖后置回声的间隔"),echoDelay," ms"));
            addEntry(createIntSliderWidget(text("回声反馈"),text("越高重复越久；最大 65%，避免无限回声"),echoFeedback,"%"));
            addEntry(createIntSliderWidget(text("回声音量"),text("极阴老祖默认 180%，后置厚重回声比原声更响；满幅声音仍会限幅"),echoGain,"%"));
            addEntry(new CategoryEntry(text("变调与滤波")));
            addEntry(createIntSliderWidget(text("变调"),text("0 为原音调；极阴老祖只改变后置回声的音调"),pitch," 半音"));
            addEntry(createDropDownEntry(text("滤波模式"),text("关闭、低通、高通或带通"),DspSettings.Filter.class,Arrays.stream(DspSettings.Filter.values()).map(v->text(v.label)).toList(),filter,true));
            addEntry(createIntSliderWidget(text("截止 / 中心频率"),text("带通模式为中心频率"),cutoff," Hz"));
            addEntry(new CategoryEntry(text("时间拉伸")));
            addEntry(createToggleEntry(text("开启时间拉伸"),text("WSOLA 保持音调改变语速，实验性实时效果"),stretch));
            addEntry(createIntSliderWidget(text("语速"),text("100% 原速；减速缓冲上限 0.5 秒，超出会跳过部分旧片段；加速可能有空隙"),speed,"%"));
            addEntry(new CategoryEntry(text("特殊音色")));
            addEntry(createToggleEntry(text("机器人载波"),text("加入机械环形调制"),robot));
            addEntry(createToggleEntry(text("原声 + 厚重后置回声"),text("原声立即正常播放，其余面板效果只处理后置回声"),ancestor));
            addEntry(new CategoryEntry(text("唱歌混响 · 两个通道分别调整")));
            var names=Arrays.stream(StudioSettings.Room.values()).map(r->text(r.label)).toList();
            addEntry(createDropDownEntry(text("人声混响预设"),text("默认关闭；录音棚适合先尝试唱歌"),StudioSettings.Room.class,names,voiceRoom,true));
            addEntry(createIntSliderWidget(text("人声混响强度"),text("保留直接人声，再加入混响；快捷键屏蔽时人声混响暂停"),voiceWet,"%"));
            addEntry(createDropDownEntry(text("音乐混响预设"),text("独立于人声混响和趣味音效"),StudioSettings.Room.class,names,musicRoom,true));
            addEntry(createIntSliderWidget(text("音乐混响强度"),text("伴奏建议使用较小强度"),musicWet,"%"));
        }
        if(singing){
        addEntry(new CategoryEntry(text("本机试听 · 试听时暂停对外发送")));
        addEntry(createToggleEntry(text("开启试听"),text("使用 Plasmo Voice 选定的耳机；关闭试听或关闭设置后恢复正常发送"),preview));
        addEntry(createDropDownEntry(text("试听延迟"),text("无延迟或 1～10 秒；只影响本机，修改后清空旧缓冲"),DelayChoice.class,Arrays.stream(DelayChoice.values()).map(v->text(v.label())).toList(),delay,true));
        addEntry(createIntSliderWidget(text("试听音量"),text("只调整本机试听，不改变发送音量"),previewVolume,"%"));
        addEntry(createToggleEntry(text("试听包含音乐"),text("关闭可单独听人声音效；开启时音乐不共享也可本机试听"),previewMusic));
        }
    }
    private void sync(){syncing=true;try{
        StudioSettings s=app.studio;voiceStyle.set(s.voiceStyle());voiceStrength.set(s.voiceStrength());
        echoDelay.set(s.echoDelay());echoFeedback.set(s.echoFeedback());
        DspSettings d=s.dsp();flanger.set(d.flanger());flangerWet.set(d.flangerWet());flangerRate.set(d.flangerRate());flangerDepth.set(d.flangerDepth());echo.set(d.delay());echoGain.set(d.delayGain());pitch.set(d.pitch());filter.set(d.filter());cutoff.set(d.cutoff());stretch.set(d.stretch());speed.set(d.speed());robot.set(d.robot());ancestor.set(d.ancestor());
        voiceRoom.set(s.voiceRoom());musicRoom.set(s.musicRoom());voiceWet.set(s.voiceReverb());musicWet.set(s.musicReverb());
        delay.set(DelayChoice.values()[s.previewDelay()]);previewVolume.set(s.previewVolume());previewMusic.set(s.previewMusic());preview.set(screen.getTestController().isActive());
    }finally{syncing=false;}dropdownSyncs.forEach(Runnable::run);}
    @Override public void tick(){super.tick();StudioSettings current=app.studio;sync();if(!current.equals(shown)){shown=current;updateOptionEntries(w->true);}}
}
