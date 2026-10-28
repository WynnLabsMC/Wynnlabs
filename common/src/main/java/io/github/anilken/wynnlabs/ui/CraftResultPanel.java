package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftChecks;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Rolls;
import io.github.anilken.wynnlabs.crafting.Shopping;
import io.github.anilken.wynnlabs.crafting.StockView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.anilken.wynnlabs.ui.CraftFormat.costLine;
import static io.github.anilken.wynnlabs.ui.CraftFormat.costText;
import static io.github.anilken.wynnlabs.ui.CraftFormat.dec;
import static io.github.anilken.wynnlabs.ui.CraftFormat.pctText;
import static io.github.anilken.wynnlabs.ui.CraftFormat.range;
import static io.github.anilken.wynnlabs.ui.CraftFormat.signed;
import static io.github.anilken.wynnlabs.ui.CraftFormat.skill;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * The crafted item on a soft card: its type and level with damage or health, seals for whether it
 * can be worn and crafted and for its skill requirements, the identifications (a hover gives the
 * roll chances), and at the foot durability and powder slots (or duration and charges) and the cost.
 */
final class CraftResultPanel extends CraftPanel {

    /** Identification rows the card shows; more scroll inside it. */
    private static final int ID_ROWS = 8;

    int idScroll;
    /** The identifications' own bar, when more than {@link #ID_ROWS} roll. */
    final ScrollBar idBar = new ScrollBar();
    int idMax;
    int[] cardRect;

    CraftResultPanel(CraftingTab tab) {
        super(tab);
    }

    /** Draws the card from y across w; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        CraftData.Recipe r = tab.recipe();
        Craft.Result c = Craft.craft(r, tab.tier1, tab.tier2, tab.speed, tab.preview != null ? tab.preview : tab.layout());
        List<CraftChecks.Gap> gaps = r.consumable() ? List.of() : CraftChecks.gaps(c.requirements(), tab.points);
        List<StockView.Short> shortMats = StockView.missing(r, tab.tier1, tab.tier2, tab.stock);
        int ids = c.ids().size();
        int rows = Math.max(1, Math.min(ID_ROWS, ids));
        int pad = Theme.SP_4;
        int lineH = Type.BODY.lineHeight + Theme.SP_1;
        int h = 2 * pad + 24 + Theme.SP_3 + Ui.SEAL_H + Theme.SP_3 + rows * lineH + Theme.SP_2 + 1 + Theme.SP_3 + Type.META.lineHeight;
        Kit.softCard(g, x, y, w, h);
        cardRect = new int[] {x, y, w, h};
        int ix = x + pad;
        int iw = w - 2 * pad;
        int cy = y + pad;
        // the item, its name and its main line
        CraftIcons.typeSlot(g, r.type(), ix, cy, 24, Theme.INK_2);
        int tx = ix + 24 + Theme.SP_3;
        Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, L.t("wynnlabs.craft.crafted", typeName(r.type())), ix + iw - tx), tx, cy,
                Theme.TIER_LEGENDARY);
        String main = c.damage() != null ? L.t("wynnlabs.craft.damage", range(c.damageLow(), ""), range(c.damageHigh(), ""), r.bracket())
                : c.health() != null ? L.t("wynnlabs.craft.health", range(c.health(), ""), r.bracket())
                : L.t("wynnlabs.craft.level_only", r.bracket());
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, main, ix + iw - tx), tx, cy + Type.LABEL.lineHeight, Theme.INK_2);
        cy += 24 + Theme.SP_3;
        // seals: wear, craft, then each skill requirement
        int sx = ix;
        String wear = r.consumable() ? L.t("wynnlabs.craft.can_use") : gaps.isEmpty() ? L.t("wynnlabs.craft.can_wear")
                : L.t("wynnlabs.craft.cannot_wear", skill(gaps.get(0).skill()), gaps.get(0).missing());
        sx += seal(g, wear, sx, cy, gaps.isEmpty() ? Theme.GOOD : Theme.BAD, gapTip(gaps)) + Theme.SP_2;
        String craft = craftMark(r, shortMats);
        sx += seal(g, craft == null ? L.t("wynnlabs.craft.can_craft") : craft, sx, cy, craft == null ? Theme.GOOD : Theme.WARN,
                shortTip(shortMats)) + Theme.SP_2;
        for (int s = 0; s < 5; s++) {
            int need = c.requirements()[s];
            if (need <= 0) {
                continue;
            }
            String t = skill(s) + " " + need;
            if (sx + Type.META.width(tab.font, t) + 2 * Theme.SP_2 > ix + iw) {
                break;
            }
            boolean met = tab.points[s] >= need;
            sx += seal(g, t, sx, cy, met ? Theme.GOOD : Theme.BAD,
                    List.of(L.c("wynnlabs.craft.gap", skill(s), need, tab.points[s]))) + Theme.SP_2;
        }
        cy += Ui.SEAL_H + Theme.SP_3;
        // the identifications: name on the left, the range on the right; a hover gives the chances
        int idsTop = cy;
        idMax = Math.max(0, ids - ID_ROWS);
        idScroll = Math.min(idScroll, idMax);
        int barW = ids > ID_ROWS ? ScrollBar.W + Theme.SP_2 : 0;
        int index = 0;
        for (Map.Entry<String, Craft.Range> e : c.ids().entrySet()) {
            if (index++ < idScroll || index > idScroll + ID_ROWS) {
                continue;
            }
            String id = e.getKey();
            Craft.Range v = e.getValue();
            String val = range(v, tab.unit(id));
            int vw = Type.NUMBER.width(tab.font, val);
            int right = ix + iw - barW;
            Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, tab.statName(id), right - vw - Theme.SP_3 - ix), ix, cy, Theme.INK_2);
            Type.NUMBER.drawRight(g, tab.font, val, right, cy, v.max() < 0 ? Theme.BAD : Theme.GOOD);
            clipped(ix, cy, iw - barW, Type.BODY.lineHeight, null, null, chances(id));
            cy += lineH;
        }
        if (ids == 0) {
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.craft.no_ids"), ix, cy, Theme.INK_3);
        }
        if (ids > ID_ROWS) {
            idBar.place(ix + iw - ScrollBar.W, idsTop, ID_ROWS * lineH, ID_ROWS, ids, idScroll);
            idBar.draw(g, mx, my);
        }
        cy = idsTop + rows * lineH + Theme.SP_2;
        Kit.hair(g, ix, cy, iw, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        // the foot: durability (or duration), then the cost and how many can be made
        String foot;
        boolean bad = false;
        if (r.consumable()) {
            foot = L.n("wynnlabs.craft.consumable_foot", c.charges(), range(c.duration(), ""), c.charges());
        } else {
            bad = c.durability().min() <= 0;
            foot = (c.powderSlots() > 0 ? L.t("wynnlabs.craft.powder", c.powderSlots()) + " · " : "")
                    + L.t("wynnlabs.craft.durability", range(c.durability(), ""));
        }
        Shopping.Needs n = tab.recipePanel.needs(r);
        boolean buying = n.lines().stream().anyMatch(l -> !l.material());
        boolean priced = n.lines().stream().anyMatch(l -> !l.material() && l.cost() != null);
        String cost = (!buying ? L.t("wynnlabs.craft.cost_none") : costLine(costText(n.cost(), priced, !n.unpriced().isEmpty())))
                + " · " + L.t("wynnlabs.craft.times", craftableTimes(r));
        int cw = Type.META.width(tab.font, cost);
        Type.META.drawRight(g, tab.font, cost, ix + iw, cy, Theme.PRICE);
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, foot, iw - cw - Theme.SP_4), ix, cy, bad ? Theme.BAD : Theme.INK_2);
        if (bad) {
            clipped(ix, cy, iw - cw, Type.META.lineHeight, null, null, List.of(L.c("wynnlabs.craft.durability_low")));
        }
        return h;
    }

    private int seal(GuiGraphics g, String text, int x, int y, int colour, List<Component> tip) {
        int w = Ui.seal(g, tab.font, text, x, y, colour);
        if (tip != null && !tip.isEmpty()) {
            clipped(x, y, w, Ui.SEAL_H, null, null, tip);
        }
        return w;
    }

    /** Why it cannot be crafted now (the level, a material, an ingredient), or null when it can. */
    private String craftMark(CraftData.Recipe r, List<StockView.Short> shortMats) {
        if (r.minLevel() > tab.level(r.profession())) {
            return L.t("wynnlabs.craft.locked_short", r.minLevel());
        }
        if (!shortMats.isEmpty()) {
            return L.t("wynnlabs.craft.cannot_craft", shortMats.get(0).missing(), shortMats.get(0).material());
        }
        Map<String, Integer> need = new HashMap<>();
        for (String s : tab.slots) {
            if (s != null) {
                need.merge(s, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            if (tab.owned(e.getKey()) < e.getValue()) {
                return L.t("wynnlabs.craft.cannot_craft", e.getValue() - tab.owned(e.getKey()), e.getKey());
            }
        }
        return null;
    }

    private static List<Component> gapTip(List<CraftChecks.Gap> gaps) {
        List<Component> out = new ArrayList<>();
        for (CraftChecks.Gap gap : gaps) {
            out.add(L.c("wynnlabs.craft.gap", skill(gap.skill()), gap.need(), gap.have()));
        }
        return out;
    }

    private static List<Component> shortTip(List<StockView.Short> shortMats) {
        List<Component> out = new ArrayList<>();
        for (StockView.Short s : shortMats) {
            out.add(L.c("wynnlabs.craft.short_material", s.missing(), s.material(), s.tier()));
        }
        return out;
    }

    /** An identification's roll chances: its mean, the top value and the chance to hit it. */
    private List<Component> chances(String id) {
        Rolls.Dist d = Rolls.of(id, tab.layout());
        String u = tab.unit(id);
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(tab.statName(id)).withStyle(ChatFormatting.WHITE));
        out.add(L.c("wynnlabs.craft.roll_line", L.unit(dec(d.mean(), 2), u), L.unit(signed(d.high()), u), pctText(d.exactly(d.high())))
                .withStyle(ChatFormatting.GRAY));
        return out;
    }

    /** How many times the recipe can be made from the stock as laid out now. */
    private int craftableTimes(CraftData.Recipe r) {
        int n = Integer.MAX_VALUE;
        int[] tiers = {tab.tier1, tab.tier2};
        for (int i = 0; i < 2; i++) {
            CraftData.Material m = r.materials().get(i);
            n = Math.min(n, tab.stock.material(m.shown(), tiers[i]) / m.amount());
        }
        Map<String, Integer> need = new HashMap<>();
        for (String s : tab.slots) {
            if (s != null) {
                need.merge(s, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : need.entrySet()) {
            n = Math.min(n, tab.owned(e.getKey()) / e.getValue());
        }
        return n == Integer.MAX_VALUE ? 0 : n;
    }
}
