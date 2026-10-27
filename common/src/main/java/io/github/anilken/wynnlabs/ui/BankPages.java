package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bank's pages as in the game, side by side under their bank's title, scrolled as one. The
 * slot size follows the width (22 down to 14, even) so two pages fit side by side when they can;
 * as many pages a row as fit. A search or a filter shows only what matches: pages without a match go,
 * and on the others what does not match leaves its slot empty (the places still say where); each
 * page says how many of its items match. Pages not read yet are faint outlines. It also holds the
 * filter, the layout and the order the other layouts ({@link BankList}) use.
 */
final class BankPages {

    static final int GAP = Theme.SP_2;
    private static final int BAR_W = 36;

    // ---- the filter ----
    String query = "";
    BankIndex.Kind kind;
    Tier tier;
    /** Account, this character's, the others' banks shown. */
    final boolean[] banks = {true, true, true};
    boolean fullFirst;

    /** The layout: the banks' pages, one sorted list, or every item in one grid; kept in the settings. */
    static final int VIEW_PAGES = 0;
    static final int VIEW_LIST = 1;
    static final int VIEW_ALL = 2;
    int view = Math.max(0, Math.min(2, io.github.anilken.wynnlabs.core.Settings.number("bankView", VIEW_PAGES)));
    /** The list's and the grid's order: name, kind, tier, value. */
    static final int SORT_NAME = 0;
    static final int SORT_KIND = 1;
    static final int SORT_TIER = 2;
    static final int SORT_VALUE = 3;
    int sort = Math.max(0, Math.min(3, io.github.anilken.wynnlabs.core.Settings.number("bankSort", SORT_NAME)));
    /** The stacks that pass the filter, in the list's order. */
    List<BankIndex.Item> shownStacks = List.of();

    /** The slot picked for the drawer (a gold edge), or null. */
    BankIndex.Item picked;
    /** This frame's slots: {x, y, w, h} and their item, for clicks. */
    final List<Object[]> slots = new ArrayList<>();
    /** Matches over the whole bank, in page order (for the line under the search). */
    final List<Object[]> matchList = new ArrayList<>();

    /** Each page's top in content pixels this frame. */
    private final Map<String, Integer> tops = new java.util.HashMap<>();

    /** A page's top in content pixels (last frame), or -1. */
    int pageY(String key) {
        return tops.getOrDefault(key, -1);
    }

    private Set<BankIndex.Item> matched = Collections.newSetFromMap(new IdentityHashMap<>());
    private Map<String, Integer> perPage = Map.of();
    private Object matchedFor;
    private String matchedKey = "";

    boolean filtered() {
        return !query.isEmpty() || kind != null || tier != null;
    }

    boolean shown(BankIndex.Bank b) {
        return banks[b.ordinal()];
    }

    /** The matches for this filter, worked out once per change of the bank or the filter. */
    private void match(BankIndex idx) {
        String key = query + "|" + kind + "|" + tier + "|" + sort;
        if (idx == matchedFor && key.equals(matchedKey)) {
            return;
        }
        matchedFor = idx;
        matchedKey = key;
        Set<BankIndex.Item> m = Collections.newSetFromMap(new IdentityHashMap<>());
        Map<String, Integer> counts = new java.util.HashMap<>();
        matchList.clear();
        for (BankIndex.Group gr : idx.groups) {
            for (BankIndex.Page p : gr.pages()) {
                List<Integer> order = new ArrayList<>(p.slots().keySet());
                Collections.sort(order);
                for (int i : order) {
                    BankIndex.Item it = p.slots().get(i);
                    if (passes(it)) {
                        m.add(it);
                        counts.merge(p.key(), 1, Integer::sum);
                        matchList.add(new Object[] {it, p});
                    }
                }
            }
        }
        matched = m;
        perPage = counts;
        List<BankIndex.Item> st = new ArrayList<>();
        for (BankIndex.Item it : idx.stacks) {
            if (passes(it)) {
                st.add(it);
            }
        }
        Map<BankIndex.Item, Double> value = new IdentityHashMap<>();
        if (sort == SORT_VALUE) {
            st.forEach(it -> value.put(it, it.group() == null ? null : io.github.anilken.wynnlabs.market.Vault.value(it.group())));
        }
        java.util.Comparator<BankIndex.Item> byName = java.util.Comparator.comparing(it -> it.st().name().toLowerCase(java.util.Locale.ROOT));
        java.util.Comparator<BankIndex.Item> order = switch (sort) {
            case SORT_KIND -> java.util.Comparator.<BankIndex.Item>comparingInt(it -> it.kind().ordinal()).thenComparing(byName);
            case SORT_TIER -> java.util.Comparator.<BankIndex.Item>comparingInt(it -> it.tier() == null ? 99 : -it.tier().ordinal())
                    .thenComparing(byName);
            case SORT_VALUE -> java.util.Comparator.<BankIndex.Item>comparingDouble(it -> value.get(it) == null ? 1 : -value.get(it))
                    .thenComparing(byName);
            default -> byName;
        };
        st.sort(order);
        shownStacks = List.copyOf(st);
    }

    /** Whether an item passes the kind, the tier and the search. */
    boolean passes(BankIndex.Item it) {
        return (kind == null || it.kind() == kind) && (tier == null || it.tier() == tier) && (query.isEmpty() || it.hay().contains(query));
    }

    /** The matches for the bank now (worked out again only when it or the filter changed). */
    void refresh(BankIndex idx) {
        match(idx);
    }

    boolean matches(BankIndex.Item it) {
        return matched.contains(it);
    }

    /** The largest even slot (22 down to 14) that puts two pages of {@code cols} side by side in {@code w}; one if two never fit. */
    static int slotFor(int w, int cols) {
        for (int want = 2; want >= 1; want--) {
            for (int sz = 22; sz >= 14; sz -= 2) {
                int gw = cols * (sz + GAP) - GAP;
                if (want * gw + (want - 1) * Theme.SP_6 <= w) {
                    return sz;
                }
            }
        }
        return 14;
    }

    static int gridW(int cols, int sz) {
        return cols * (sz + GAP) - GAP;
    }

    static int gridH(int rows, int sz) {
        return rows * (sz + GAP) - GAP;
    }

    /** Draws the pages from {@code y} (scrolled) across {@code w}; returns the content's height. */
    int draw(MenuTab tab, GuiGraphics g, BankIndex idx, int x, int y, int w, int top, int bottom, int mx, int my) {
        Font font = tab.font();
        match(idx);
        slots.clear();
        tops.clear();
        boolean filtered = filtered();
        int sz = slotFor(w, idx.cols);
        int gw = gridW(idx.cols, sz);
        int per = Math.max(1, (w + Theme.SP_6) / (gw + Theme.SP_6));
        int headH = Type.LABEL.lineHeight + Theme.SP_2;
        int pageH = headH + gridH(idx.rows, sz);
        int cy = y;
        boolean first = true;
        for (BankIndex.Group gr : idx.groups) {
            List<BankIndex.Page> pages = new ArrayList<>();
            for (BankIndex.Page p : gr.pages()) {
                if (!filtered || perPage.getOrDefault(p.key(), 0) > 0) {
                    pages.add(p);                   // a search or a filter shows only the pages with a match
                }
            }
            if (!shown(gr.bank()) || pages.isEmpty()) {
                continue;
            }
            if (!first) {
                cy += Theme.SP_6;
            }
            first = false;
            // the bank's title and a hairline
            Type.LABEL.draw(g, font, Type.LABEL.fit(font, gr.title(), w), x, cy);
            Kit.hair(g, x, cy + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
            cy += Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
            if (fullFirst) {
                pages.sort((a, b) -> Integer.compare(b.used(), a.used()));
            }
            for (int k = 0; k < pages.size(); k++) {
                BankIndex.Page p = pages.get(k);
                int col = k % per;
                if (col == 0 && k > 0) {
                    cy += pageH + Theme.SP_6;
                }
                int px = x + col * (gw + Theme.SP_6);
                int count = perPage.getOrDefault(p.key(), 0);
                tops.put(p.key(), cy - y);
                if (cy + pageH <= top || cy >= bottom) {
                    continue;                       // out of view: nothing to draw
                }
                page(tab, g, font, idx, p, px, cy, gw, sz, count, filtered, top, bottom, mx, my);
            }
            cy += pageH;
        }
        return cy - y;
    }

    private void page(MenuTab tab, GuiGraphics g, Font font, BankIndex idx, BankIndex.Page p, int x, int y, int gw, int sz, int count,
                      boolean filtered, int top, int bottom, int mx, int my) {
        String name = p.name();
        boolean faint = !p.read();
        Type.LABEL.draw(g, font, name, x, y, faint ? Theme.INK_3 : Theme.INK);
        int nx = x + Type.LABEL.width(font, name) + Theme.SP_2;
        if (!p.read()) {
            Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.bank.not_read"), x + gw - nx), nx, y + 1, Theme.INK_3);
        } else if (filtered && count > 0) {
            Type.META.draw(g, font, String.valueOf(count), nx, y + 1, Theme.GOLD_EDGE);
        }
        if (p.read()) {
            // how full the page is: a short bar on the right, gold when nearly full
            double fill = p.used() / (double) Math.max(1, idx.cols * idx.rows);
            int by = Type.LABEL.onCaps(y, 3);
            int bx = x + gw - BAR_W;
            g.fill(bx, by, bx + BAR_W, by + 3, Theme.WELL);
            g.fill(bx, by, bx + Math.max(3, (int) Math.round(BAR_W * Math.min(1, fill))), by + 3, fill > 0.85 ? Theme.GOLD_DEEP : 0xFF8C7B58);
        }
        int gy = y + Type.LABEL.lineHeight + Theme.SP_2;
        if (!p.read()) {
            ghost(g, x, gy, idx.cols, idx.rows, sz);
            return;
        }
        for (int r = 0; r < idx.rows; r++) {
            for (int c = 0; c < idx.cols; c++) {
                int sx = x + c * (sz + GAP);
                int sy = gy + r * (sz + GAP);
                if (sy + sz <= top || sy >= bottom) {
                    continue;
                }
                BankIndex.Item it = p.slots().get(r * 9 + c);
                if (it != null && filtered && !matches(it)) {
                    it = null;                      // filtered out: an empty slot, so the places still say where
                }
                boolean hover = it != null && mx >= sx && mx < sx + sz && my >= sy && my < sy + sz && my >= top && my < bottom;
                slot(tab, g, font, sx, sy, sz, it, it != null && it == picked, hover);
                if (it != null) {
                    int vy = Math.max(sy, top);
                    int vh = Math.min(sy + sz, bottom) - vy;
                    slots.add(new Object[] {new int[] {sx, vy, sz, vh}, it});
                    BankTips.addSlotTip(tab, sx, vy, sz, vh, it, mx, my);
                }
            }
        }
    }

    /** One slot: the sunken square, the item at slot - 6 (3 px in), its count, a corner mark, "?", its tier's line, the gold edge. */
    static void slot(MenuTab tab, GuiGraphics g, Font font, int x, int y, int sz, BankIndex.Item it, boolean gold, boolean hover) {
        slot(tab, g, font, x, y, sz, it, it == null ? 0 : it.st().count(), gold, hover);
    }

    /** The same with the count to show ({@code n}: a stack's total in the one grid). */
    static void slot(MenuTab tab, GuiGraphics g, Font font, int x, int y, int sz, BankIndex.Item it, int n, boolean gold, boolean hover) {
        Kit.slot(g, x, y, sz, 0);
        if (it != null) {
            ItemStack stack = it.stack();
            if (DevHooks.ON && stack != null) {
                DevHooks.part("bankitem", g.pose(), x, y, sz, sz);
            }
            if (stack != null) {
                int isz = sz - 6;
                g.pose().pushMatrix();
                g.pose().translate(x + 3, y + 3);
                g.pose().scale(isz / 16f, isz / 16f);
                g.renderItem(stack, 0, 0);
                g.renderItemDecorations(font, stack, 0, 0, "");     // the bar, without the game's count
                g.pose().popMatrix();
            }
            if (it.tier() != null && it.tier() != Tier.NORMAL) {
                Kit.hair(g, x + 3, y + sz - 3, sz - 6, 1, HomePart.tierColour(it.tier()));     // its tier, quietly
            }
            String count = shortCount(n);
            if (count != null) {
                // in the slot's bottom right, over the item: a shadow keeps it readable
                g.drawString(font, Type.META.of(count), x + sz - 1 - Type.META.width(font, count), y + sz - Type.META.lineHeight + 1,
                        0xFFFFFFFF, true);
            }
            if (it.unknown()) {
                g.drawString(font, Type.META.of("?"), x + 2, y + 1, Theme.GOLD, true);
            }
            if (it.mark() != 0) {
                // a small triangle in the top-right corner, in the mark's colour (inside the round corner)
                for (int i = 0; i < 4; i++) {
                    g.fill(x + sz - 5 + i, y + 1 + i, x + sz - 1, y + 2 + i, it.mark());
                }
            }
        }
        if (gold) {
            Kit.slotRing(g, x, y, sz);
        }
        if (hover) {
            g.fill(x + 1, y + 1, x + sz - 1, y + sz - 1, 0x50FFFFFF);
        }
    }

    /** A page not read yet: its outline and grid only, very faint (never slots). */
    static void ghost(GuiGraphics g, int x, int y, int cols, int rows, int sz) {
        int gw = gridW(cols, sz);
        int gh = gridH(rows, sz);
        g.fill(x, y, x + gw, y + gh, 0x0A000000);
        Kit.outline(g, x, y, gw, gh, 0x30624E3C);
        for (int r = 1; r < rows; r++) {
            Kit.hair(g, x + Theme.SP_2, y + r * (sz + GAP) - GAP / 2, gw - 2 * Theme.SP_2, 1, 0x1C624E3C);
        }
        for (int c = 1; c < cols; c++) {
            Kit.hair(g, x + c * (sz + GAP) - GAP / 2, y + Theme.SP_2, 1, gh - 2 * Theme.SP_2, 0x1C624E3C);
        }
    }

    static String shortCount(int n) {
        // "1k" past 999: a slot has room for three digits
        return n <= 1 ? null : n < 1000 ? String.valueOf(n) : n / 1000 + "k";
    }
}
