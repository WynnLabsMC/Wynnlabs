package io.github.anilken.wynnlabs.ui;

import static io.github.anilken.wynnlabs.ui.SettingsRows.SETTING_CATS;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.ui.SettingsRows.SetRow;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A settings page drawn: section titles (LABEL over a hairline; a folding one with its chevron
 * and count), then rows on one grid: the name (BODY) and its control in the column at the right,
 * or chips on their own line when they do not fit beside it. Only the picked row shows what it
 * does and an example. A row whose parent is off is indented, pale and still. Rows keep SP_2 of
 * page between them, so two lit rows never touch. Search results and the changed settings are
 * the same rows under their pages' names.
 */
final class SettingsList extends MenuPaint {

    /** The narrowest control column: a switch, or − value +. */
    private static final int CTRL = 76;
    /** A child row's indent. */
    private static final int INDENT = Theme.SP_5;
    /** Plain page under every row. */
    private static final int GAP = Theme.SP_2;

    private final SettingsTab tab;
    private final SettingsControls controls;

    SettingsList(RoadmapScreen menu, SettingsTab tab) {
        super(menu);
        this.tab = tab;
        controls = new SettingsControls(menu, tab);
    }

    /** A line of the page: a section title ({@code title} set; {@code row} null for a page's name) or a setting. */
    record Item(int cat, int index, SetRow row, String title) {

        boolean head() {
            return title != null;
        }
    }

    /** A page's title (HEADING); returns the height. */
    int pageHead(GuiGraphics g, int x, int y, int w, String title) {
        Type.HEADING.draw(g, font(), Type.HEADING.fit(font(), title, w), x, y);
        return Type.HEADING.lineHeight + Theme.SP_5;
    }

    /** The items of a page: its section titles and the rows of the sections that are open. */
    static List<Item> catItems(int cat, Set<String> open) {
        List<Item> items = new ArrayList<>();
        List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[cat]);
        for (int i = 0; i < rows.size(); i++) {
            SetRow r = rows.get(i);
            if (r.head()) {
                items.add(new Item(cat, i, r, L.t(r.id())));
            } else if (r.fold() == null || open.contains(r.fold())) {
                items.add(new Item(cat, i, r, null));
            }
        }
        return items;
    }

    /**
     * The rows that match the search (or, {@code query} null, that differ from their default),
     * under their pages' names, each setting once: Basics, which repeats the others, comes last.
     */
    static List<Item> found(String query) {
        List<Item> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int k = 1; k <= SETTING_CATS.length; k++) {
            int c = k % SETTING_CATS.length;
            List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[c]);
            boolean named = false;
            for (int i = 0; i < rows.size(); i++) {
                SetRow r = rows.get(i);
                boolean hit = query == null ? !r.head() && SettingsRows.rowChanged(r) : SettingsText.matches(r, query);
                if (!hit || !seen.add(r.key())) {
                    continue;
                }
                if (!named) {
                    items.add(new Item(c, -1, null, L.t(SettingsRows.pageKey(c))));
                    named = true;
                }
                items.add(new Item(c, i, r, null));
            }
        }
        return items;
    }

    /** The rows and titles from {@code y}, scrolled, at most {@code h} tall; the scroll bar at the right when they scroll. */
    void list(GuiGraphics g, List<Item> items, int x, int y, int w, int h, boolean compact, int mx, int my) {
        int iw = w - ScrollBar.W - Theme.SP_2;
        int[] hs = new int[items.size()];
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            hs[i] = height(items.get(i), iw, i == 0, compact);
            total += hs[i];
        }
        tab.settingsScrollMax = Math.max(0, total - h);
        tab.settingsScroll = Math.max(0, Math.min(tab.settingsScrollMax, tab.settingsScroll));
        tab.settingsListRect = new int[] {x - Theme.SP_3, y, w + Theme.SP_3, h};
        int rowMy = my >= y && my < y + h ? my : -10000;           // no hover on a row scrolled out
        int t0 = menu.toggles.size();
        int h0 = menu.hits.size();
        g.enableScissor(x - Theme.SP_3, y, x + w, y + h);
        int ry = y - tab.settingsScroll;
        for (int i = 0; i < items.size(); i++) {
            if (ry + hs[i] > y && ry < y + h) {
                Item it = items.get(i);
                if (it.head()) {
                    head(g, it, x, ry + (i == 0 ? 0 : Theme.SP_4), iw, mx, rowMy);
                } else if (it.row().kind().equals("notice")) {
                    tab.pages.rulesNotice(g, x, ry, iw, mx, rowMy);
                } else {
                    row(g, it, x, ry, iw, hs[i] - GAP, compact, mx, rowMy);
                }
            }
            ry += hs[i];
        }
        g.disableScissor();
        // click areas and tooltips: only the part that shows
        for (int i = menu.toggles.size() - 1; i >= t0; i--) {
            if (menu.toggles.get(i)[0] instanceof int[] r) {
                int y0 = Math.max(r[1], y);
                int y1 = Math.min(r[1] + r[3], y + h);
                if (y1 <= y0) {
                    menu.toggles.remove(i);
                } else {
                    menu.toggles.set(i, new Object[] {new int[] {r[0], y0, r[2], y1 - y0}, menu.toggles.get(i)[1]});
                }
            }
        }
        menu.hits.subList(h0, menu.hits.size()).removeIf(hb -> hb.y() < y || hb.y() + hb.h() > y + h);
        tab.settingsBar.place(x + w - ScrollBar.W, y, h, h, total, tab.settingsScroll);
        tab.settingsBar.draw(g, mx, my);
    }

    private int height(Item it, int w, boolean first, boolean compact) {
        if (it.head()) {
            return (first ? 0 : Theme.SP_4) + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_2;
        }
        SetRow r = it.row();
        if (r.kind().equals("notice")) {
            return tab.pages.rulesNoticeHeight(w) + Theme.SP_3;
        }
        int x0 = indent(r);
        int h = Theme.SP_2 + Type.BODY.lineHeight + Theme.SP_2;
        if (chipsBelow(r, w - x0)) {
            h += Theme.SP_2 + controls.chipsHeight(r, w - x0);
        }
        if (picked(it)) {
            int tw = w - x0;                        // under the name: the row's whole width
            String desc = SettingsText.rowDesc(r);
            if (!desc.isEmpty()) {
                h += Theme.SP_1 + Math.min(compact ? 3 : 2, Type.META.wrap(font(), desc, tw).size()) * Type.META.lineHeight;
            }
            if (!SettingsText.rowExample(r).isEmpty()) {
                h += Theme.SP_1 + Type.META.lineHeight;
            }
        }
        if (r.kind().equals("keynote") && tab.keyOpen) {
            h += Theme.SP_2 + SettingsPages.keyDetailHeight();
        }
        return h + GAP;
    }

    private boolean picked(Item it) {
        return it.cat() == tab.pickCat && it.index() == tab.pickRow;
    }

    /** Whether the row's chips go on their own line: beside the name they would cut it. */
    private boolean chipsBelow(SetRow r, int w) {
        if (!SettingsControls.chips(r)) {
            return false;
        }
        int name = Type.BODY.width(font(), SettingsText.rowTitle(r));
        return w - controls.width(r, false) - Theme.SP_4 < name;
    }

    /**
     * A section title in LABEL over a hairline: a folding one starts with its chevron and ends in
     * how many rows it holds; a page whose settings are this character's has the mark at its right.
     */
    private void head(GuiGraphics g, Item it, int x, int y, int w, int mx, int my) {
        if (it.row() == null) {
            Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), it.title(), w), x, y);
            Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
            return;
        }
        SetRow head = it.row();
        int tx = x;
        int right = x + w;
        String fold = head.fold();
        if (fold != null) {
            boolean open = tab.openFolds.contains(fold);
            int hh = Type.LABEL.lineHeight + Theme.SP_1 + 1;
            boolean hover = mx >= x - Theme.SP_3 && mx < x + w && my >= y - Theme.SP_1 && my < y + hh;
            if (hover) {
                g.fill(x - Theme.SP_3, y - Theme.SP_1, x + w, y + hh - 1, Theme.CARD_HOVER);
            }
            Art.icon(g, open ? "chevron-down" : "chevron-right", x, Type.LABEL.onCaps(y, 8), 8, hover ? Theme.INK : Theme.INK_2);
            tx += 8 + Theme.SP_2;
            String count = String.valueOf(foldSize(it.cat(), it.index()));
            int sw = Type.META.width(font(), count) + 2 * Theme.SP_2;
            Ui.seal(g, font(), count, right - sw, Type.LABEL.onCaps(y, Ui.SEAL_H), Theme.INK_3);
            right -= sw + Theme.SP_2;
            menu.toggles.add(new Object[] {new int[] {x - Theme.SP_3, y - Theme.SP_1, w + Theme.SP_3, hh + Theme.SP_1}, "#fold:" + fold});
        } else if (SettingsRows.perCharacter(head.id())) {
            Art.icon(g, "user", right - 8, Type.LABEL.onCaps(y, 8), 8, Theme.INK_3);
            if (mx >= right - 8 && mx < right && my >= y && my < y + Type.LABEL.lineHeight) {
                rowTip(right - 8, y, 8, Type.LABEL.lineHeight, it.title(), L.t("wynnlabs.rules.per_character"));
            }
            right -= 8 + Theme.SP_2;
        }
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), it.title(), right - tx), tx, y);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
    }

    /** A child row's indent; a row shared by two parents (dimmed only while both are off) stays level with them. */
    private static int indent(SetRow r) {
        return r.parent() == null || r.parent().contains("|") ? 0 : INDENT;
    }

    /** How many rows a folding section holds. */
    private static int foldSize(int cat, int head) {
        List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[cat]);
        int n = 0;
        for (int j = head + 1; j < rows.size() && !rows.get(j).head(); j++) {
            n++;
        }
        return n;
    }

    // ---- a row ----

    private void row(GuiGraphics g, Item it, int x, int y, int w, int h, boolean compact, int mx, int my) {
        SetRow r = it.row();
        boolean live = SettingsRows.active(r);
        boolean picked = picked(it) && live;
        boolean hover = live && mx >= x - Theme.SP_3 && mx < x + w && my >= y && my < y + h;
        if (picked) {
            Kit.rowLit(g, x - Theme.SP_3, y, w + Theme.SP_3, h);
        } else if (hover) {
            g.fill(x - Theme.SP_3, y, x + w, y + h, Theme.CARD_HOVER);
        }
        int ty = y + Theme.SP_2;
        boolean changed = SettingsRows.rowChanged(r);
        if (changed) {
            // a small gold dot in the gutter, clear of the name
            Art.box(g, x - Theme.SP_3 + 1, Type.BODY.onCaps(ty, 4), 4, 4, 2, Theme.GOLD_DEEP, Theme.GOLD_EDGE);
        }
        int nx = x + (indent(r));
        int nw = x + w - nx;
        String ref = it.cat() + ":" + it.index();
        boolean below = chipsBelow(r, nw);
        // the control first: its click areas win over the row's
        int cw;
        if (below) {
            cw = SettingsRows.hasDefault(r) && changed ? controls.reset(g, r, ref, x + w - Ui.CHIP_H - 2,
                    Type.BODY.onCaps(ty, Ui.CHIP_H), live, mx, my) : 0;
        } else {
            cw = controls.draw(g, r, ref, picked, live, x + w, ty, mx, my);
        }
        int tw = nx + nw - Math.max(CTRL, cw) - Theme.SP_4 - nx;
        boolean toggle = SettingsRows.isToggle(r);
        String title = SettingsText.rowTitle(r);
        String cut = Type.BODY.fit(font(), title, tw);
        Type.BODY.draw(g, font(), cut, nx, ty, toggle && !SettingsRows.toggleOn(r) ? Theme.INK_2 : Theme.INK);
        String hint = SettingsText.rowHint(r);
        int hx = nx + Type.BODY.width(font(), cut) + Theme.SP_3;
        if (hint != null && nx + tw - hx > 24) {
            Type.META.draw(g, font(), Type.META.fit(font(), hint, nx + tw - hx), hx, ty + 1, Theme.INK_3);
        }
        int dy = ty + Type.BODY.lineHeight;
        if (below) {
            dy += Theme.SP_2;
            dy += controls.drawChips(g, r, live, nx, dy, nw, mx, my);
        }
        String desc = SettingsText.rowDesc(r);
        if (picked) {
            dy = more(g, r, desc, nx, dy, nw, compact, hover);
        }
        if (r.kind().equals("keynote") && tab.keyOpen) {
            tab.pages.drawKeyDetail(g, nx, dy + Theme.SP_2, nw, mx, my);
        }
        if (!live) {
            g.fill(x - Theme.SP_3, y, x + w, y + h, SettingsControls.DIM);
            return;                             // pale and still until its parent is on
        }
        // the row: a switch flips with a click anywhere on it, any other row is picked
        int areaW = toggle ? w + Theme.SP_3 : w + Theme.SP_3 - Math.max(CTRL, below ? 0 : cw);
        int areaH = below || r.kind().equals("keynote") ? Type.BODY.lineHeight + 2 * Theme.SP_2 : h;
        if (picked && !toggle) {
            areaH = Type.BODY.lineHeight + 2 * Theme.SP_2;
        }
        menu.toggles.add(new Object[] {new int[] {x - Theme.SP_3, y, areaW, areaH}, (toggle ? "#stog:" : "#ssel:") + ref});
        if (hover && mx < nx + tw && my < y + Type.BODY.lineHeight + 2 * Theme.SP_2) {
            tip(r, title, desc, x, y, nx + tw - x, Type.BODY.lineHeight + 2 * Theme.SP_2);
        }
    }

    /** The picked row's description (two lines, three when compact) and its example; returns the y under them. */
    private int more(GuiGraphics g, SetRow r, String desc, int x, int dy, int tw, boolean compact, boolean hover) {
        if (!desc.isEmpty()) {
            dy += Theme.SP_1;
            List<FormattedCharSequence> lines = Type.META.wrap(font(), desc, tw);
            int shown = Math.min(compact ? 3 : 2, lines.size());
            for (int i = 0; i < shown; i++) {
                if (i == shown - 1 && lines.size() > shown) {
                    Type.META.draw(g, font(), Type.META.fit(font(), descFrom(desc, lines, i), tw), x, dy, Theme.INK_2);
                } else {
                    g.drawString(font(), Kit.ink(lines.get(i)), x, dy, Theme.INK_2, false);
                }
                dy += Type.META.lineHeight;
            }
        }
        String example = SettingsText.rowExample(r);
        if (!example.isEmpty()) {
            dy += Theme.SP_1;
            Art.icon(g, "eye", x, Type.META.onCaps(dy, 8), 8, Theme.INK_3);
            String ex = L.t("wynnlabs.settings.example_title") + ": " + example.replace('\n', ' ');
            String exCut = Type.META.fit(font(), ex, tw - 8 - Theme.SP_2);
            Type.META.draw(g, font(), exCut, x + 8 + Theme.SP_2, dy, Theme.INK_3);
            if (!exCut.equals(ex) && hover) {
                menu.hits.add(new MenuHit(x, dy, tw, Type.META.lineHeight, null, List.of(Component.literal(ex).withStyle(ChatFormatting.GRAY)), null));
            }
            dy += Type.META.lineHeight;
        }
        return dy;
    }

    /** The row's tooltip: its name, what it does, its default and whether it is at it. */
    private void tip(SetRow r, String title, String desc, int x, int y, int w, int h) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
        if (!desc.isEmpty()) {
            tip.add(Glossary.mentions(title + "\n" + desc) ? Glossary.grey(desc) : Component.literal(desc).withStyle(ChatFormatting.GRAY));
        }
        String def = SettingsText.rowDefault(r);
        if (def != null) {
            tip.add(Component.literal(def).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (SettingsRows.hasDefault(r)) {
            tip.add(SettingsRows.rowChanged(r) ? L.c("wynnlabs.settings.changed").withStyle(ChatFormatting.GOLD)
                    : L.c("wynnlabs.settings.at_default").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (r.kind().equals("rule") || r.kind().equals("rulemulti") || r.kind().equals("preset") || r.kind().equals("slots")) {
            tip.add(L.c("wynnlabs.rules.per_character").withStyle(ChatFormatting.DARK_GRAY));
        }
        menu.hits.add(new MenuHit(x - Theme.SP_3, y, w + Theme.SP_3, h, null, tip, null));
    }

    /** The description from line {@code i} on, as one string (so the cut line ends in "…"). */
    private String descFrom(String desc, List<FormattedCharSequence> lines, int i) {
        int skip = 0;
        for (int k = 0; k < i; k++) {
            StringBuilder b = new StringBuilder();
            lines.get(k).accept((idx, style, cp) -> {
                b.appendCodePoint(cp);
                return true;
            });
            skip += b.length();
        }
        return desc.substring(Math.min(desc.length(), skip)).stripLeading();
    }
}
