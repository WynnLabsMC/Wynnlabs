package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.endgame.ItemIcons;
import io.github.anilken.wynnlabs.endgame.PoolView;
import io.github.anilken.wynnlabs.endgame.RaidPool;
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
 * One item row of a pool list (the pool drawer): the item's own picture (the game's model, the
 * pack's texture or a glyph, through {@link ItemIcons}), its name in its rarity colour, the marks
 * on the right, and its tooltip.
 */
final class EndgamePoolItems extends EndgamePanel {

    /** Every item picture in a pool list: 12 px, centred in its row with SP_2 of air between rows. */
    static final int ICON_SIZE = 12;

    EndgamePoolItems(EndgameTab tab) {
        super(tab);
    }

    /**
     * One item row: its icon, its name in its rarity colour, and on the right (drawn first, never
     * cut) the shiny tracker badge, the mark or ✔ of an own aspect, and "×9" for a folded amount.
     */
    void row(GuiGraphics g, PoolView.Row r, boolean dim, PoolView.Group.Kind kind, int x, int y, int w, int h, boolean compact,
            boolean raid, PoolView.Region region, int index, int mx, int my) {
        PoolItem item = r.item();
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        if (hover) {
            g.fill(x, y, x + w, y + h, Theme.CARD_HOVER);     // plain rows, the hovered one washed
        }
        int size = ICON_SIZE;
        ItemIcons.draw(g, item, item.aspectClass(), x + 2, y + (h - size) / 2, size, dim);
        int ty = y + (h - 8) / 2;
        int rx = x + w - 1;
        if (item.shiny() && !item.shinyStat().isEmpty()) {
            int bw = Kit.Ui.tagW(tab.font, item.shinyStat());
            Art.pill(g, rx - bw, ty - 1, bw, 10, Theme.WELL, 0);
            Kit.text(g, tab.font, item.shinyStat(), rx - bw + Kit.Ui.TAG_PAD, ty, Theme.INK_3);
            rx -= bw + 3;
        }
        if (r.ownClass()) {
            if (r.maxed()) {
                Art.icon(g, "check", rx - 8, ty, 8, Theme.GOOD);
                rx -= 11;
            } else {
                String tg = markText(r);
                if (tg != null) {
                    int tw = Kit.Ui.tagW(tab.font, tg);
                    tag(g, tg, rx - tw, ty - 1);
                    rx -= tw + 3;
                }
            }
        }
        if (r.count() > 1) {
            String n = "×" + r.count();
            right(g, n, rx, ty, Theme.INK_3);
            rx -= tab.font.width(n) + 3;
        }
        int nx = x + 2 + size + 3;
        int colour = tierColour(item.rarity());
        if (dim) {
            colour = colour & 0x00FFFFFF | 0x80000000;
        }
        ink(g, fit(itemName(item), rx - nx), nx, ty, colour);
        tab.rowRects.putIfAbsent(item.name() + (item.shiny() ? "*" : ""), new int[] {x, y, w, h});
        tab.tips.add(new Tip(x, y, w, h, itemTip(r, raid, region)));
    }

    /** "YENİ" or "+T3" for an own aspect's mark; null for none. */
    private static String markText(PoolView.Row r) {
        if (r.mark() == RaidPool.Mark.NEW) {
            return L.t("wynnlabs.endgame.tag_new");
        }
        if (r.mark() == RaidPool.Mark.TIER_UP && r.tagTier() > 0) {
            return L.t("wynnlabs.endgame.tag_tier_up", r.tagTier());
        }
        return null;
    }

    /** The item's name, with its tier as a numeral for a powder or an amplifier ("Fire Powder IV"). */
    private static String itemName(PoolItem i) {
        if (i.tier() <= 0 || i.aspect()) {
            return i.name();
        }
        String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        String n = i.tier() <= roman.length ? roman[i.tier() - 1] : String.valueOf(i.tier());
        return i.name().endsWith(" " + n) ? i.name() : i.name() + " " + n;
    }

    /** "Ekipman", "Shaman aspect'i", "Tome", "Rün" …; "" for a type the mod does not know. */
    private String kindName(PoolItem i) {
        if (i.aspect()) {
            return L.t("wynnlabs.endgame.kind_aspect", nice(i.aspectClass())).trim();
        }
        return switch (i.itemType()) {
            case "GearItem" -> L.t("wynnlabs.endgame.grp_gear");
            case "TomeItem" -> L.t("wynnlabs.endgame.kind_tome");
            case "RuneItem" -> L.t("wynnlabs.endgame.kind_rune");
            case "DungeonKeyItem" -> L.t("wynnlabs.endgame.kind_key");
            case "EmeraldItem" -> L.t("wynnlabs.endgame.kind_emerald");
            case "PowderItem" -> L.t("wynnlabs.endgame.kind_powder");
            case "AmplifierItem" -> L.t("wynnlabs.endgame.kind_amplifier");
            case "InsulatorItem" -> L.t("wynnlabs.endgame.kind_insulator");
            case "SimulatorItem" -> L.t("wynnlabs.endgame.kind_simulator");
            case "WardItem" -> L.t("wynnlabs.endgame.kind_ward");
            case "CharmItem" -> L.t("wynnlabs.endgame.kind_charm");
            default -> "";
        };
    }

    /**
     * An item row's tooltip: the name in its rarity colour; rarity · kind; the folded amount; the
     * shiny tracker; for an own aspect the owned tier, its mark and the other raids holding it;
     * then the region with its age and the source.
     */
    private List<Component> itemTip(PoolView.Row r, boolean raid, PoolView.Region region) {
        PoolItem i = r.item();
        List<Component> t = new ArrayList<>();
        t.add(Component.literal(itemName(i)).withStyle(tierChat(i.rarity())));
        String rarity = i.rarity() == Tier.NORMAL ? L.t("wynnlabs.endgame.grp_other") : rarityName(i.rarity());
        String kind = kindName(i);
        t.add(grey(rarity + (kind.isEmpty() ? "" : " · " + kind) + (i.shiny() ? " · " + L.t("wynnlabs.endgame.grp_shiny") : "")));
        if (r.count() > 1) {
            t.add(grey(L.t("wynnlabs.endgame.tip_amount", r.count())));
        }
        if (i.shiny()) {
            t.add(Component.literal(i.shinyStat().isEmpty() ? L.t("wynnlabs.endgame.grp_shiny")
                    : L.t("wynnlabs.endgame.tip_shiny", i.shinyStat())).withStyle(ChatFormatting.YELLOW));
        }
        if (r.ownClass()) {
            String owned = r.ownedTier() < 0 ? L.t("wynnlabs.endgame.tip_owned_unknown")
                    : r.ownedTier() == 0 ? L.t("wynnlabs.endgame.tip_owned_none")
                    : L.t("wynnlabs.endgame.tip_owned", r.ownedTier(), r.maxTier() > 0 ? String.valueOf(r.maxTier()) : "?");
            MutableComponent line = grey(owned).copy();
            String mk = markText(r);
            if (mk != null) {
                line.append(Component.literal(" · " + mk).withStyle(ChatFormatting.GREEN));
            } else if (r.maxed()) {
                line.append(Component.literal(" · " + L.t("wynnlabs.endgame.tier_max")).withStyle(ChatFormatting.GREEN));
            }
            t.add(line);
            List<String> also = new ArrayList<>(EndgameData.safe(RaidPool.raidsHolding(i.name())));
            also.remove(region.name());
            if (!also.isEmpty()) {
                t.add(grey(L.t("wynnlabs.endgame.tip_also", String.join(", ", also))));
            }
        }
        t.add(dark(EndgamePoolsView.regionName(region) + (region.seenAt() < 0 ? "" : " · " + agoLong(region.seenAt()))));
        if (tab.d.v().stale(raid)) {
            t.add(warn(L.t("wynnlabs.endgame.tip_week_stale")));
        }
        t.add(dark(tab.pools.sourceTip(raid)));
        return t;
    }
}
