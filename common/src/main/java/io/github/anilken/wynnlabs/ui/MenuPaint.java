package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The menu's shared drawing kit: ink on paper, cards, section titles, chips, step buttons and
 * the hover and click areas every tab adds to the screen's {@link RoadmapScreen#hits}.
 */
class MenuPaint {

    /** The menu screen this draws on. */
    final RoadmapScreen menu;

    MenuPaint(RoadmapScreen menu) {
        this.menu = menu;
    }

    /** The screen's font (it changes with the webFont setting). */
    net.minecraft.client.gui.Font font() {
        return menu.font();
    }

    // palette: the J menu theme (Theme), under the names the screen has always used
    static final int CARD = Theme.CARD;
    static final int CARD_HOVER = Theme.CARD_HOVER;
    static final int LINE = Theme.LINE;
    static final int ACCENT = Theme.GOLD_DEEP;
    static final int ACCENT_DARK = Theme.WOOD;
    static final int GOOD = Theme.GOOD;
    static final int MUTED = Theme.INK_2;
    static final int FAINT = Theme.INK_3;
    static final int GOLD = Theme.PRICE;
    // tags on paper: a light wash of the colour with dark text in it
    static final int TAG_RED = 0xFFF2C9BE;
    static final int TAG_AMBER = 0xFFF0D090;
    static final int TAG_PURPLE = 0xFFDCC8EC;
    static final int TAG_GREEN = 0xFFCFE0B0;

    // ---- drawing kit ----

    /** A small section title: a brass diamond, the title, a rule running to the right edge. */
    void section(GuiGraphics g, String title, int x, int y, int w) {
        Kit.section(g, font(), title, x, y, w);
    }

    // ---- text on paper: dark ink, no shadow, chat colours in their paper tone ----

    void ink(GuiGraphics g, String s, int x, int y, int colour) {
        Kit.text(g, font(), s, x, y, Theme.paperRgb(colour));
    }

    void ink(GuiGraphics g, String s, int x, int y, int colour, boolean shadow) {
        ink(g, s, x, y, colour);
    }

    void ink(GuiGraphics g, Component c, int x, int y, int colour) {
        Kit.text(g, font(), c, x, y, Theme.paperRgb(colour));
    }

    void ink(GuiGraphics g, Component c, int x, int y, int colour, boolean shadow) {
        ink(g, c, x, y, colour);
    }

    void ink(GuiGraphics g, FormattedCharSequence s, int x, int y, int colour) {
        g.drawString(font(), Kit.ink(s), x, y, Theme.paperRgb(colour), false);
    }

    void ink(GuiGraphics g, FormattedCharSequence s, int x, int y, int colour, boolean shadow) {
        ink(g, s, x, y, colour);
    }

    void bigItem(GuiGraphics g, ItemStack stack, int x, int y) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(2f, 2f);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    String fit(String s, int w) {
        if (font().width(s) <= w) {
            return s;
        }
        return font().plainSubstrByWidth(s, Math.max(0, w - font().width("…"))) + "…";
    }

    void badge(GuiGraphics g, int tab, int x, int y, int size) {
        badge(g, tab, x, y, size, 0);
    }

    /** A tab's icon: the rail's, a section chip's. */
    static String iconOf(int tab) {
        return switch (tab) {
            case 0 -> "home";
            case 1 -> "sun";
            case 2 -> "map-2";
            case 3 -> "skull";
            case RoadmapScreen.TAB_BANK -> "building-bank";
            case RoadmapScreen.TAB_AI -> "sparkles";
            case RoadmapScreen.TAB_DAMAGE -> "sword";
            case RoadmapScreen.TAB_TREE -> "hierarchy";
            case RoadmapScreen.TAB_CRAFT -> "hammer";
            case RoadmapScreen.TAB_ENDGAME -> "trophy";
            case RoadmapScreen.TAB_SEARCH -> "search";
            default -> "settings";
        };
    }

    /** {@code ink} not 0: no tile, the icon alone straight on paper in that one ink colour. */
    void badge(GuiGraphics g, int tab, int x, int y, int size, int ink) {
        String icon = iconOf(tab);
        int bg;
        int fg;
        switch (tab) {
            case 0 -> { bg = 0xFF26233A; fg = 0xFFAFA9EC; }
            case 1 -> { bg = 0xFF3A2A12; fg = 0xFFFAC775; }
            case 2 -> { bg = 0xFF12273A; fg = 0xFF85B7EB; }
            case 3 -> { bg = 0xFF3A1616; fg = 0xFFF09595; }
            case RoadmapScreen.TAB_BANK -> { bg = 0xFF0F2A22; fg = 0xFF5DCAA5; }
            case RoadmapScreen.TAB_AI -> { bg = 0xFF12303A; fg = 0xFF7FD4E8; }
            case RoadmapScreen.TAB_DAMAGE -> { bg = 0xFF3A2414; fg = 0xFFF0B27A; }
            case RoadmapScreen.TAB_TREE -> { bg = 0xFF1E3220; fg = 0xFF9FDC8A; }
            case RoadmapScreen.TAB_CRAFT -> { bg = 0xFF3A2E14; fg = 0xFFE8C07A; }
            case RoadmapScreen.TAB_ENDGAME -> { bg = 0xFF40280C; fg = 0xFFFFD36B; }
            case RoadmapScreen.TAB_SEARCH -> { bg = 0xFF2E2414; fg = 0xFFF2D59A; }
            default -> { bg = 0xFF24242E; fg = 0xFFB4B2A9; }
        }
        if (ink != 0) {
            Art.icon(g, icon, x, y, size, ink);
            return;
        }
        Art.iconTile(g, icon, x, y, size, bg, fg);
    }

    /** A button under an empty card's title, and a faint centred note under it ({@code note} may be null). */
    record EmptyButton(String label, int state, List<Component> tip, String action, String note) {}

    /** An edit box for paper: no frame of its own (Kit.field draws one), dark ink without a shadow. */
    static void paperField(net.minecraft.client.gui.components.EditBox box) {
        box.setBordered(false);
        box.setTextColor(Theme.INK);
        box.setTextShadow(false);
    }

    static String ago(long t) {
        long min = (System.currentTimeMillis() - t) / 60000;
        if (min < 1) {
            return L.t("wynnlabs.screen.ago_now");
        }
        if (min < 60) {
            return L.t("wynnlabs.screen.ago_min", min);
        }
        if (min < 24 * 60) {
            return L.t("wynnlabs.screen.ago_hours", min / 60);
        }
        return L.n("wynnlabs.screen.ago_days", min / 1440);
    }

    /** The gold dot of a setting changed from its default. */
    static void dot(GuiGraphics g, int x, int y) {
        Art.box(g, x, y, 6, 6, 3, Theme.GOLD_DEEP, Theme.GOLD_EDGE);
    }

    /** A row's one-line description in META, cut to fit. */
    void desc(GuiGraphics g, String text, int x, int y, int w) {
        Type.META.draw(g, font(), Type.META.fit(font(), text, w), x, y, Theme.INK_2);
    }

    /** A setting's full title and description as a tooltip, for the text the row had to cut. */
    void rowTip(int x, int y, int w, int h, String title, String desc) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
        for (String l : Advice.wrap(desc, 44)) {
            tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
        }
        menu.hits.add(new MenuHit(x, y, w, h, null, tip, null));
    }

    /** {@code v} with {@code digits} decimals and the language's separator ("4.5", "4,5"). */
    static String dec(double v, int digits) {
        String s = String.format(Locale.ROOT, "%." + digits + "f", v);
        int dot = s.indexOf('.');
        return dot < 0 ? s : L.t("wynnlabs.damage.decimal", s.substring(0, dot), s.substring(dot + 1));
    }

    static boolean in(double x, double y, int[] r) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    static String nice(String upper) {
        if (upper == null || upper.isEmpty()) {
            return "";
        }
        String n = upper.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
