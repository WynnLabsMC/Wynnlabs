package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.GuideImages;
import io.github.anilken.wynnlabs.quests.GuideLayout;
import io.github.anilken.wynnlabs.quests.QuestGuides;
import io.github.anilken.wynnlabs.quests.QuestWiki;
import io.github.anilken.wynnlabs.quests.Waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Görev çözümü: the quest's walkthrough from the Wynncraft wiki (wynncraft.wiki.gg). A shipped
 * guide reads stage by stage: the stage list on the left ({@link GuideRail}), the stage shown on
 * the right ({@link GuidePane}) with its task, options, steps and pictures, and every place one
 * click from the compass. A quest without a shipped guide falls back to the page's lines
 * ({@link GuideWikiList}). Fetched once in the background (the wiki, not the game server).
 */
final class GuideScreen extends Screen {

    /** The option picked per quest ("Brie", "Azure Outfit"), so later stages open on it too. */
    private static final Map<String, String> PICKED = new ConcurrentHashMap<>();

    final String quest;
    final String task;
    /** A shipped guide: its facts, stages (and the English ones), each laid out, the list labels. */
    volatile QuestWiki.Info info;
    volatile List<QuestWiki.Stage> stages;
    volatile List<GuideLayout.View> views;
    List<String> labels = List.of();
    /** The stages' pictures by their heading ("Stage 5"), and the one shown large. */
    volatile Map<String, List<GuideImages.Image>> images = Map.of();
    GuideImages.Image zoom;
    /** The stage the game says you are on (-1: unknown), the one shown, its part and option. */
    int current = -1;
    int sel;
    int part;
    int option;
    /** The steps' scroll (px), the stage list's (rows); the keys moved the stage: the list follows. */
    int scroll;
    int listScroll;
    /** The stage list's and the rows' scroll bars: dragged, clicked or wheeled. */
    final ScrollBar railBar = new ScrollBar();
    final ScrollBar paneBar = new ScrollBar();
    boolean follow = true;
    /** Where the stage list ends: the wheel scrolls the list left of it and the stage right of it. */
    int split;
    private final List<Object[]> taps = new ArrayList<>();      // {rect, Runnable}
    private String flash = "";
    private long flashUntil;
    private int[] closeRect;
    private final GuideWikiList wiki;

    GuideScreen(String quest, String task) {
        super(Component.literal(quest));
        this.quest = quest;
        this.task = task == null ? "" : task;
        this.wiki = new GuideWikiList(this);
        CompletableFuture.runAsync(this::fetch);
    }

    @Override
    protected void init() {
        UiFont.apply(this);
        super.init();
    }

    private void fetch() {
        // a shipped guide (offline too), in the player's language: stage by stage
        QuestWiki.Info inf = QuestGuides.find(quest) == null ? null : QuestWiki.get(quest);
        if (inf != null && !inf.stages().isEmpty()) {
            List<QuestWiki.Stage> english = QuestWiki.englishStages(inf);
            StringBuilder text = new StringBuilder();
            for (QuestWiki.Stage st : inf.stages()) {
                text.append(st.task()).append(' ').append(String.join(" ", st.hints())).append(' ');
            }
            String all = text.toString().toLowerCase(Locale.ROOT);
            List<GuideLayout.View> v = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < inf.stages().size(); i++) {
                QuestWiki.Stage en = i < english.size() ? english.get(i) : null;
                v.add(GuideLayout.of(inf.stages().get(i), en, all));
                names.add(QuestWiki.listLabel(inf.stages().get(i), en));
            }
            int cur = QuestWiki.currentStage(inf, task);
            info = inf;
            labels = names;
            stages = inf.stages();
            current = cur;
            views = v;
            pick(Math.max(0, cur));
            if (QuestWiki.on()) {
                try {
                    String page = QuestWiki.page(quest);
                    if (page != null) {
                        images = GuideImages.byStage(quest, page);
                    }
                } catch (Exception e) {
                    // the guide reads without its pictures
                }
            }
            return;
        }
        wiki.fetch();
    }

    // ---- what is shown ----

    /** Shows stage {@code stage}, on the part and option picked earlier in the quest when it has them. */
    void pick(int stage) {
        sel = Math.max(0, Math.min(stage, stages.size() - 1));
        part = 0;
        option = 0;
        scroll = 0;
        String picked = PICKED.get(quest);
        if (picked == null) {
            return;
        }
        GuideLayout.View v = views.get(sel);
        for (int i = 0; i < v.parts().size(); i++) {
            String used = v.parts().get(i).usedFor();
            if (used != null && List.of(used.split(",\\s*")).contains(picked)) {
                part = i;
                break;
            }
        }
        List<GuideLayout.Option> opts = options();
        for (int k = 0; k < opts.size(); k++) {
            if (opts.get(k).labels().contains(picked)) {
                option = k;
            }
        }
    }

    /** The options of what is shown: the stage's, or its part's. */
    List<GuideLayout.Option> options() {
        GuideLayout.View v = views.get(sel);
        return v.parts().isEmpty() ? v.options() : v.parts().get(Math.min(part, v.parts().size() - 1)).options();
    }

    /** The option named {@code label} picked, and remembered for the rest of the quest. */
    void pickOption(String label) {
        List<GuideLayout.Option> opts = options();
        for (int k = 0; k < opts.size(); k++) {
            if (opts.get(k).labels().contains(label)) {
                option = k;
            }
        }
        PICKED.put(quest, label);
        scroll = 0;
    }

    /** The label the option chips show as picked. */
    String pickedLabel() {
        List<GuideLayout.Option> opts = options();
        if (opts.isEmpty()) {
            return null;
        }
        List<String> group = opts.get(Math.min(option, opts.size() - 1)).labels();
        String p = PICKED.get(quest);
        return p != null && group.contains(p) ? p : group.get(0);
    }

    String picked() {
        return PICKED.get(quest);
    }

    void pickPart(int i) {
        part = i;
        option = 0;
        scroll = 0;
        String p = PICKED.get(quest);
        List<GuideLayout.Option> opts = options();
        for (int k = 0; k < opts.size() && p != null; k++) {
            if (opts.get(k).labels().contains(p)) {
                option = k;
            }
        }
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // at the menu scale (MenuScale): a logical size, a scaled pose, logical mouse positions
        MenuScale.render(this, g, mx, my, (x, y) -> draw(g, x, y, pt));
    }

    private void draw(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        taps.clear();
        boolean large = MenuLayout.of(width, height).large();
        int w = Math.min(width - 20, large ? 720 : 460);
        int h = Math.min(height - 20, large ? 480 : 320);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        // the menu's window over the dimmed game: the wooden frame, the parchment page, the close seal
        KitFrame.dialog(g, width, height, x, y, w, h);
        int left = x + Theme.SP_6;
        int right = x + w - Theme.SP_6;
        // header: the title and how far along you are at the right end
        int ty = y + Theme.SP_7;                                   // the title clear of the parchment's corner ornaments
        Type.TITLE.draw(g, font, Type.TITLE.fit(font, QuestGuides.title(quest), w / 2), left, ty);
        List<QuestWiki.Stage> st = stages;
        if (st != null && current >= 0) {
            int bw = 48;
            HdBar.draw(g, right - bw, ty + 6, bw, 5, current / (double) st.size(), HdBar.TEAL);
            String p = large ? L.t("wynnlabs.guide.progress", current, st.size()) : current + " / " + st.size();
            Type.META.drawRight(g, font, p, right - bw - Theme.SP_3, ty + 4, Type.META.colour);
        }
        int rule = ty + Type.TITLE.lineHeight + Theme.SP_4;
        Kit.hair(g, left, rule, right - left, 1, Theme.LINE);
        int top = rule + 1 + Theme.SP_5;
        int foot = y + h - Theme.SP_5 - Type.META.lineHeight;     // clear of the parchment's rim
        int bottom = foot - Theme.SP_5;
        footer(g, left, right, foot, st != null);
        if (views != null) {
            int lw = large ? 200 : 132;
            split = left + lw + Theme.SP_5;
            GuideRail.draw(this, g, mx, my, left, top, lw, bottom);
            Kit.hair(g, split, top, 1, bottom - top, Theme.LINE);
            GuidePane.draw(this, g, mx, my, split + Theme.SP_5, top, right - split - Theme.SP_5, bottom, large);
        } else {
            wiki.draw(g, mx, my, left, top, right, bottom);
        }
        int[] cr = KitFrame.closeAt(width, x, y, w);
        closeRect = KitFrame.closeOutside(g, width, x, y, w, zoom == null && in(mx, my, cr));
        if (views != null && zoom != null) {
            drawZoom(g);
        }
    }

    /** Key hints (drawn arrows, not the pixel font's), or a moment's message; the source on the right. */
    private void footer(GuiGraphics g, int left, int right, int y, boolean guide) {
        Type.META.drawRight(g, font, "wynncraft.wiki.gg", right, y, Type.META.colour);
        if (System.currentTimeMillis() < flashUntil) {
            Type.META.draw(g, font, flash, left, y, Theme.GOOD);
            return;
        }
        if (!guide) {
            Type.META.draw(g, font, L.t(wiki.current() ? "wynnlabs.guide.footer_current" : "wynnlabs.guide.footer"), left, y);
            return;
        }
        int ink = Type.META.colour;
        int cx = left;
        Kit.icon(g, "icon_up", cx, Type.META.onCaps(y, 7), 5, 7, ink);
        Kit.icon(g, "icon_down", cx + 6, Type.META.onCaps(y, 7), 5, 7, ink);
        cx += 11 + Theme.SP_2;
        String stage = L.t("wynnlabs.guide.key_stage");
        Type.META.draw(g, font, stage, cx, y);
        cx += Type.META.width(font, stage) + Theme.SP_5;
        boolean parts = views != null && !views.get(sel).parts().isEmpty();
        if (parts || options().size() > 1) {
            Kit.icon(g, "icon_left", cx, Type.META.onCaps(y, 5), 7, 5, ink);
            Kit.icon(g, "icon_right", cx + 8, Type.META.onCaps(y, 5), 7, 5, ink);
            cx += 15 + Theme.SP_2;
            String k = L.t(parts ? "wynnlabs.guide.key_part" : "wynnlabs.guide.key_option");
            Type.META.draw(g, font, k, cx, y);
            cx += Type.META.width(font, k) + Theme.SP_5;
        }
        String k = L.t("wynnlabs.guide.key_scroll");
        Type.META.draw(g, font, k, cx, y);
        cx += Type.META.width(font, k) + Theme.SP_5;
        Type.META.draw(g, font, L.t("wynnlabs.guide.key_close"), cx, y);
    }

    /** The picture clicked, as large as the screen allows (at most twice its size), with its caption. */
    private void drawZoom(GuiGraphics g) {
        GuideImageCache.Tex t = GuideImageCache.get(zoom.file());
        if (t == null) {
            zoom = null;
            return;
        }
        g.nextStratum();
        g.fill(0, 0, width, height, 0xC8000000);
        double k = Math.min(2.0, Math.min((width - 40) / (double) t.w(), (height - 50) / (double) t.h()));
        int w = (int) (t.w() * k);
        int h = (int) (t.h() * k);
        int x = (width - w) / 2;
        int y = (height - h) / 2 - 8;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, 0, 0, w, h, t.w(), t.h(), t.w(), t.h());
        String close = L.t("wynnlabs.guide.image_close");
        String line = zoom.caption().isEmpty() ? close : zoom.caption() + " · " + close;
        g.drawCenteredString(font, font.plainSubstrByWidth(line, width - 20), width / 2, y + h + 6, 0xFFE8E2D0);
    }

    // ---- clicks and the compass ----

    /** While the steps draw: their visible box; a click area outside it is left out. */
    int[] clip;

    /** A click area for this frame (cut to {@link #clip} while it is set). */
    void tap(int x, int y, int w, int h, Runnable action) {
        if (clip != null) {
            int x1 = Math.max(x, clip[0]);
            int y1 = Math.max(y, clip[1]);
            int x2 = Math.min(x + w, clip[0] + clip[2]);
            int y2 = Math.min(y + h, clip[1] + clip[3]);
            if (x2 <= x1 || y2 <= y1) {
                return;
            }
            x = x1;
            y = y1;
            w = x2 - x1;
            h = y2 - y1;
        }
        taps.add(new Object[] {new int[] {x, y, w, h}, action});
    }

    private static boolean in(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** Points the compass at {@code at}; {@code knowsY} false (the guide gave x and z): at the player's height. */
    void compass(int[] at, boolean knowsY) {
        int y = at[1];
        if (!knowsY) {
            var p = Minecraft.getInstance().player;
            y = p == null ? 64 : p.getBlockY();
        }
        if (Waypoints.go(at[0], y, at[2], QuestGuides.title(quest))) {
            flash = L.t("wynnlabs.guide.showing_way", at[0] + ", " + y + ", " + at[2]);
            flashUntil = System.currentTimeMillis() + 2500;
        }
    }

    void compassAll(List<int[]> points) {
        if (Waypoints.goAll(points, QuestGuides.title(quest)) > 0) {
            flash = L.t("wynnlabs.guide.showing_all", points.size());
            flashUntil = System.currentTimeMillis() + 2500;
        }
    }

    /** How far {@code at} is from the player, "~140 m"; null when unknown. */
    static String distance(int[] at) {
        var p = Minecraft.getInstance().player;
        if (p == null || at == null) {
            return null;
        }
        double d = Math.hypot(p.getX() - at[0], p.getZ() - at[2]);
        return "~" + (d >= 1000 ? String.format(Locale.ROOT, "%.1f km", d / 1000) : Math.round(d) + " m");
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        x = MenuScale.at(x);
        int step = 3 * Type.BODY.lineHeight;
        if (views != null && x < split) {
            listScroll = Math.max(0, listScroll - 3 * (int) Math.signum(v));
        } else if (views != null) {
            scroll = Math.max(0, scroll - step * (int) Math.signum(v));
        } else {
            wiki.scroll(-3 * (int) Math.signum(v));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        MouseButtonEvent e = MenuScale.event(event);
        if (railBar.mouseDragged(e.y())) {
            listScroll = railBar.offset();
            return true;
        }
        if (paneBar.mouseDragged(e.y())) {
            scroll = paneBar.offset();
            return true;
        }
        if (views == null && wiki.drag(e.y())) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        railBar.mouseReleased();
        paneBar.mouseReleased();
        wiki.bar.mouseReleased();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        event = MenuScale.event(event);
        int[] cr = closeRect;
        if (cr != null && in(event.x(), event.y(), cr)) {
            onClose();
            return true;
        }
        if (zoom != null) {
            zoom = null;
            return true;
        }
        if (views != null && railBar.mouseClicked(event.x(), event.y())) {
            listScroll = railBar.offset();
            return true;
        }
        if (views != null && paneBar.mouseClicked(event.x(), event.y())) {
            scroll = paneBar.offset();
            return true;
        }
        if (views == null && wiki.press(event.x(), event.y())) {
            return true;
        }
        for (Object[] tap : List.copyOf(taps)) {
            if (in(event.x(), event.y(), (int[]) tap[0])) {
                ((Runnable) tap[1]).run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (zoom != null && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            zoom = null;
            return true;
        }
        if (views != null) {
            GuideLayout.View v = views.get(sel);
            int parts = v.parts().size();
            List<GuideLayout.Option> opts = options();
            int step = event.key() == GLFW.GLFW_KEY_LEFT ? -1 : event.key() == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
            switch (event.key()) {
                case GLFW.GLFW_KEY_UP -> pick(sel - 1);
                case GLFW.GLFW_KEY_DOWN -> pick(sel + 1);
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                    if (parts > 1) {
                        pickPart((part + step + parts) % parts);
                    } else if (opts.size() > 1) {
                        List<GuideLayout.Option> o = opts;
                        int k = (option + step + o.size()) % o.size();
                        pickOption(o.get(k).labels().get(0));
                    }
                }
                default -> {
                    return super.keyPressed(event);
                }
            }
            follow = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
