package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.coach.Today;
import io.github.anilken.wynnlabs.core.L;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The other characters on the Today card as small chips: the class's icon, the level and a ring of
 * today's dailies (green done, blue partly, empty not started), those played this week and not done
 * first, the ones not played for a week faded at the end. Two lines at most; a "+N" chip opens all
 * of them in the shared dropdown ({@link UiMenu}), which owns the mouse while open.
 */
final class TodayOthers extends MenuPaint implements MenuTab.Popup {

    /** A week: characters not played since fade and go last. */
    static final long OLD = 7L * 24 * 3_600_000L;
    private static final int CHIP_H = Ui.CHIP_H;
    private static final int RING = 9;

    private final UiMenu.State state = new UiMenu.State();
    private List<Today.Day> all = List.of();
    private int[] plus;
    private boolean open;

    TodayOthers(RoadmapScreen menu) {
        super(menu);
    }

    // ---- the order ----

    /** Worth doing today first: played this week with dailies left, then the finished ones, then the old ones; each by last played. */
    static List<Today.Day> order(List<Today.Day> others, long now, String today) {
        List<Today.Day> out = new ArrayList<>(others);
        out.sort(Comparator.<Today.Day>comparingInt(d -> rank(d, now, today)).thenComparing(Comparator.comparingLong(Today.Day::seen).reversed()));
        return out;
    }

    private static int rank(Today.Day d, long now, String today) {
        if (now - d.seen() > OLD) {
            return 2;
        }
        return done(d, today) >= total(d, today) && total(d, today) > 0 ? 1 : 0;
    }

    static int done(Today.Day d, String today) {
        if (!today.equals(d.date())) {
            return 0;
        }
        return (int) d.objectives().stream().filter(o -> o.max() > 0 && o.score() >= o.max()).count();
    }

    static int total(Today.Day d, String today) {
        return today.equals(d.date()) ? d.objectives().size() : 0;
    }

    /** The class's icon from the kit (the same for its reskin). */
    static String classIcon(String cls) {
        String c = cls == null ? "" : cls.toUpperCase(Locale.ROOT);
        return switch (c) {
            case "MAGE", "DARKWIZARD" -> "wand";
            case "ARCHER", "HUNTER" -> "bow";
            case "WARRIOR", "KNIGHT" -> "sword";
            case "ASSASSIN", "NINJA" -> "flame";
            case "SHAMAN", "SKYSEER" -> "trident";
            default -> "user";
        };
    }

    // ---- the chips ----

    private int chipW(Today.Day d) {
        return Theme.SP_3 + 8 + Theme.SP_1 + Type.META.width(font(), String.valueOf(d.level())) + Theme.SP_2 + RING + Theme.SP_3;
    }

    /** The height two lines of chips take. */
    static int height(int lines) {
        return lines * CHIP_H + (lines - 1) * Theme.SP_2;
    }

    /** The chips from x, y across w, at most {@code lines} lines; returns the height used. */
    int draw(GuiGraphics g, int x, int y, int w, int lines, List<Today.Day> others, int mx, int my) {
        long now = System.currentTimeMillis();
        String today = Today.utcDate();
        all = order(others, now, today);
        plus = null;
        int cx = x;
        int cy = y;
        int line = 0;
        for (int i = 0; i < all.size(); i++) {
            Today.Day d = all.get(i);
            int cw = chipW(d);
            String more = "+" + (all.size() - i);
            int pw = Type.META.width(font(), more) + 2 * Theme.SP_3;
            boolean lastLine = line == lines - 1;
            int need = cw + (lastLine && i < all.size() - 1 ? Theme.SP_2 + pw : 0);
            if (cx > x && cx + need > x + w) {
                if (lastLine) {
                    drawPlus(g, cx, cy, more, pw, mx, my);
                    return cy + CHIP_H - y;
                }
                line++;
                cx = x;
                cy += CHIP_H + Theme.SP_2;
            }
            chip(g, cx, cy, cw, d, now, today, mx, my);
            cx += cw + Theme.SP_2;
        }
        return cy + CHIP_H - y;
    }

    private void drawPlus(GuiGraphics g, int x, int y, String more, int w, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + CHIP_H;
        Kit.pill(g, x, y, w, CHIP_H, open, hover);
        Type.META.draw(g, font(), more, x + Theme.SP_3, Type.META.yIn(y, CHIP_H), open ? Theme.INK : Theme.INK_2);
        plus = new int[] {x, y, w, CHIP_H};
        menu.hits.add(new MenuHit(x, y, w, CHIP_H, null, List.of(L.c("wynnlabs.today.others_all", all.size())), "#qtodayothers"));
    }

    private void chip(GuiGraphics g, int x, int y, int w, Today.Day d, long now, String today, int mx, int my) {
        boolean old = now - d.seen() > OLD;
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + CHIP_H;
        Kit.pill(g, x, y, w, CHIP_H, false, hover);
        int ink = old ? Theme.INK_3 : Theme.INK;
        Art.icon(g, classIcon(d.cls()), x + Theme.SP_3, y + (CHIP_H - 8) / 2, 8, Theme.INK_3);
        Type.META.draw(g, font(), String.valueOf(d.level()), x + Theme.SP_3 + 8 + Theme.SP_1, Type.META.yIn(y, CHIP_H), ink);
        ring(g, x + w - Theme.SP_3 - RING, y + (CHIP_H - RING) / 2, done(d, today), total(d, today), old);
        menu.hits.add(new MenuHit(x, y, w, CHIP_H, null, tip(d, today), null));
    }

    /** A 9 px ring drawn finely (a quarter pixel a step): the share of the dailies done around it. */
    private static void ring(GuiGraphics g, int x, int y, int done, int total, boolean old) {
        int track = old ? 0x66CDB98A : 0xFFCDB98A;
        int fill = done >= total && total > 0 ? Theme.GREEN : Theme.TEAL;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.25f, 0.25f);
        float c = RING * 2f;
        float r = RING * 2f - 3;
        int steps = 72;
        for (int k = 0; k < steps; k++) {
            double a = -Math.PI / 2 + 2 * Math.PI * k / steps;
            int px = Math.round((float) (c + r * Math.cos(a)));
            int py = Math.round((float) (c + r * Math.sin(a)));
            boolean on = total > 0 && k < steps * done / total;
            g.fill(px - 2, py - 2, px + 2, py + 2, on ? fill : track);
        }
        g.pose().popMatrix();
    }

    /** A chip's tooltip: the class, level, when played, and today's objectives one by one. */
    private static List<Component> tip(Today.Day d, String today) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(nice(d.cls()) + " · Lv " + d.level()).withStyle(ChatFormatting.AQUA));
        tip.add(Component.literal(ago(d.seen())).withStyle(ChatFormatting.GRAY));
        if (!today.equals(d.date()) || d.objectives().isEmpty()) {
            tip.add(L.c("wynnlabs.today.others_not_read").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (Today.Objective o : d.objectives()) {
                boolean ok = o.max() > 0 && o.score() >= o.max();
                tip.add(Component.literal((ok ? "✔ " : "• ") + o.goal() + (o.max() > 0 ? " " + o.score() + "/" + o.max() : ""))
                        .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        return tip;
    }

    // ---- the popup: every character ----

    /** "+N" clicked: the list opens (again: it closes). */
    void toggle() {
        open = !open;
        state.reset();
    }

    @Override
    public boolean open() {
        return open && plus != null;
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
        long now = System.currentTimeMillis();
        String today = Today.utcDate();
        UiMenu.Items items = new UiMenu.Items();
        boolean faded = false;
        for (Today.Day d : all) {
            boolean old = now - d.seen() > OLD;
            if (old && !faded) {
                items.divide();
                faded = true;
            }
            int t = total(d, today);
            UiMenu.Item it = UiMenu.Item.of(d.character(), classIcon(d.cls()), nice(d.cls()), t > 0 ? done(d, today) + "/" + t : "—", false)
                    .sub("Lv " + d.level() + " · " + ago(d.seen())).tip(tip(d, today));
            items.add(old ? it.asDim() : it);
        }
        boxes.add(UiMenu.draw(g, font(), UiMenu.Spec.under(plus, menu.width, menu.height).rows(8).width(180), state, items.list, mx, my,
                r -> menu.hits.add(new MenuHit(r.x(), r.y(), r.w(), r.h(), null, r.item().tip(), null))));
    }

    @Override
    public boolean clicked(MouseButtonEvent event) {
        return state.press(event.x(), event.y()) || true;
    }

    @Override
    public void scrolled(double x, double y, double v) {
        state.wheel(v);
    }

    @Override
    public boolean key(int key) {
        state.key(key);
        return true;
    }

    /** The list's scroll bar held and dragged. */
    boolean drag(double y) {
        return open && state.drag(y);
    }

    void release() {
        state.release();
    }
}
