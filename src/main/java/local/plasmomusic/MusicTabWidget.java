package local.plasmomusic;

import java.awt.Color;
import su.plo.config.entry.BooleanConfigEntry;
import su.plo.config.entry.IntConfigEntry;
import su.plo.lib.mod.client.render.gui.GuiRenderContext;
import su.plo.slib.api.chat.component.McTextComponent;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.client.config.VoiceClientConfig;
import su.plo.voice.client.gui.settings.VoiceSettingsScreen;
import su.plo.voice.client.gui.settings.tab.TabWidget;
import su.plo.voice.client.gui.settings.widget.IntSliderWidget;

/** Native settings for music sharing and the independent 快捷键 voice-censor switch. */
public final class MusicTabWidget extends TabWidget {
    private static final Color LABEL = new Color(225, 236, 245);
    private static final Color MUTED = new Color(160, 181, 194);
    private static final Color TRACK = new Color(34, 47, 60);
    private static final Color INACTIVE = new Color(55, 74, 88);
    private static final Color GREEN = new Color(63, 210, 169);
    private static final Color YELLOW = new Color(255, 197, 85);
    private static final Color RED = new Color(255, 112, 91);

    private final MusicAddon app;
    private final BooleanConfigEntry sharing = new BooleanConfigEntry(false);
    private final BooleanConfigEntry forceOpen = new BooleanConfigEntry(false);
    private final BooleanConfigEntry censor = new BooleanConfigEntry(false);
    private final IntConfigEntry volume = new IntConfigEntry(50, 0, 150);
    private final BooleanConfigEntry stereo = new BooleanConfigEntry(true);
    private boolean syncing;
    private double captureDisplay, outgoingDisplay;
    private long lastPaintNanos;

    public MusicTabWidget(VoiceSettingsScreen parent, PlasmoVoiceClient voice, VoiceClientConfig config, MusicAddon app) {
        super(parent, voice, config);
        this.app = app;
        syncFromApp();
        sharing.addChangeListener(value -> { if (!syncing) app.setEnabled(value); });
        forceOpen.addChangeListener(value -> { if (!syncing) app.setForceOpen(value); });
        censor.addChangeListener(value -> { if (!syncing) app.setCensorEnabled(value); });
        volume.addChangeListener(value -> {
            if (!syncing) {
                Settings old = app.settings;
                app.update(new Settings("exclude-game", old.enabled(), value, old.stereo(), old.forceOpen()));
            }
        });
        stereo.addChangeListener(value -> {
            if (!syncing) {
                Settings old = app.settings;
                app.update(new Settings("exclude-game", old.enabled(), old.volume(), value, old.forceOpen()));
            }
        });
    }

    @Override public void init() {
        super.init();
        syncFromApp();
        addEntry(new CategoryEntry(McTextComponent.literal("共享音乐")));
        addEntry(createToggleEntry(McTextComponent.literal("开启共享"),
            McTextComponent.literal("附近玩家将听到电脑其他应用的声音；不会抓取 Minecraft"), sharing));
        addEntry(createToggleEntry(McTextComponent.literal("强制话筒开启"),
            McTextComponent.literal("共享期间低于感应阈值也持续发送，远端喇叭可能常亮；真人麦克风仍按原有激活规则，静音和权限仍有效"), forceOpen));
        addEntry(createIntSliderWidget(McTextComponent.literal("发送音量"),
            McTextComponent.literal("只调整分享给附近玩家的音乐音量"), volume, "%"));
        addEntry(createToggleEntry(McTextComponent.literal("立体声"),
            McTextComponent.literal("服务器不支持时自动使用单声道"), stereo));
        addEntry(new CategoryEntry(McTextComponent.literal("人声屏蔽")));
        addEntry(createToggleEntry(McTextComponent.literal("屏蔽人声（按住快捷键）"),
            McTextComponent.literal("只替换人声：达到感应阈值播放屏蔽音，低于阈值静音；音乐不受影响"), censor));
        addEntry(new CategoryEntry(McTextComponent.literal("实时电平")));
        addEntry(new MeterEntry());
    }

    @Override public void tick() {
        super.tick();
        boolean sliderChanged = !volume.value().equals(app.settings.volume());
        syncFromApp();
        if (sliderChanged) updateOptionEntries(widget -> widget instanceof IntSliderWidget);
    }

    private void syncFromApp() {
        Settings current = app.settings;
        syncing = true;
        try {
            sharing.set(current.enabled());
            forceOpen.set(current.forceOpen());
            censor.set(app.censorEnabled);
            volume.set(current.volume());
            stereo.set(current.stereo());
        } finally {
            syncing = false;
        }
    }

    private static double db(double amplitude) {
        return amplitude > 0 ? 20.0 * Math.log10(amplitude) : -127.0;
    }
    private static double fraction(double db) {
        return Math.max(0.0, Math.min(1.0, (db + 60.0) / 60.0));
    }
    private static double approach(double from, double to, double dt) {
        double constant = to > from ? 0.09 : 0.28;
        return from + (to - from) * (1.0 - Math.exp(-dt / constant));
    }
    private static String level(double db) {
        return db <= -60 ? "静音" : Math.round(db) + " dB";
    }
    private void bar(GuiRenderContext context, int left, int top, int width, double value, boolean marker) {
        context.fill(left - 3, top - 3, left + width + 3, top + 11, TRACK);
        int count = 28;
        int active = (int)Math.round(value * count);
        for (int i = 0; i < count; i++) {
            int x0 = left + i * width / count + 1;
            int x1 = left + (i + 1) * width / count - 1;
            Color color = i >= active ? INACTIVE : i < 19 ? GREEN : i < 25 ? YELLOW : RED;
            context.fill(x0, top, x1, top + 8, color);
        }
        if (marker) {
            int x = left + (int)Math.round(width * fraction(app.speakerThresholdDb()));
            context.fill(x, top - 3, x + 2, top + 11, Color.WHITE);
        }
    }

    private final class MeterEntry extends Entry {
        private MeterEntry() { super(120); }
        @Override public void render(GuiRenderContext context, int index, int x, int y, int entryWidth,
                                     int mouseX, int mouseY, boolean hovered, float delta) {
            long now = System.nanoTime();
            double dt = lastPaintNanos == 0 ? 0.016 : Math.max(0.005, Math.min(0.1, (now - lastPaintNanos) / 1_000_000_000.0));
            lastPaintNanos = now;
            double captureDb = db(app.buffer.peak);
            double outgoingDb = app.outgoingDecibels();
            captureDisplay = approach(captureDisplay, fraction(captureDb), dt);
            outgoingDisplay = approach(outgoingDisplay, fraction(outgoingDb), dt);
            int left = x + 24;
            int width = Math.max(100, entryWidth - 48);
            context.drawString("音源采集  ·  " + level(captureDb), left, y + 3, LABEL);
            bar(context, left, y + 20, width, captureDisplay, false);
            context.drawString("实际发送（含麦克风）  ·  " + level(outgoingDb), left, y + 43, LABEL);
            bar(context, left, y + 60, width, outgoingDisplay, true);
            context.drawString("音源：其他应用，排除 Minecraft  ·  白线为图标阈值", left, y + 79, MUTED);
            String status = app.displayStatus;
            if (status.length() > 44) status = status.substring(0, 43) + "…";
            context.drawString(status + "  ·  按住快捷键屏蔽 / 快捷键开关音乐", left, y + 96, MUTED);
        }
    }
}
