package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.endgame.ItemIcons;
import io.github.anilken.wynnlabs.endgame.PoolView;
import io.github.anilken.wynnlabs.endgame.RaidPool;
import io.github.anilken.wynnlabs.endgame.WynnventoryPools;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.PoolItem;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Endgame tab's Havuzlar view (openspec endgame-tab, decision 11): the raid and camp sub-tabs,
 * the region list, and each region's pool in scrolling columns with the items' own icons.
 */
final class EndgamePoolsView extends EndgamePanel {

    /** Scroll slots per sub-tab: the three columns (0-2), the compact list, the region list. */
    private static final int S_LIST = 3;
    static final int S_REGIONS = 4;
    /** Lines of a pool list: a group header, the "other classes" rule, an item row. */
    private static final int L_HEAD = 0;
    private static final int L_RULE = 1;
    private static final int L_ROW = 2;

    /** The lists drawn this frame that the wheel scrolls. */
    private final List<Wheel> wheels = new ArrayList<>();
    /** The item rows of a pool list. */
    private final EndgamePoolItems items;

    /** A list the wheel scrolls: its box, its scroll slot and how far it can scroll. */
    private record Wheel(int x, int y, int w, int h, int slot, int max) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** One line of a pool list. */
    private record PLine(int type, PoolView.Group.Kind kind, int count, PoolView.Row row, boolean dim) {}

    EndgamePoolsView(EndgameTab tab) {
        super(tab);
        items = new EndgamePoolItems(tab);
    }

    /** A new frame: the lists the wheel scrolls are added again as they are drawn. */
    void newFrame() {
        wheels.clear();
        for (ScrollBar b : bars) {
            b.place(0, 0, 0, 1, 1, 0);                 // placed again by the lists drawn this frame
        }
    }

    /** Each list's bar, by its slot (the columns, the compact list, the regions). */
    private final ScrollBar[] bars = {new ScrollBar(), new ScrollBar(), new ScrollBar(), new ScrollBar(), new ScrollBar()};

    /** A press on a list's bar: a drag starts or the track pages; true when one took it. */
    boolean barPressed(double mx, double my) {
        for (int i = 0; i < bars.length; i++) {
            if (bars[i].mouseClicked(mx, my)) {
                tab.poolScroll[tab.poolTab][i] = bars[i].offset();
                return true;
            }
        }
        return false;
    }

    /** A drag with a bar held; true while one is. */
    boolean barDragged(double my) {
        for (int i = 0; i < bars.length; i++) {
            if (bars[i].mouseDragged(my)) {
                tab.poolScroll[tab.poolTab][i] = bars[i].offset();
                return true;
            }
        }
        return false;
    }

    void barReleased() {
        for (ScrollBar b : bars) {
            b.mouseReleased();
        }
    }

    /** The wheel over Havuzlar: each column, the compact list and the region list scroll on their own; true when it did. */
    boolean mouseScrolled(double mx, double my, double v) {
        for (Wheel wl : wheels) {
            if (wl.in(mx, my)) {
                if (wl.max() <= 0) {
                    return false;
                }
                int[] sc = tab.poolScroll[tab.poolTab];
                sc[wl.slot()] = Math.max(0, Math.min(wl.max(), sc[wl.slot()] - (int) Math.signum(v)));
                return true;
            }
        }
        return false;
    }

    /**
     * The region named {@code want}, or the week's best for you: the raid with the most aspects
     * marked for you (ties in raid order), the camp with the most mythics; null without regions.
     */
    PoolView.Region best(boolean raid, String want) {
        List<PoolView.Region> regs = tab.d.v().regions(raid);
        if (regs.isEmpty()) {
            return null;
        }
        for (PoolView.Region r : regs) {
            if (r.name().equals(want)) {
                return r;
            }
        }
        PoolView.Region best = regs.get(0);
        for (PoolView.Region r : regs) {
            if (raid ? r.marked() > best.marked() : r.mythics() > best.mythics()) {
                best = r;
            }
        }
        return best;
    }

    /**
     * One raid's or camp's pool in a box (the pool drawer): its line (age, count, the class chip),
     * then its groups in columns (Aspectler | Tome'lar | Ekipman, Diğer when large; one list when
     * compact), each scrolling on its own with a bar.
     */
    void regionPane(GuiGraphics g, boolean raid, String region, int x, int y, int w, int h, boolean compact, boolean large,
            int mx, int my) {
        if (DevHooks.ON) {
            DevHooks.part("pools", g.pose(), x, y, w, h);
        }
        tab.poolTab = raid ? EndgameTab.TAB_RAIDS : EndgameTab.TAB_CAMPS;
        int bottom = y + h;
        PoolView.Region lit = best(raid, region);
        if (lit == null) {
            boolean loading = tab.d.p().status() == WynnventoryPools.Status.LOADING;
            String s = L.t(loading ? "wynnlabs.endgame.pools_loading"
                    : raid ? "wynnlabs.endgame.pools_none_raids" : "wynnlabs.endgame.pools_none_loot");
            Type.MUTED.drawWrapped(g, tab.font, s, x, y, w, 4, Theme.INK_2);
            return;
        }
        if (raid) {
            tab.litRaid = lit.name();
        } else {
            tab.litCamp = lit.name();
        }
        List<PoolView.Group> groups = tab.d.v().groups(raid, lit.name());
        paneHeader(g, lit, raid, x, y, w, mx, my);
        int top = y + 17 + Theme.SP_2;
        if (compact) {
            column(g, lines(groups, raid), x, top, w, bottom - top, S_LIST, true, raid, lit, mx, my);
            return;
        }
        int n = large ? 3 : 2;
        int gap = Theme.SP_5;
        int cw = (w - gap * (n - 1)) / n;
        List<List<PoolView.Group>> cols = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cols.add(new ArrayList<>());
        }
        for (PoolView.Group gr : groups) {
            cols.get(columnOf(gr.kind(), raid, large)).add(gr);
        }
        for (int i = 0; i < n; i++) {
            int cx = x + i * (cw + gap);
            column(g, lines(cols.get(i), raid), cx, top, i == n - 1 ? x + w - cx : cw, bottom - top, i, false, raid, lit, mx, my);
        }
    }

    /**
     * The column a group goes in. Standard: a raid's aspects | the rest; a camp's Shiny, Mythic,
     * Fabled | the rest. Large: Aspectler | Tome'lar | Ekipman, Diğer; Shiny, Mythic | Fabled,
     * Legendary | Rare, Unique, Diğer.
     */
    private static int columnOf(PoolView.Group.Kind k, boolean raid, boolean large) {
        if (raid) {
            return switch (k) {
                case SHINY, ASPECTS -> 0;
                case TOMES -> 1;
                default -> large ? 2 : 1;
            };
        }
        return switch (k) {
            case SHINY, MYTHIC -> 0;
            case FABLED -> large ? 1 : 0;
            case LEGENDARY -> 1;
            default -> large ? 2 : 1;
        };
    }

    /** The tooltips' source line. */
    String sourceTip(boolean raid) {
        if (raid && !tab.d.p().wynnventory()) {
            return L.t("wynnlabs.endgame.tip_src_game");
        }
        return L.t("wynnlabs.endgame.tip_src_week", raid ? tab.d.p().week() : tab.d.p().lootWeek());
    }

    /** A region's name: the raid or camp, or "Pool you saw in game" for the in-game pool. */
    static String regionName(PoolView.Region r) {
        return r.name().isEmpty() ? L.t("wynnlabs.endgame.ingame_region") : r.name();
    }

    /** "3 new" for a raid, "4 mythic" for a camp; "" when none. */
    static String regionCount(PoolView.Region r) {
        if (r.raid()) {
            return r.marked() > 0 ? L.t("wynnlabs.endgame.pool_new", r.marked()) : "";
        }
        return r.mythics() > 0 ? L.n("wynnlabs.endgame.mythic_n", r.mythics()) : "";
    }

    /** A region row's tooltip: its full name, its age, every group with its count, the source. */
    private List<Component> regionTip(PoolView.Region r, boolean click) {
        List<Component> t = new ArrayList<>();
        t.add(title(regionName(r), ChatFormatting.GOLD));
        boolean stale = tab.d.v().stale(r.raid());
        if (r.seenAt() >= 0) {
            t.add(grey(agoLong(r.seenAt()) + (stale ? L.t("wynnlabs.endgame.pool_stale") : "")));
        }
        String count = regionCount(r);
        if (!count.isEmpty() || r.shinies() > 0) {
            String shiny = r.shinies() > 0 ? L.t("wynnlabs.endgame.shiny_n", r.shinies()) : "";
            t.add(r.raid() ? good(count) : Component.literal(count.isEmpty() ? shiny : shiny.isEmpty() ? count : count + " · " + shiny)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        for (PoolView.Group gr : tab.d.v().groups(r.raid(), r.name())) {
            t.add(grey("  " + groupName(gr.kind()) + " " + gr.rows().size()));
        }
        if (stale) {
            t.add(warn(L.t("wynnlabs.endgame.tip_week_stale")));
        }
        t.add(dark(sourceTip(r.raid())));
        if (click) {
            t.add(dark(L.t("wynnlabs.endgame.card_click")));
        }
        return t;
    }

    /** The pane's header: the region's name in capitals, its age and counts (drawn whole), and the class chip for a raid. */
    private void paneHeader(GuiGraphics g, PoolView.Region r, boolean raid, int x, int y, int w, int mx, int my) {
        int right = x + w;
        if (raid && tab.d.klass() != Klass.NONE) {
            right = classChip(g, right, y, mx, my) - Theme.SP_3;
        }
        boolean stale = tab.d.v().stale(raid);
        String rest = headRest(r, stale);
        // the facts only: the drawer's title names it
        rest = rest.startsWith(" · ") ? rest.substring(3) : rest;
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, rest, right - x), x, Type.META.yIn(y, Ui.CHIP_H), stale ? GREYED : Theme.INK_2);
        tab.tips.add(new Tip(x, y, right - x, Ui.CHIP_H, regionTip(r, false)));
    }

    /** " · 3 h ago · 3 new" / " · 2 h ago · 4 mythic · 1 shiny", with "· may be old" after the age. */
    private String headRest(PoolView.Region r, boolean stale) {
        StringBuilder b = new StringBuilder();
        if (r.seenAt() >= 0) {
            b.append(" · ").append(agoLong(r.seenAt()));
        }
        if (stale) {
            b.append(L.t("wynnlabs.endgame.pool_stale"));
        }
        String count = regionCount(r);
        if (!count.isEmpty()) {
            b.append(" · ").append(count);
        }
        if (r.shinies() > 0) {
            b.append(" · ").append(L.t("wynnlabs.endgame.shiny_n", r.shinies()));
        }
        return b.toString();
    }

    /** The "Shaman only" chip ending at {@code right}; returns its left edge. */
    private int classChip(GuiGraphics g, int right, int y, int mx, int my) {
        String s = L.t("wynnlabs.endgame.class_only", nice(tab.d.klass()));
        int bw = Ui.chipWidth(tab.font, s);
        int bx = right - bw;
        Ui.chip(g, tab.font, s, bx, y, bw, tab.classOnly, false, false, mx, my);
        tab.acts.add(new Act(bx, y, bw, 14, () -> {
            tab.classOnly = !tab.classOnly;
            int[] sc = tab.poolScroll[EndgameTab.TAB_RAIDS];
            for (int i = 0; i < S_REGIONS; i++) {
                sc[i] = 0;
            }
            tab.d = null;                                   // the groups are read again with the chip
        }, "endgame:classonly"));
        tab.tips.add(new Tip(bx, y, bw, 14, List.of(grey(L.t(tab.classOnly ? "wynnlabs.endgame.class_only_off" : "wynnlabs.endgame.class_only_tip",
                nice(tab.d.klass()))))));
        return bx;
    }

    /** The lines of a pool list: each group's header and rows, the "other classes" rule before the first other class's aspect. */
    private List<PLine> lines(List<PoolView.Group> groups, boolean raid) {
        List<PLine> out = new ArrayList<>();
        boolean klass = tab.d.klass() != Klass.NONE;
        for (PoolView.Group gr : groups) {
            out.add(new PLine(L_HEAD, gr.kind(), gr.rows().size(), null, false));
            boolean ruled = false;
            for (PoolView.Row r : gr.rows()) {
                boolean other = raid && klass && r.item().aspect() && !r.ownClass();
                if (other && !ruled) {
                    out.add(new PLine(L_RULE, gr.kind(), 0, null, false));
                    ruled = true;
                }
                out.add(new PLine(L_ROW, gr.kind(), 0, r, other));
            }
        }
        return out;
    }

    private static int lineH(PLine l, boolean compact) {
        // a row: the 12 px picture and SP_2 of air under it; the "other classes" rule: SP_3 above and below
        return l.type() == L_ROW ? EndgamePoolItems.ICON_SIZE + Theme.SP_2 : l.type() == L_RULE ? 12 + 2 * Theme.SP_3 : 12;
    }

    /** One scrolling list of a pool (a column, or the compact list) with its own thumb. */
    private void column(GuiGraphics g, List<PLine> lines, int x, int y, int w, int h, int slot, boolean compact, boolean raid,
            PoolView.Region region, int mx, int my) {
        int bottom = y + h;
        int fitH = 0;
        int from = lines.size();
        while (from > 0 && fitH + lineH(lines.get(from - 1), compact) <= h) {
            fitH += lineH(lines.get(from - 1), compact);
            from--;
        }
        int max = from;
        int[] sc = tab.poolScroll[tab.poolTab];
        sc[slot] = Math.max(0, Math.min(max, sc[slot]));
        wheels.add(new Wheel(x, y, w, h, slot, max));
        int rw = max > 0 ? w - ScrollBar.W - Theme.SP_1 : w;
        int ly = y;
        int shown = 0;
        for (int i = sc[slot]; i < lines.size(); i++) {
            PLine l = lines.get(i);
            int lh = lineH(l, compact);
            if (ly + lh > bottom) {
                break;
            }
            switch (l.type()) {
                case L_HEAD -> groupHeader(g, l, x, ly, rw);
                case L_RULE -> otherRule(g, x, ly, rw);
                default -> items.row(g, l.row(), l.dim(), l.kind(), x, ly, rw, lh, compact, raid, region, shown, mx, my);
            }
            ly += lh;
            shown++;
        }
        if (max > 0) {
            thumb(g, x + w - ScrollBar.W, y, h, lines.size(), sc[slot], max, slot, mx, my);
        }
    }

    /** A list's bar ({@link ScrollBar}) in its box at x: rows from {@code pos}, {@code max} the furthest. */
    private void thumb(GuiGraphics g, int x, int y, int h, int total, int pos, int max, int slot, int mx, int my) {
        bars[slot].place(x, y, h, total - max, total, pos);
        bars[slot].draw(g, mx, my);
    }

    private static String groupName(PoolView.Group.Kind k) {
        return switch (k) {
            case SHINY -> L.t("wynnlabs.endgame.grp_shiny");
            case ASPECTS -> L.t("wynnlabs.endgame.aspects");
            case TOMES -> L.t("wynnlabs.endgame.grp_tomes");
            case GEAR -> L.t("wynnlabs.endgame.grp_gear");
            case MYTHIC -> rarityName(Tier.MYTHIC);
            case FABLED -> rarityName(Tier.FABLED);
            case LEGENDARY -> rarityName(Tier.LEGENDARY);
            case RARE -> rarityName(Tier.RARE);
            case UNIQUE -> rarityName(Tier.UNIQUE);
            default -> L.t("wynnlabs.endgame.grp_other");
        };
    }

    /** A group's header: its name and row count over a rule ("Shiny" with the shiny mark). */
    private void groupHeader(GuiGraphics g, PLine l, int x, int y, int w) {
        int tx = x;
        if (l.kind() == PoolView.Group.Kind.SHINY) {
            Art.icon(g, "sparkles", x, y + 2, 8, Theme.TIER_UNIQUE);
            tx += 10;
        }
        Kit.section(g, tab.font, fit(groupName(l.kind()) + " " + l.count(), x + w - tx - 12), tx, y + 2, x + w - tx);
    }

    /** The dotted rule "other classes" between the class's own aspects and the others. */
    private void otherRule(GuiGraphics g, int x, int y, int w) {
        y += Theme.SP_3;
        String s = fit(L.t("wynnlabs.endgame.other_classes"), w - 16);
        int sw = tab.font.width(s);
        int tx = x + (w - sw) / 2;
        for (int dx = x + 2; dx + 2 <= tx - 3; dx += 4) {
            Kit.hair(g, dx, y + 6, 2, 1, Theme.LINE);
        }
        for (int dx = tx + sw + 3; dx + 2 <= x + w; dx += 4) {
            Kit.hair(g, dx, y + 6, 2, 1, Theme.LINE);
        }
        ink(g, s, tx, y + 2, Theme.INK_3);
    }

}
