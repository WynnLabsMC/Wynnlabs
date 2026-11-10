package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Shopping;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.UseList;
import io.github.anilken.wynnlabs.search.Uses;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What the Uses view reads of the player: how many of a material or an ingredient they hold (the
 * bag and the bank pages they can open, every tier together), their profession levels and the
 * prices already kept. Reads only; the counts are kept for a moment so a list of rows is one read.
 */
final class UseStock {

    /** For the no-game preview: {bag, bank} by item name (null: the game's own). */
    static Function<String, int[]> preview;

    private static Map<String, int[]> held;
    private static long heldAt;

    private UseStock() {}

    /** The uses of an item by its name: the index's entry when it has one (an ingredient's professions), else the name alone. */
    static Uses.Result uses(String item) {
        Entry e = ItemIndex.get().byName(item);
        return e != null ? Uses.of(e) : Uses.of(item);
    }

    /** The lines of an item's uses with what the player holds and their profession levels. */
    static List<UseList.Line> lines(String item) {
        return UseList.of(uses(item), UseStock::total, UseStock::level);
    }

    /** {in the bag, in the bank} of an item by name. */
    static int[] of(String name) {
        if (preview != null) {
            int[] p = preview.apply(name);
            return p == null ? new int[2] : p;
        }
        long now = System.currentTimeMillis();
        if (held == null || now - heldAt > 2000) {
            try {
                held = Crafting.held();
            } catch (RuntimeException e) {
                held = Map.of();                    // no bag or bank read yet
            }
            heldAt = now;
        }
        int[] h = held.get(Crafting.heldKey(name));
        return h == null ? new int[2] : h;
    }

    static long total(String name) {
        int[] h = of(name);
        return (long) h[0] + h[1];
    }

    /** The player's level in a profession ("jeweling"), 0 when not known. */
    static int level(String profession) {
        return Math.max(0, Professions.level(profession));
    }

    /** A material's tier 1 price from the prices already kept, by its shown name; null when there is none. */
    static Double price(String shown) {
        Prices.Price p = Prices.of(Shopping.materialKey(shown, 1));
        return p == null ? null : p.typical();
    }

    /** "You have 24 · bag 16, bank 8", "You have 26 · bank", "You don't have any". */
    static String text(int[] h) {
        int n = h[0] + h[1];
        if (n <= 0) {
            return L.t("wynnlabs.search.uses_have_none");
        }
        if (h[0] > 0 && h[1] > 0) {
            return L.t("wynnlabs.search.uses_have_both", n, h[0], h[1]);
        }
        return L.t(h[0] > 0 ? "wynnlabs.search.uses_have_bag" : "wynnlabs.search.uses_have_bank", n);
    }
}
