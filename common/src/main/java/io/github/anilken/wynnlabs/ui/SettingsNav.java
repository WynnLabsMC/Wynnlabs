package io.github.anilken.wynnlabs.ui;

import static io.github.anilken.wynnlabs.ui.SettingsRows.SETTING_CATS;
import static io.github.anilken.wynnlabs.ui.SettingsRows.SETTING_CAT_ICONS;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.UpdateCheck;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings' pages: a list on the left (an icon, the name, a gold count of what differs from
 * its default) with its foot ("Applies instantly", "N changed", the About "?"), or, compact, a
 * filter row on top and the same foot as one line under the page.
 */
final class SettingsNav extends MenuPaint {

    /** The pages' list on the large layout. */
    static final int NAV_W = 132;
    private static final int NAV_ROW = 18;
    private static final int NAV_SEAL = 0xFFF0CF6A;
    /** The compact foot's height: one line of chips. */
    static final int FOOT_H = Ui.CHIP_H;

    private final SettingsTab tab;

    SettingsNav(RoadmapScreen menu, SettingsTab tab) {
        super(menu);
        this.tab = tab;
    }

    /** The pages as a list from {@code y}, and the foot at the bottom of {@code h}. */
    void nav(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int ry = y;
        for (int c = 0; c < SETTING_CATS.length; c++) {
            boolean on = c == tab.settingsCat && !tab.listView() && !tab.aboutOpen;
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
            String name = L.t(SettingsRows.pageKey(c));
            role.draw(g, font(), role.fit(font(), name, right - tx), tx, ty, ink);
            menu.toggles.add(new Object[] {new int[] {x - Theme.SP_2, ry, w + Theme.SP_2, NAV_ROW}, "#cat:" + c});
            if (hover && changed > 0) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(name).withStyle(ChatFormatting.WHITE));
                tip.add(L.c("wynnlabs.settings.changed_count", changed).withStyle(ChatFormatting.GOLD));
                menu.hits.add(new MenuHit(x - Theme.SP_2, ry, w + Theme.SP_2, NAV_ROW, null, tip, null));
            }
            ry += NAV_ROW + Theme.SP_2;
        }
        // the foot: "Applies instantly" once, then how many differ (a link to them) and About's "?"
        int fy = y + h - Ui.CHIP_H;
        int ay = fy - Theme.SP_2 - Type.META.lineHeight;
        if (ay - Theme.SP_3 < ry) {
            return;
        }
        Kit.hair(g, x, ay - Theme.SP_3, w, 1, Theme.LINE);
        Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.settings.applies_now"), w), x, ay, Theme.INK_3);
        footLine(g, x, fy, w, mx, my);
    }

    /** Compact: the pages as a filter row (a count after a changed one's name); returns its height. */
    int filter(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<Ui.Tab> list = new ArrayList<>();
        for (int c = 0; c < SETTING_CATS.length; c++) {
            int changed = SettingsRows.changedIn(SETTING_CATS[c]);
            list.add(new Ui.Tab(null, L.t(SettingsRows.pageKey(c)) + (changed > 0 ? " " + changed : "")));
        }
        int[][] hits = new int[list.size() + 2][];
        int h = Ui.filterRow(g, font(), x, y, w, List.of(list), new int[] {tab.listView() || tab.aboutOpen ? -1 : tab.settingsCat},
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

    /** Compact: the foot as one line ("Applies instantly" left, the changed link and "?" right) at y. */
    void compactFoot(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String now = L.t("wynnlabs.settings.applies_now");
        int right = footRight(g, x, y, w, mx, my);
        int room = right - Theme.SP_4 - x;
        if (room > 30) {
            Type.META.draw(g, font(), Type.META.fit(font(), now, room), x, Type.META.yIn(y, Ui.CHIP_H), Theme.INK_3);
        }
    }

    /** The large foot's line: the changed link on the left, "?" on the right. */
    private void footLine(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int right = about(g, x + w - Ui.CHIP_H - 2, y, mx, my) - Theme.SP_3;
        changedLink(g, x, y, right - x, mx, my);
    }

    /** "?" then the changed link, both ending at x + w; returns where they start. */
    private int footRight(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int qx = about(g, x + w - Ui.CHIP_H - 2, y, mx, my);
        String label = changedLabel();
        int lw = Math.min(qx - Theme.SP_3 - x, Type.LINK.width(font(), label));
        int lx = qx - Theme.SP_3 - lw;
        changedLink(g, lx, y, lw, mx, my);
        return lx;
    }

    private String changedLabel() {
        int n = SettingsRows.changedTotal();
        return tab.showChanged ? L.t("wynnlabs.settings.back") : n > 0 ? L.t("wynnlabs.settings.changed_short", n)
                : L.t("wynnlabs.settings.none_changed");
    }

    /** "2 changed" (a link to the list of them), "Back" from that list; plain text when nothing changed. */
    private void changedLink(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String label = changedLabel();
        boolean link = tab.showChanged || SettingsRows.changedTotal() > 0;
        int ty = Type.LINK.yIn(y, Ui.CHIP_H);
        String cut = Type.LINK.fit(font(), label, w);
        Type.LINK.draw(g, font(), cut, x, ty, link ? Theme.LINK : Theme.INK_3);
        if (link) {
            int lw = Type.LINK.width(font(), cut);
            menu.toggles.add(new Object[] {new int[] {x, y, lw, Ui.CHIP_H}, tab.showChanged ? "#sclearview" : "#sshowchanged"});
        }
    }

    /** About's "?" at x, gold while About is open, with a dot when a newer version is out; returns x. */
    private int about(GuiGraphics g, int x, int y, int mx, int my) {
        boolean hover = Ui.iconButton(g, "question-mark", x, y, tab.aboutOpen, mx, my);
        if (UpdateCheck.state().status() == UpdateCheck.Status.NEWER) {
            int d = 4;
            Art.box(g, x + Ui.CHIP_H + 2 - d, y, d, d, 2, Theme.GOOD, Theme.GOOD);
        }
        menu.toggles.add(new Object[] {new int[] {x, y, Ui.CHIP_H + 2, Ui.CHIP_H}, "#about"});
        if (hover) {
            rowTip(x, y, Ui.CHIP_H + 2, Ui.CHIP_H, L.t("wynnlabs.settings.about_title"), UpdateCheck.state().status()
                    == UpdateCheck.Status.NEWER ? L.t("wynnlabs.screen.rail_badge_update") : L.t("wynnlabs.settings.about_tip"));
        }
        return x;
    }
}
