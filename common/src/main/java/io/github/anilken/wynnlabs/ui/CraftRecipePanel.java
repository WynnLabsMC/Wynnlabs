package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.Shopping;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import io.github.anilken.wynnlabs.ui.CraftingTab.Drop;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.github.anilken.wynnlabs.ui.CraftFormat.dec;
import static io.github.anilken.wynnlabs.ui.CraftFormat.pct;
import static io.github.anilken.wynnlabs.ui.CraftFormat.range;

/**
 * The recipe: its two materials (the count owned at the chosen tier against the count needed, and
 * the tier chip), then the 2×3 grid of HD slots, each with the ingredient's model, its name, its
 * stars, what it adds to the goal there and the slot's effectiveness; the link to the shopping list
 * under it. A slot opens the ingredient picker; a right click empties it.
 */
final class CraftRecipePanel extends CraftPanel {

    private static final String[] POS_KEYS = {"left", "right", "above", "under", "touching", "not_touching"};

    private String shopKey = "";
    private Shopping.Needs shop;

    CraftRecipePanel(CraftingTab tab) {
        super(tab);
    }

    /** Draws the recipe from y across w; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        CraftData.Recipe r = tab.recipe();
        int cy = y;
        String mult = L.t("wynnlabs.craft.multiplier", dec(Craft.weightPercent(r, tab.tier1, tab.tier2) / 100.0, 2));
        cy = head(g, L.t("wynnlabs.craft.recipe"), mult, x, cy, w);
        for (int m = 0; m < 2; m++) {
            cy = material(g, r, m, x, cy, w, mx, my) + Theme.SP_2;
        }
        cy += Theme.SP_3 - Theme.SP_2;
        cy = grid(g, x, cy, w, mx, my) + Theme.SP_3;
        cy = shopLink(g, r, x, cy, w, mx, my);
        return cy - y;
    }

    /** A section title in LABEL with a META note and the hairline under it; returns the y under it. */
    int head(GuiGraphics g, String title, String meta, int x, int y, int w) {
        Type.LABEL.draw(g, tab.font, title, x, y);
        if (meta != null) {
            int mx = x + Type.LABEL.width(tab.font, title) + Theme.SP_3;
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, meta, x + w - mx), mx, y + 1, Theme.INK_3);
        }
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    /** One material row: its model, "12× Starfish Oil", the count owned / needed and the tier chip. */
    private int material(GuiGraphics g, CraftData.Recipe r, int m, int x, int y, int w, int mx, int my) {
        CraftData.Material mat = r.materials().get(m);
        int tier = m == 0 ? tab.tier1 : tab.tier2;
        int rowH = 18;
        CraftIcons.slot(g, x, y, rowH, CraftIcons.of(mat.shown()));
        int ty = Type.BODY.yIn(y, rowH);
        int sealW = UiTierSeal.width(Type.LABEL.width(tab.font, String.valueOf(tier)));
        int sx = x + w - sealW;
        int sy = y + (rowH - UiTierSeal.H) / 2;
        Drop d = m == 0 ? Drop.TIER1 : Drop.TIER2;
        UiTierSeal.draw(g, tab.font, tier, sx, sy, tab.open == d, false, mx, my);
        int[] anchor = {sx, sy, sealW, UiTierSeal.H};
        clipped(sx, sy, sealW, UiTierSeal.H, () -> tab.toggle(d, anchor), null,
                List.of(L.c("wynnlabs.craft.tier_tip", mat.shown(), tier), L.c("wynnlabs.craft.tier_weights")));
        int have = tab.stock.material(mat.shown(), tier);
        String count = have + " / " + mat.amount();
        int cw = Type.META.width(tab.font, count);
        Type.META.draw(g, tab.font, count, sx - Theme.SP_4 - cw, ty + 1, have >= mat.amount() ? Theme.GOOD : Theme.WARN);
        int nx = x + rowH + Theme.SP_3;
        String name = mat.amount() + "× " + mat.shown();
        Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, name, sx - Theme.SP_4 - cw - Theme.SP_3 - nx), nx, ty);
        return y + rowH;
    }

    /** The 2×3 grid; returns the y under it. */
    private int grid(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<Ingredient> lay = tab.layout();
        List<Ingredient> shown = tab.preview != null ? tab.preview : lay;
        int[] eff = Craft.effectiveness(shown);
        int[] base = Craft.effectiveness(lay);
        int[][] give = gives(shown);
        int lv = tab.level(tab.recipe().profession());
        String main = tab.weights.isEmpty() ? null : tab.weights.keySet().iterator().next();
        int size = tab.twoColumns ? 30 : 28;
        int cellW = (w - Theme.SP_4) / 2;
        for (int s = 0; s < Craft.SLOTS; s++) {
            int cx = x + (s % 2) * (cellW + Theme.SP_4);
            int cy = y + (s / 2) * (size + Theme.SP_3);
            Ingredient in = shown.get(s);
            boolean picking = tab.drawers.pickingSlot() == s;
            boolean changed = tab.preview != null && (eff[s] != base[s] || !in.name().equals(lay.get(s).name()));
            boolean hover = in(mx, my, cx, cy, cellW, size);
            CraftIcons.slot(g, cx, cy, size, in.empty() ? null : CraftIcons.of(in.name()));
            if (hover && !in.empty()) {
                g.fill(cx + 1, cy + 1, cx + size - 1, cy + size - 1, 0x40FFFFFF);
            }
            if (picking || changed) {
                Kit.slotRing(g, cx, cy, size);
            }
            int tx = cx + size + Theme.SP_3;
            int tw = cx + cellW - tx;
            int ty = cy + (size - Type.BODY.lineHeight - Type.META.lineHeight) / 2;
            String e = pct(eff[s]);
            int ew = Type.META.width(tab.font, e);
            int ec = eff[s] > 100 ? Theme.GOOD : eff[s] < 100 ? Theme.BAD : Theme.INK_3;
            if (in.empty()) {
                String unknown = tab.slots[s] != null && tab.preview == null ? "? " + tab.slots[s] : null;
                Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, unknown != null ? unknown : L.t("wynnlabs.craft.add"), tw), tx, ty,
                        unknown != null ? Theme.BAD : Theme.INK_3);
                Type.META.drawRight(g, tab.font, e, cx + cellW, ty + Type.BODY.lineHeight, ec);
            } else {
                boolean locked = in.level() > lv;
                int nx = tx;
                if (locked) {
                    Art.icon(g, "lock", nx, Type.BODY.onCaps(ty, 8), 8, Theme.BAD);
                    nx += 8 + Theme.SP_1;
                }
                Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, in.name(), cx + cellW - nx), nx, ty, locked ? Theme.INK_3 : Theme.INK);
                int my2 = ty + Type.BODY.lineHeight;
                for (int k = 0; k < in.tier(); k++) {
                    Art.icon(g, "star-filled", tx + k * 6, Type.META.onCaps(my2, 5), 5, Theme.GOLD_DEEP);
                }
                int vx = tx + Math.max(1, in.tier()) * 6 + Theme.SP_2;
                Ingredient.Stat st = main == null ? null : in.ids().get(main);
                if (st != null) {
                    Craft.Range v = Craft.scaled(st, eff[s]);
                    String val = range(v, tab.unit(main));
                    Type.META.draw(g, tab.font, Type.META.fit(tab.font, val, cx + cellW - ew - Theme.SP_2 - vx), vx, my2,
                            v.max() < 0 ? Theme.BAD : Theme.GOOD);
                }
                Type.META.drawRight(g, tab.font, e, cx + cellW, my2, ec);
                // how many are owned, in the slot's corner over the item (a shadow keeps it readable); "al" when none
                int own = tab.owned(in.name());
                String c = own > 0 ? String.valueOf(own) : L.t("wynnlabs.craft.buy_short");
                g.drawString(tab.font, Type.META.of(c), cx + size - 2 - Type.META.width(tab.font, c), cy + size - Type.META.lineHeight,
                        own > 0 ? 0xFFFFFFFF : Theme.GOLD, true);
            }
            int slot = s;
            List<Component> tip = new ArrayList<>(breakdown(shown, give, s));
            if (in.empty()) {
                tip.add(L.c("wynnlabs.craft.slot_tip", pct(eff[s])));
            } else {
                tip.addAll(card(in));
            }
            clipped(cx, cy, cellW, size, () -> tab.drawers.openPicker(slot), () -> tab.slots[slot] = null, tip);
        }
        return y + 3 * size + 2 * Theme.SP_3;
    }

    /** "Eksik 3 malzeme · ~1,2 LE" (or "Alışveriş listesi" when nothing is missing) as a chip: opens the shopping drawer. */
    private int shopLink(GuiGraphics g, CraftData.Recipe r, int x, int y, int w, int mx, int my) {
        Shopping.Needs n = needs(r);
        int missing = n.lines().size();
        long cost = n.cost();
        // a drawer opens: a chip with the cart, the missing count as its gold seal
        String t = missing == 0 ? L.t("wynnlabs.craft.shop_link_none")
                : L.n("wynnlabs.craft.shop_link", missing) + (cost > 0 ? " · ~" + Prices.fmt(cost) : "");
        int cw = Math.min(w, Ui.iconChipWidth(tab.font, "shopping-cart", t, false));
        Ui.iconChip(g, tab.font, "shopping-cart", Type.BODY.fit(tab.font, t, cw - 2 * Theme.SP_4 - 8 - Theme.SP_1), false, x, y, cw, false, mx, my);
        clipped(x, y, cw, Ui.CHIP_H, () -> tab.drawers.openShop(), null, null);
        return y + Ui.CHIP_H;
    }

    /** What the recipe still needs: worked out again only when the recipe, the stock or the prices change. */
    Shopping.Needs needs(CraftData.Recipe r) {
        String k = tab.key() + Arrays.toString(tab.slots) + System.identityHashCode(tab.stock) + System.identityHashCode(tab.prices);
        if (!k.equals(shopKey) || shop == null) {
            shopKey = k;
            shop = Shopping.of(r, tab.tier1, tab.tier2, tab.layout(), tab.stock, tab.prices::get);
            tab.wantedPrices.addAll(shop.unpriced());
        }
        return shop;
    }

    /**
     * What each meta gives each slot in this layout, in effectiveness points: {@code give[from][to]},
     * 0 where it changes nothing. A meta's own slot is 0.
     */
    private static int[][] gives(List<Ingredient> lay) {
        int[][] give = new int[Craft.SLOTS][Craft.SLOTS];
        for (int from = 0; from < Craft.SLOTS; from++) {
            Ingredient m = lay.get(from);
            if (m.empty() || !m.meta()) {
                continue;
            }
            List<Ingredient> alone = new ArrayList<>(Craft.slots());
            alone.set(from, m);
            int[] only = Craft.effectiveness(alone);
            for (int to = 0; to < Craft.SLOTS; to++) {
                give[from][to] = to == from ? 0 : only[to] - 100;
            }
        }
        return give;
    }

    /** A meta's effects that reach no slot from where it sits (left of the left column …), as position indices. */
    private static List<Integer> wasted(Ingredient m, int slot) {
        List<Integer> out = new ArrayList<>();
        int row = slot / 2;
        int col = slot % 2;
        int[] p = m.positions();
        if (p[Ingredient.LEFT] != 0 && col == 0) {
            out.add(Ingredient.LEFT);
        }
        if (p[Ingredient.RIGHT] != 0 && col == 1) {
            out.add(Ingredient.RIGHT);
        }
        if (p[Ingredient.ABOVE] != 0 && row == 0) {
            out.add(Ingredient.ABOVE);
        }
        if (p[Ingredient.UNDER] != 0 && row == 2) {
            out.add(Ingredient.UNDER);
        }
        return out;
    }

    /** The tooltip's first lines for a slot: how its % adds up, and for a meta what it gives and wastes. */
    private List<Component> breakdown(List<Ingredient> lay, int[][] give, int s) {
        List<Component> out = new ArrayList<>();
        int e = Craft.effectiveness(lay)[s];
        StringBuilder sb = new StringBuilder("100");
        boolean any = false;
        for (int from = 0; from < Craft.SLOTS; from++) {
            int d = give[from][s];
            if (d != 0) {
                any = true;
                sb.append(' ').append(d > 0 ? "+" : "−").append(Math.abs(d)).append(' ').append(lay.get(from).name());
            }
        }
        if (any) {
            out.add(L.c("wynnlabs.craft.slot_sum", pct(e), sb.toString()).withStyle(ChatFormatting.GRAY));
        }
        Ingredient m = lay.get(s);
        if (m.meta()) {
            List<String> parts = new ArrayList<>();
            for (int to = 0; to < Craft.SLOTS; to++) {
                if (give[s][to] != 0) {
                    parts.add((give[s][to] > 0 ? "+" : "−") + Math.abs(give[s][to]) + " → " + (to + 1));
                }
            }
            if (!parts.isEmpty()) {
                out.add(L.c("wynnlabs.craft.slot_gives", String.join(", ", parts)).withStyle(ChatFormatting.GRAY));
            }
            for (int p : wasted(m, s)) {
                out.add(L.c("wynnlabs.craft.slot_wasted", L.t("wynnlabs.craft.pos." + POS_KEYS[p],
                        L.pct((m.positions()[p] > 0 ? "+" : "−") + Math.abs(m.positions()[p]))))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return out;
    }
}
