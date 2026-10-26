package io.github.anilken.wynnlabs.ui;

import static io.github.anilken.wynnlabs.ui.SettingsRows.SETTING_CATS;
import static io.github.anilken.wynnlabs.ui.SettingsRows.SETTING_CAT_DESC;
import static io.github.anilken.wynnlabs.ui.SettingsRows.SETTING_CAT_ICONS;
import static io.github.anilken.wynnlabs.ui.SettingsRows.SETTING_CAT_NAMES;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.ui.SettingsRows.SetRow;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings drawn: the categories as a list on the left (each with how many of its settings
 * differ from their default) or, compact, as a filter row; then a page of rows on one grid: the
 * name (BODY), what it does under it (META) and the control in a fixed column on the right
 * (a switch, − value +, a segmented choice, the key's state). The picked row shows its example;
 * a changed one has a gold dot and a reset beside its control. Search results and the changed
 * settings are the same rows under their categories' names.
 */
final class SettingsList extends MenuPaint {

    /** The categories' list on the large layout. */
    static final int NAV_W = 132;
    private static final int NAV_ROW = 18;
    /** The narrowest control column: a switch, or − value +. */
    private static final int CTRL = 76;
    private static final int NAV_SEAL = 0xFFF0CF6A;

    private final SettingsTab tab;

    SettingsList(RoadmapScreen menu, SettingsTab tab) {
        super(menu);
        this.tab = tab;
        controls = new SettingsControls(menu, tab);
    }

    private final SettingsControls controls;

    /** A line of the page: a section title ({@code title} set; {@code row} null for a category's name) or a setting. */
    record Item(int cat, int index, SetRow row, String title) {

        boolean head() {
            return title != null;
        }
    }

    // ---- the categories ----

    /** The categories as a list from {@code y}, and under it how many settings differ from their default. */
    void nav(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int ry = y;
        for (int c = 0; c < SETTING_CATS.length; c++) {
            boolean on = c == tab.settingsCat && !tab.listView();
            boolean hover = mx >= x - Theme.SP_2 && mx < x + w && my >= ry && my < ry + NAV_ROW;
            if (on) {
                Kit.rowLit(g, x - Theme.SP_2, ry, w + Theme.SP_2, NAV_ROW);
                g.fill(x - Theme.SP_2, ry + 3, x - Theme.SP_2 + 2, ry + NAV_ROW - 3, Theme.GOLD_DEEP);
            } else if (hover) {
                g.fill(x - Theme.SP_2, ry, x + w, ry + NAV_ROW, Theme.CARD_HOVER);
            }
            Type role = on ? Type.LABEL : Type.BODY;
            int ink = on ? Theme.INK : Theme.INK_2;
            int ty = role.yIn(ry, NAV_ROW);
            Art.icon(g, SETTING_CAT_ICONS[c], x + Theme.SP_2, role.onCaps(ty, 8), 8, ink);
            int tx = x + Theme.SP_2 + 8 + Theme.SP_3;
            int changed = SettingsRows.changedIn(SETTING_CATS[c]);
            int right = x + w - Theme.SP_2;
            if (changed > 0) {
                String n = String.valueOf(changed);
                int sw = Math.max(11, Type.META.width(font(), n) + 2 * Theme.SP_2);
                int sy = ry + (NAV_ROW - Ui.SEAL_H) / 2;
                Kit.seal(g, right - sw, sy, sw, Ui.SEAL_H, NAV_SEAL);
                Type.META.draw(g, font(), n, right - sw + (sw - Type.META.width(font(), n)) / 2, Type.META.yIn(sy, Ui.SEAL_H),
                        Theme.INK);
                right -= sw + Theme.SP_2;
            }
            String name = L.t(SETTING_CAT_NAMES[c]);
            role.draw(g, font(), role.fit(font(), name, right - tx), tx, ty, ink);
            menu.toggles.add(new Object[] {new int[] {x - Theme.SP_2, ry, w + Theme.SP_2, NAV_ROW}, "#cat:" + c});
            if (hover) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(name).withStyle(ChatFormatting.WHITE));
                tip.add(L.c(SETTING_CAT_DESC[c]).withStyle(ChatFormatting.GRAY));
                if (changed > 0) {
                    tip.add(L.c("wynnlabs.settings.changed_count", changed).withStyle(ChatFormatting.GOLD));
                }
                menu.hits.add(new MenuHit(x - Theme.SP_2, ry, w + Theme.SP_2, NAV_ROW, null, tip, null));
            }
            ry += NAV_ROW + Theme.SP_1;
        }
        // the foot: how many differ from their default, and the link that lists them
        int n = SettingsRows.changedTotal();
        int fy = y + h - 2 * Type.META.lineHeight;
        if (fy - Theme.SP_3 < ry) {
            return;
        }
        Kit.hair(g, x, fy - Theme.SP_3, w, 1, Theme.LINE);
        String what = n > 0 ? L.t("wynnlabs.settings.changed_count", n) : L.t("wynnlabs.settings.all_default");
        Type.META.draw(g, font(), Type.META.fit(font(), what, w), x, fy, Theme.INK_2);
        if (n > 0 || tab.showChanged) {
            String link = tab.showChanged ? "‹ " + L.t("wynnlabs.settings.back") : L.t("wynnlabs.settings.show_changed");
            int lw = Type.META.width(font(), link);
            int ly = fy + Type.META.lineHeight;
            Type.META.draw(g, font(), link, x, ly, Theme.LINK);
            menu.toggles.add(new Object[] {new int[] {x, ly - 1, lw, Type.META.lineHeight}, tab.showChanged ? "#sclearview" : "#sshowchanged"});
        }
    }

    /** Compact: the categories as a filter row (a count after a changed one's name); returns its height. */
    int filter(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<Ui.Tab> list = new ArrayList<>();
        for (int c = 0; c < SETTING_CATS.length; c++) {
            int changed = SettingsRows.changedIn(SETTING_CATS[c]);
            list.add(new Ui.Tab(null, L.t(SETTING_CAT_NAMES[c]) + (changed > 0 ? " " + changed : "")));
        }
        int[][] hits = new int[list.size() + 2][];
        int h = Ui.filterRow(g, font(), x, y, w, List.of(list), new int[] {tab.listView() ? -1 : tab.settingsCat},
                tab.filterFirst, mx, my, hits);
        for (int c = 0; c < list.size(); c++) {
            if (hits[c] != null) {
                menu.toggles.add(new Object[] {hits[c], "#cat:" + c});
            }
        }
        if (hits[list.size()] != null) {
            menu.toggles.add(new Object[] {hits[list.size()], "#sfirst:-1"});
        }
        if (hits[list.size() + 1] != null) {
            menu.toggles.add(new Object[] {hits[list.size() + 1], "#sfirst:1"});
        }
        return h;
    }

    // ---- the page ----

    /** A page's title (HEADING), what it holds beside it and a note on the right; returns the height. */
    int pageHead(GuiGraphics g, int x, int y, int w, String title, String desc, String right) {
        int rw = right == null ? 0 : Type.META.width(font(), right);
        String t = Type.HEADING.fit(font(), title, w - rw - Theme.SP_4);
        Type.HEADING.draw(g, font(), t, x, y);
        int dy = y + (Type.HEADING.lineHeight - Type.META.lineHeight);
        int dx = x + Type.HEADING.width(font(), t) + Theme.SP_4;
        int room = x + w - rw - Theme.SP_4 - dx;
        if (desc != null && room > 30) {
            Type.META.draw(g, font(), Type.META.fit(font(), desc, room), dx, dy, Theme.INK_2);
        }
        if (right != null) {
            Type.META.draw(g, font(), right, x + w - rw, dy, Theme.INK_3);
        }
        return Type.HEADING.lineHeight + Theme.SP_5;
    }

    /** The items of a category: its section titles and its rows. */
    static List<Item> catItems(int cat) {
        List<Item> items = new ArrayList<>();
        List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[cat]);
        for (int i = 0; i < rows.size(); i++) {
            SetRow r = rows.get(i);
            items.add(new Item(cat, i, r, r.head() ? L.t(r.id()) : null));
        }
        return items;
    }

    /** The rows that match the search (or, {@code query} null, that differ from their default), under their categories' names. */
    static List<Item> found(String query) {
        List<Item> items = new ArrayList<>();
        for (int c = 0; c < SETTING_CATS.length; c++) {
            List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[c]);
            boolean named = false;
            for (int i = 0; i < rows.size(); i++) {
                SetRow r = rows.get(i);
                boolean hit = query == null ? !r.head() && SettingsRows.rowChanged(r) : SettingsRows.matches(r, query);
                if (!hit) {
                    continue;
                }
                if (!named) {
                    items.add(new Item(c, -1, null, L.t(SETTING_CAT_NAMES[c])));
                    named = true;
                }
                items.add(new Item(c, i, r, null));
            }
        }
        return items;
    }

    /** The rows and titles from {@code y}, scrolled, at most {@code h} tall; a thin bar at the right when they scroll. */
    void list(GuiGraphics g, List<Item> items, int x, int y, int w, int h, boolean compact, int mx, int my) {
        int bar = 6;
        int iw = w - bar;
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
                } else {
                    row(g, it, x, ry, iw, hs[i], compact, mx, rowMy);
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
        // where in the list you are: a thin bar at the right, dragged or clicked
        tab.settingsBar.place(x + w - ScrollBar.W, y, h, h, total, tab.settingsScroll);
        tab.settingsBar.draw(g, mx, my);
    }

    private int height(Item it, int w, boolean first, boolean compact) {
        if (it.head()) {
            return (first ? 0 : Theme.SP_4) + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_2;
        }
        SetRow r = it.row();
        int tw = textWidth(r, it, w);
        String desc = SettingsRows.rowDesc(r);
        int lines = desc.isEmpty() ? 0 : Math.min(compact ? 2 : 1, Type.META.wrap(font(), desc, tw).size());
        int h = Theme.SP_2 + Type.BODY.lineHeight + lines * Type.META.lineHeight + Theme.SP_2;
        if (picked(it) && !SettingsRows.rowExample(r).isEmpty()) {
            h += Theme.SP_1 + Type.META.lineHeight;
        }
        if (r.kind().equals("key")) {
            h += SettingsTab.keyDetailHeight() + Theme.SP_2;
        }
        return h;
    }

    private boolean picked(Item it) {
        return it.cat() == tab.pickCat && it.index() == tab.pickRow;
    }

    /** A section title in LABEL over a hairline; a section of switches gets its "all off / all on" link on the right. */
    private void head(GuiGraphics g, Item it, int x, int y, int w, int mx, int my) {
        int lw = 0;
        if (it.row() != null) {
            List<String> keys = tab.sectionKeys(it.cat(), it.index());
            if (!keys.isEmpty()) {
                boolean anyOn = SectionSwitches.anyOn(keys);
                String label = SectionSwitches.label(anyOn);
                lw = Type.META.width(font(), label);
                int lx = x + w - lw;
                Type.META.draw(g, font(), label, lx, y + (Type.LABEL.lineHeight - Type.META.lineHeight), Theme.LINK);
                menu.toggles.add(new Object[] {new int[] {lx, y, lw, Type.LABEL.lineHeight}, "#sall:" + it.cat() + ":" + it.index()});
                if (mx >= lx && mx < lx + lw && my >= y && my < y + Type.LABEL.lineHeight) {
                    rowTip(lx, y, lw, Type.LABEL.lineHeight, label, SectionSwitches.tip(anyOn, it.title()));
                }
                lw += Theme.SP_4;
            }
        }
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), it.title(), w - lw), x, y);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
    }

    // ---- a row ----

    /** The width a row leaves its name and description: its control (at least {@link #CTRL}) and SP_4 off. */
    private int textWidth(SetRow r, Item it, int w) {
        return w - Math.max(CTRL, controls.width(r, picked(it))) - Theme.SP_4;
    }

    private void row(GuiGraphics g, Item it, int x, int y, int w, int h, boolean compact, int mx, int my) {
        SetRow r = it.row();
        boolean picked = picked(it);
        boolean hover = mx >= x - Theme.SP_3 && mx < x + w && my >= y && my < y + h;
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
        String ref = it.cat() + ":" + it.index();
        // the control first: its click areas win over the row's
        int cw = controls.draw(g, it.row(), it.cat() + ":" + it.index(), picked(it), x + w, ty, mx, my);
        int tw = w - Math.max(CTRL, cw) - Theme.SP_4;
        boolean toggle = SettingsRows.isToggle(r);
        String title = SettingsRows.rowTitle(r);
        String cut = Type.BODY.fit(font(), title, tw);
        Type.BODY.draw(g, font(), cut, x, ty, toggle && !SettingsRows.toggleOn(r) ? Theme.INK_2 : Theme.INK);
        int dy = ty + Type.BODY.lineHeight;
        String desc = SettingsRows.rowDesc(r);
        List<FormattedCharSequence> lines = desc.isEmpty() ? List.of() : Type.META.wrap(font(), desc, tw);
        int shown = Math.min(compact ? 2 : 1, lines.size());
        for (int i = 0; i < shown; i++) {
            boolean last = i == shown - 1 && lines.size() > shown;
            if (last) {
                String rest = Type.META.fit(font(), descFrom(desc, lines, i), tw);
                Type.META.draw(g, font(), rest, x, dy, Theme.INK_2);
            } else {
                g.drawString(font(), Kit.ink(lines.get(i)), x, dy, Theme.INK_2, false);
            }
            dy += Type.META.lineHeight;
        }
        String example = SettingsRows.rowExample(r);
        if (picked && !example.isEmpty()) {
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
        if (r.kind().equals("key")) {
            tab.drawKeyDetail(g, x, dy + Theme.SP_2, w, mx, my);
        }
        // the row: a switch flips with a click anywhere on it, any other row is picked (its example shows)
        int areaW = toggle ? w + Theme.SP_3 : w + Theme.SP_3 - Math.max(CTRL, cw);
        int areaH = r.kind().equals("key") ? dy - y : h;
        menu.toggles.add(new Object[] {new int[] {x - Theme.SP_3, y, areaW, areaH}, (toggle ? "#stog:" : "#ssel:") + ref});
        if (hover && mx < x + tw) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(title).withStyle(ChatFormatting.WHITE));
            if (!desc.isEmpty()) {
                tip.add(Glossary.mentions(title + "\n" + desc) ? Glossary.grey(desc) : Component.literal(desc).withStyle(ChatFormatting.GRAY));
            }
            String def = SettingsRows.rowDefault(r);
            if (def != null) {
                tip.add(Component.literal(def).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (SettingsRows.hasDefault(r)) {
                tip.add(SettingsRows.rowChanged(r) ? L.c("wynnlabs.settings.changed").withStyle(ChatFormatting.GOLD)
                        : L.c("wynnlabs.settings.at_default").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (r.kind().equals("rule")) {
                tip.add(L.c("wynnlabs.rules.per_character").withStyle(ChatFormatting.DARK_GRAY));
            }
            menu.hits.add(new MenuHit(x - Theme.SP_3, y, tw + Theme.SP_3, areaH, null, tip, null));
        }
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
