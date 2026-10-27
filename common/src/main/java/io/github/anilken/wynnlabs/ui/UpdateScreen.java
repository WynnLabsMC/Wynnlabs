package io.github.anilken.wynnlabs.ui;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Platform;
import io.github.anilken.wynnlabs.core.ReleaseNotes;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.core.UpdateDownload;
import io.github.anilken.wynnlabs.core.UpdatePrefs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * The update window, opened from the chat line's [Details] or the About page: a soft card with
 * the new version and yours, Summary / Changes as chip tabs (what Update does in three steps, or
 * the release notes, scrollable), the download's state over the foot, and the foot itself: Not
 * now and Skip this version on the left, Update (gold) and Update &amp; exit on the right, or Quit
 * now once the jar is ready. ✕ and Esc close it. Nothing here talks to the game server; the
 * download is GitHub only ({@link UpdateDownload}).
 */
public final class UpdateScreen extends Screen {

    private final Screen parent;
    private boolean changelog;
    private int scroll;
    /** Update &amp; exit was pressed: the game closes once the jar is ready. */
    private boolean exitWhenReady;
    private List<ReleaseNotes.Line> notes;
    private String notesFor;

    /** This frame's click areas and what a click does. */
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();

    public UpdateScreen(Screen parent, boolean changelog) {
        super(L.c("wynnlabs.update.screen_title"));
        this.parent = parent;
        this.changelog = changelog;
    }

    /**
     * On this client only: {@code /wynnlabs-update} (the chat line's [Update]) downloads at once
     * and says how it went in chat; {@code /wynnlabs-update details} ([Details]) opens this window.
     * With no newer release known, both only say so.
     */
    public static <S> LiteralArgumentBuilder<S> command(BiConsumer<S, Component> feedback) {
        return LiteralArgumentBuilder.<S>literal("wynnlabs-update")
                .executes(c -> {
                    UpdateCheck.updateFromChat(line -> feedback.accept(c.getSource(), line));
                    return 1;
                })
                .then(LiteralArgumentBuilder.<S>literal("details").executes(c -> {
                    if (UpdateCheck.state().status() != UpdateCheck.Status.NEWER) {
                        feedback.accept(c.getSource(), L.c("wynnlabs.update.none_known").withStyle(ChatFormatting.GRAY));
                        return 0;
                    }
                    Minecraft mc = Minecraft.getInstance();
                    mc.schedule(() -> mc.setScreen(new UpdateScreen(mc.screen, false)));   // after the chat box closed
                    return 1;
                }));
    }

    @Override
    protected void init() {
        UiFont.apply(this);
        super.init();
    }

    @Override
    public void tick() {
        UpdateDownload.Phase p = UpdateDownload.phase();
        if (exitWhenReady && p == UpdateDownload.Phase.READY) {
            exitWhenReady = false;
            minecraft.stop();                   // the install runs while the game closes
        } else if (exitWhenReady && p == UpdateDownload.Phase.FAILED) {
            exitWhenReady = false;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        MenuScale.render(this, g, mx, my, (x, y) -> draw(g, x, y, pt));
    }

    private void draw(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        hits.clear();
        tip = null;
        UpdateCheck.State s = UpdateCheck.state();
        int ww = Math.min(width - 16, 400);
        int wh = Math.min(height - 16, 250);
        int wx = (width - ww) / 2;
        int wy = (height - wh) / 2;
        // the menu's window over the dimmed game: the wooden frame, the parchment page, the close seal
        KitFrame.dialog(g, width, height, wx, wy, ww, wh);
        int x = wx + Theme.SP_6;
        int y = wy + Theme.SP_7;                       // clear of the parchment's rim and corner ornaments
        int w = ww - 2 * Theme.SP_6;
        int bottom = wy + wh - Theme.SP_6;
        boolean newer = s.status() == UpdateCheck.Status.NEWER;
        // the title row: the state's tile and the version (close is the frame's seal)
        int tile = 22;
        Art.box(g, x, y, tile, tile, 6, 0xFFF6E7B8, Theme.GOLD_DEEP);
        Art.icon(g, newer ? "arrow-up" : "check", x + 6, y + 6, 10, newer ? Theme.GOLD_DEEP : Theme.GOOD);
        int[] cr = KitFrame.closeAt(width, wx, wy, ww);
        boolean closeHover = mx >= cr[0] && mx < cr[0] + cr[2] && my >= cr[1] && my < cr[1] + cr[3];
        KitFrame.closeOutside(g, width, wx, wy, ww, closeHover);
        if (closeHover) {
            tip = List.of(L.c("wynnlabs.update.close"));
        }
        hits.add(new Hit(cr[0], cr[1], cr[2], cr[3], this::onClose));
        int room = x + w;
        int tx = x + tile + Theme.SP_3;
        String title = newer ? L.t("wynnlabs.update.screen_newer", s.latest()) : L.t("wynnlabs.about.uptodate_title");
        Type.HEADING.draw(g, font, Type.HEADING.fit(font, title, room - tx), tx, y - 1, Theme.INK);
        String mine = vName(s.own());
        String sub = s.published() > 0 ? L.t("wynnlabs.about.newer_sub", mine, MenuPaint.ago(s.published()))
                : L.t("wynnlabs.about.newer_sub_nodate", mine);
        if (newer && s.asset() != null) {
            sub += " · " + mb(s.asset().size()) + " MB";
        }
        Type.META.draw(g, font, Type.META.fit(font, sub, room - tx), tx, y + Type.HEADING.lineHeight, Theme.INK_3);
        y += tile + Theme.SP_4;
        // Özet / Değişiklikler as chip tabs
        if (newer) {
            String[][] tabs = {{"list-check", L.t("wynnlabs.update.summary_button")}, {"script", L.t("wynnlabs.update.changelog")}};
            int tbx = x;
            for (int i = 0; i < 2; i++) {
                int tw = Ui.iconChipWidth(font, tabs[i][0], tabs[i][1], false);
                Ui.iconChip(g, font, tabs[i][0], tabs[i][1], false, tbx, y, tw, changelog == (i == 1), mx, my);
                boolean notes = i == 1;
                hits.add(new Hit(tbx, y, tw, Ui.CHIP_H, () -> changelog = notes));
                tbx += tw + Theme.SP_2;
            }
            y += Ui.CHIP_H + Theme.SP_3;
        }
        Kit.hair(g, x, y, w, 1, Theme.LINE);
        y += 1 + Theme.SP_4;
        // the foot: the state over the buttons, the one main action on the right
        int footY = bottom - Ui.BUTTON_H;
        int statusH = drawStatus(g, s, x, footY - Theme.SP_3, w);
        int footLine = footY - Theme.SP_3 - statusH - (statusH > 0 ? Theme.SP_3 : 0);
        Kit.hair(g, x, footLine, w, 1, 0x40A08A5A);
        drawFoot(g, s, newer, x, footY, w, mx, my);
        int bodyBottom = footLine - Theme.SP_3;
        if (changelog && newer) {
            drawNotes(g, s, x, y, w, bodyBottom - y, mx, my);
        } else {
            notesBar.place(0, 0, 0, 1, 1, 0);
            drawSummary(g, s, newer, x, y, w);
        }
        if (tip != null) {
            Glossary.draw(g, font, tip, mx, my, width);
        }
    }

    private List<Component> tip;

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0);
    }

    /** What pressing Update does, as three short steps, or why no download fits. */
    private void drawSummary(GuiGraphics g, UpdateCheck.State s, boolean newer, int x, int y, int w) {
        if (!newer || s.asset() == null) {
            String text = !newer ? L.t("wynnlabs.update.nothing_new") : L.t("wynnlabs.update.no_fit", loaderLabel());
            Type.BODY.drawWrapped(g, font, text, x, y, w, 4, Theme.INK_2);
            return;
        }
        String[] steps = {L.t("wynnlabs.update.step1", loaderLabel(), mb(s.asset().size())), L.t("wynnlabs.update.step2"),
            L.t("wynnlabs.update.step3")};
        int ly = y;
        for (int i = 0; i < steps.length; i++) {
            int dot = 11;
            int dy = Type.BODY.onCaps(ly, dot);
            Kit.pill(g, x, dy, dot, dot, false, false);
            String n = String.valueOf(i + 1);
            Type.META.draw(g, font, n, x + (dot - Type.META.width(font, n) + 1) / 2, Type.META.yIn(dy, dot), Theme.INK_2);
            ly = Type.BODY.drawWrapped(g, font, steps[i], x + dot + Theme.SP_3, ly, w - dot - Theme.SP_3, 2, Theme.INK_2) + Theme.SP_2;
        }
        Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.update.keeps"), w), x, ly + Theme.SP_1, Theme.INK_3);
        if (UpdatePrefs.ignoredVersion(s.latest())) {
            Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.update.ignored_note"), w), x,
                    ly + Theme.SP_1 + Type.META.lineHeight + Theme.SP_1, Theme.INK_3);
        }
    }

    /** The release notes: sections as LABEL, items as bullets, scrolled by the wheel and the bar. */
    private void drawNotes(GuiGraphics g, UpdateCheck.State s, int x, int y, int w, int h, int mx, int my) {
        if (notes == null || !s.latest().equals(notesFor)) {
            notes = ReleaseNotes.lines(s.notes());
            notesFor = s.latest();
            scroll = 0;
            noteLines = null;
        }
        int tw = w - ScrollBar.W - Theme.SP_3;
        if (noteLines == null || noteW != tw) {
            noteLines = new ArrayList<>();
            for (ReleaseNotes.Line l : notes) {
                List<String> parts = new ArrayList<>();
                if (l.heading()) {
                    parts.add(l.text());
                } else {
                    for (var seq : font.getSplitter().splitLines(Type.BODY.of(l.text()), tw - (l.bullet() ? 10 : 0),
                            net.minecraft.network.chat.Style.EMPTY)) {
                        parts.add(seq.getString());
                    }
                }
                for (int i = 0; i < parts.size(); i++) {
                    noteLines.add(new Object[] {parts.get(i), l.heading(), l.bullet() && i == 0, l.bullet()});
                }
            }
            noteW = tw;
        }
        if (noteLines.isEmpty()) {
            Type.MUTED.draw(g, font, L.t("wynnlabs.update.no_notes"), x, y, Theme.INK_3);
            return;
        }
        int lineH = Type.BODY.lineHeight + Theme.SP_1;
        int visible = Math.max(1, h / lineH);
        scroll = Math.max(0, Math.min(scroll, noteLines.size() - visible));
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < visible && scroll + i < noteLines.size(); i++) {
            Object[] l = noteLines.get(scroll + i);
            int ly = y + i * lineH;
            if ((Boolean) l[1]) {
                Type.LABEL.draw(g, font, (String) l[0], x, ly, Theme.INK);
            } else {
                if ((Boolean) l[2]) {
                    Art.icon(g, "circle-dot", x, Type.BODY.onCaps(ly, 6), 6, Theme.INK_3);
                }
                Type.BODY.draw(g, font, (String) l[0], x + ((Boolean) l[3] ? 10 : 0), ly, Theme.INK_2);
            }
        }
        g.disableScissor();
        notesBar.place(x + w - ScrollBar.W, y, h, visible, noteLines.size(), scroll);
        notesBar.draw(g, mx, my);
    }

    private List<Object[]> noteLines;
    private int noteW;

    /** The release notes' bar (offsets in lines). */
    private final ScrollBar notesBar = new ScrollBar();

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (notesBar.mouseDragged(MenuScale.event(event).y())) {
            scroll = notesBar.offset();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        notesBar.mouseReleased();
        return super.mouseReleased(event);
    }

    /** The download's progress, the ready note or why it failed, ending at {@code bottom}; returns its height. */
    private int drawStatus(GuiGraphics g, UpdateCheck.State s, int x, int bottom, int w) {
        if (s.latest() == null || !s.latest().equals(UpdateDownload.tag())) {
            return 0;
        }
        int y = bottom - Type.BODY.lineHeight;
        switch (UpdateDownload.phase()) {
            case DOWNLOADING -> {
                double f = UpdateDownload.progress();
                String t = L.t("wynnlabs.box.pct", (int) Math.round(f * 100))
                        + (s.asset() == null ? "" : " · " + mb((long) (s.asset().size() * f)) + " / " + mb(s.asset().size()) + " MB");
                int pw = Type.META.width(font, t) + Theme.SP_3;
                AboutUpdateCard.bar(g, x, Type.BODY.onCaps(y, 6), w - pw, f);
                Type.META.drawRight(g, font, t, x + w, y + 1, Theme.INK_3);
            }
            case READY -> {
                Art.icon(g, "check", x, Type.BODY.onCaps(y, 8), 8, Theme.GOOD);
                Type.BODY.draw(g, font, Type.BODY.fit(font, L.t("wynnlabs.update.ready", s.latest()), w - 11), x + 11, y, Theme.GOOD);
            }
            case FAILED -> {
                Art.icon(g, "alert-triangle", x, Type.BODY.onCaps(y, 8), 8, Theme.BAD);
                Type.BODY.draw(g, font, Type.BODY.fit(font, UpdateCheck.failText(UpdateDownload.failure()), w - 11), x + 11, y, Theme.BAD);
            }
            default -> {
                return 0;
            }
        }
        return Type.BODY.lineHeight;
    }

    /** Geç and Atla on the left; on the right Update (gold) and Update &amp; exit, or Quit now once ready. */
    private void drawFoot(GuiGraphics g, UpdateCheck.State s, boolean newer, int x, int y, int w, int mx, int my) {
        int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        int lx = x;
        if (newer) {
            String later = L.t("wynnlabs.update.ignore_now");
            int lw = Ui.iconChipWidth(font, null, later, false);
            Ui.iconChip(g, font, null, later, false, lx, cy, lw, false, mx, my);
            hits.add(new Hit(lx, cy, lw, Ui.CHIP_H, () -> {
                UpdatePrefs.ignoreNow();
                onClose();
            }));
            lx += lw + Theme.SP_2;
            if (!UpdatePrefs.ignoredVersion(s.latest())) {
                String skip = L.t("wynnlabs.update.ignore_version");
                int sw = Ui.iconChipWidth(font, null, skip, false);
                Ui.iconChip(g, font, null, skip, false, lx, cy, sw, false, mx, my);
                hits.add(new Hit(lx, cy, sw, Ui.CHIP_H, () -> {
                    UpdatePrefs.ignore(s.latest());
                    onClose();
                }));
            }
        }
        boolean busy = UpdateDownload.phase() == UpdateDownload.Phase.DOWNLOADING;
        boolean ready = UpdateDownload.phase() == UpdateDownload.Phase.READY && s.latest() != null && s.latest().equals(UpdateDownload.tag());
        int rx = x + w;
        if (ready) {
            rx -= gold(g, L.t("wynnlabs.about.quit_now"), "player-play", rx, y, mx, my, () -> minecraft.stop());
        } else if (busy) {
            Type.META.drawRight(g, font, L.t("wynnlabs.update.downloading_short"), rx, Type.META.yIn(y, Ui.BUTTON_H), Theme.INK_3);
        } else if (newer && s.asset() != null) {
            rx -= gold(g, L.t("wynnlabs.update.update"), "arrow-down", rx, y, mx, my,
                    () -> UpdateDownload.start(s.asset(), s.latest(), false)) + Theme.SP_2;
            if (!exitWhenReady) {
                String t = L.t("wynnlabs.update.update_exit");
                int tw = Ui.iconChipWidth(font, null, t, false);
                if (rx - tw > lx + Theme.SP_4) {
                    Ui.iconChip(g, font, null, t, false, rx - tw, cy, tw, false, mx, my);
                    hits.add(new Hit(rx - tw, cy, tw, Ui.CHIP_H, () -> {
                        exitWhenReady = true;
                        UpdateDownload.start(s.asset(), s.latest(), false);
                    }));
                }
            }
        } else if (newer) {
            gold(g, L.t("wynnlabs.about.open_github"), "arrow-up-right", rx, y, mx, my, () -> openPage(s.url()));
        }
    }

    /** The one gold button, its right edge at {@code right}; returns its width. */
    private int gold(GuiGraphics g, String label, String icon, int right, int y, int mx, int my, Runnable action) {
        int bw = Ui.iconButtonLabelWidth(font, label);
        Ui.iconButtonLabel(g, font, icon, label, right - bw, y, bw, mx, my);
        hits.add(new Hit(right - bw, y, bw, Ui.BUTTON_H, action));
        return bw;
    }

    /** A GitHub page in the browser, after the game's "open this link?" prompt. */
    private void openPage(String url) {
        if (url != null && url.startsWith("https://github.com/")) {
            net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, java.net.URI.create(url), true);
        }
    }

    static String vName(String v) {
        if (v == null || v.isBlank()) {
            return "?";
        }
        return Character.isDigit(v.charAt(0)) ? "v" + v : v;
    }

    static String loaderLabel() {
        try {
            String l = Platform.get().loaderName();
            return l.equals("neoforge") ? "NeoForge" : l.equals("fabric") ? "Fabric" : l;
        } catch (Throwable e) {
            return "?";
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        event = MenuScale.event(event);
        double ex = event.x();
        double ey = event.y();
        if (changelog && notesBar.mouseClicked(ex, ey)) {
            scroll = notesBar.offset();
            return true;
        }
        for (Hit h : List.copyOf(hits)) {
            if (ex >= h.x() && ex < h.x() + h.w() && ey >= h.y() && ey < h.y() + h.h()) {
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        if (changelog) {
            scroll -= (int) Math.signum(v) * 3;
        }
        return true;
    }
}
