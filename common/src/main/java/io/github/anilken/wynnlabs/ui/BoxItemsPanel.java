package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The box window's left list: "Possible items" and their count, then one row per item the box can
 * hold: its icon in an HD slot, its name in its rarity's colour (with "+N" when it adds to your
 * build), under it the level, how many listings there are and the best roll, or why there is no
 * number; the lowest price in a right column sized to the widest. The picked row is lit; the
 * hovered one shows a magnifier at its end that opens the item in Search. A click picks the row
 * whose listings the right side shows.
 */
final class BoxItemsPanel {

    /** A row: the name and its META line. */
    static final int TEXT_H = Type.BODY.lineHeight + Type.META.lineHeight;
    private static final int SLOT = 18;
    /** Row to row: the text, SP_2 inside a lit row's wash each side, SP_2 of plain page between washes. */
    private static final int PITCH = TEXT_H + 3 * Theme.SP_2;

    private final BoxScreen s;
    private final ScrollBar bar = new ScrollBar();
    private final SearchIcons icons = new SearchIcons();
    private final Map<String, ItemStack> fallback = new HashMap<>();
    private final Map<String, ItemStack> found = new HashMap<>();
    private int scroll;
    private int[] area = new int[4];

    BoxItemsPanel(BoxScreen s) {
        this.s = s;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        area = new int[] {x, y, w, h};
        List<BoxMarket.Row> rows = s.rows;
        // the head: what can come out, how many, over a hairline
        String head = L.t("wynnlabs.box.items_title");
        Type.LABEL.draw(g, s.font(), head, x, y, Theme.INK);
        Ui.seal(g, s.font(), String.valueOf(s.items.size()), x + Type.LABEL.width(s.font(), head) + Theme.SP_3,
                Type.LABEL.onCaps(y, Ui.SEAL_H), Theme.GOLD_DEEP);
        int hy = y + Type.LABEL.lineHeight + Theme.SP_1;
        Kit.hair(g, x, hy, w - ScrollBar.W - Theme.SP_2, 1, Theme.LINE);
        int top = hy + 1 + Theme.SP_3;
        int listH = y + h - top;
        int total = rows.size() * PITCH;
        scroll = Math.max(0, Math.min(ScrollBar.max(listH, total), scroll));
        int rx = x + Theme.SP_2;
        int rw = w - ScrollBar.W - Theme.SP_2 - 2 * Theme.SP_2;
        int priceW = 0;
        for (BoxMarket.Row r : rows) {
            priceW = Math.max(priceW, Type.BODY.width(s.font(), price(r)));
        }
        g.enableScissor(x, top - Theme.SP_1, x + w, top + listH);
        int ry = top + Theme.SP_2 - scroll;
        for (BoxMarket.Row r : rows) {
            if (ry + PITCH > top && ry - Theme.SP_2 < top + listH) {
                row(g, r, rx, ry, rw, priceW, mx, my, top, listH);
            }
            ry += PITCH;
        }
        g.disableScissor();
        bar.place(x + w - ScrollBar.W, top, listH, listH, total, scroll);
        bar.draw(g, mx, my);
    }

    private void row(GuiGraphics g, BoxMarket.Row r, int x, int y, int w, int priceW, int mx, int my, int top, int listH) {
        boolean chosen = r.name().equals(s.selected);
        boolean hover = BoxChips.in(mx, my, x - Theme.SP_2, Math.max(y - Theme.SP_2, top), w + 2 * Theme.SP_2, TEXT_H + 2 * Theme.SP_2)
                && my < top + listH;
        if (chosen) {
            Kit.rowLit(g, x - Theme.SP_2, y - Theme.SP_2, w + 2 * Theme.SP_2, TEXT_H + 2 * Theme.SP_2);
        } else if (hover) {
            Art.box(g, x - Theme.SP_2, y - Theme.SP_2, w + 2 * Theme.SP_2, TEXT_H + 2 * Theme.SP_2, 4, Theme.CARD_HOVER, 0);
        }
        int sy = y + (TEXT_H - SLOT) / 2;
        Kit.slot(g, x, sy, SLOT, 0);
        ItemStack icon = icon(r.item());
        g.renderItem(icon, x + 1, sy + 1);
        if (BoxChips.in(mx, my, x, sy, SLOT, SLOT)) {
            g.setTooltipForNextFrame(s.font(), icon, mx, my);
        }
        int nx = x + SLOT + Theme.SP_3;
        int right = x + w - priceW - Theme.SP_3;
        int gain = s.gainOf(r.name());
        int sealW = gain > 0 ? BoxChips.buildSealWidth(s.font(), gain, false) + Theme.SP_2 : 0;
        Type role = chosen ? Type.LABEL : Type.BODY;
        String name = role.fit(s.font(), r.name(), right - nx - sealW);
        role.draw(g, s.font(), name, nx, y, HomePart.tierColour(r.item().tier()));
        if (gain > 0) {
            BoxChips.buildSeal(g, s.font(), gain, false, nx + role.width(s.font(), name) + Theme.SP_2, Type.BODY.onCaps(y, Ui.SEAL_H));
        }
        Type.META.draw(g, s.font(), Type.META.fit(s.font(), detail(r), right - nx), nx, y + Type.BODY.lineHeight,
                r.state() == BoxMarket.State.READY ? Theme.INK_2 : Theme.INK_3);
        if (hover) {
            // the row's end: the item in Search, in place of its price while the mouse is on the row
            int bx = x + w - Ui.CHIP_H - 2;
            s.bar.searchButton(g, bx, y + (TEXT_H - Ui.CHIP_H) / 2, r.name(), mx, my);
        } else {
            String p = price(r);
            if (!p.isEmpty()) {
                Type.BODY.drawRight(g, s.font(), p, x + w, y, r.lowest() != null ? Theme.PRICE : Theme.INK_3);
            }
        }
        int ty = Math.max(y - Theme.SP_2, top);
        int bottom = Math.min(y + TEXT_H + Theme.SP_2, top + listH);
        if (bottom > ty) {
            s.hit(x - Theme.SP_2, ty, w + 2 * Theme.SP_2 - (hover ? Ui.CHIP_H + 2 : 0), bottom - ty, () -> {
                s.selected = r.name();
                s.right.reset();
            });
        }
        if (hover && gain > 0 && BoxChips.in(mx, my, nx + role.width(s.font(), name), y, sealW, Type.BODY.lineHeight)) {
            s.tip(List.of(L.c("wynnlabs.box.build_tip", gain).withStyle(ChatFormatting.GRAY)));
        }
        if (hover && r.bestRoll() != null && BoxChips.in(mx, my, nx, y, right - nx, TEXT_H)) {
            // what the META line leaves out: the best roll listed now
            s.tip(List.of(Component.literal(r.name()).withStyle(ChatFormatting.WHITE),
                    L.c("wynnlabs.box.best_roll", BoxScreen.pct(r.bestRoll())).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** The lowest price now, else an older one as "~x earlier", else nothing. */
    private static String price(BoxMarket.Row r) {
        if (!io.github.anilken.wynnlabs.market.Listings.live()) {
            return "";
        }
        return r.lowest() != null ? Prices.fmt(r.lowest()) : r.pastPrice() != null ? Prices.past("~" + Prices.fmt(r.pastPrice())) : "";
    }

    /** "Lv 72 · 7 listings · best 86%", or the state in words. */
    private static String detail(BoxMarket.Row r) {
        String lv = "Lv " + r.item().level();
        if (!io.github.anilken.wynnlabs.market.Listings.live()) {
            return lv;
        }
        return switch (r.state()) {
            case UNTRADABLE -> lv + " · " + L.t("wynnlabs.box.untradable");
            case LOADING -> lv + " · " + L.t("wynnlabs.box.loading");
            case UNKNOWN -> lv + " · " + L.t("wynnlabs.box.price_unknown");
            case NONE -> lv + " · " + L.t("wynnlabs.box.no_listings");
            case READY -> r.count() == 0 ? lv + " · " + L.t("wynnlabs.box.no_listings")
                    : lv + " · " + L.n("wynnlabs.box.listings", r.count());
        };
    }

    /** The item's real icon: the search index's (Wynntils' guide stack or its model), else its type's base item. */
    ItemStack icon(BoxMarket.Item it) {
        ItemStack known = found.get(it.name());
        if (known != null) {
            return known;
        }
        Entry e = ItemIndex.get().byName(it.name());
        if (e != null) {
            ItemStack st = icons.of(e);
            if (!st.isEmpty()) {
                found.put(it.name(), st);       // looked up once per item, not per frame
            }
            return st;
        }
        return fallback.computeIfAbsent(it.name(), n -> {
            if (GameState.current().running()) {
                try {
                    ItemStack guide = WynntilsItems.guideStack(Kind.GEAR, n);
                    if (guide != null && !guide.isEmpty()) {
                        return guide;
                    }
                } catch (LinkageError ignored) {
                    // Wynntils changed: the base item below
                }
            }
            return new ItemStack(switch (it.slot()) {
                case HELMET -> Items.LEATHER_HELMET;
                case CHESTPLATE -> Items.LEATHER_CHESTPLATE;
                case LEGGINGS -> Items.LEATHER_LEGGINGS;
                case BOOTS -> Items.LEATHER_BOOTS;
                case RING, BRACELET, NECKLACE, ACCESSORY -> Items.GOLD_NUGGET;
                case BOW -> Items.BOW;
                default -> Items.STICK;
            });
        });
    }

    boolean mouseClicked(double mx, double my) {
        if (bar.mouseClicked(mx, my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    boolean mouseDragged(double my) {
        if (bar.mouseDragged(my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    void mouseReleased() {
        bar.mouseReleased();
    }

    boolean mouseScrolled(double mx, double my, double v) {
        if (!BoxChips.in(mx, my, area[0], area[1], area[2], area[3])) {
            return false;
        }
        scroll -= (int) Math.signum(v) * PITCH;
        return true;
    }
}
