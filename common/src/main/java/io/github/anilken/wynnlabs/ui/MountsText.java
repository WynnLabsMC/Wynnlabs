package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.mounts.MountType;
import io.github.anilken.wynnlabs.mounts.Range;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** The Mounts tab's words and small pictures: times, ranges, emeralds and a mount type's icon. */
final class MountsText {

    private MountsText() {}

    /** "6 h", "2 h 14 min", "5 d 6 h", "20 min"; "—" for nothing. */
    static String dur(long sec) {
        if (sec <= 0) {
            return "—";
        }
        long min = (sec + 59) / 60;
        if (min < 60) {
            return L.t("wynnlabs.mounts.dur_m", min);
        }
        long h = min / 60;
        long m = min % 60;
        if (h < 24) {
            return m == 0 || h >= 10 ? L.t("wynnlabs.mounts.dur_h", h) : L.t("wynnlabs.mounts.dur_hm", h, m);
        }
        long d = h / 24;
        long rh = h % 24;
        return rh == 0 ? L.t("wynnlabs.mounts.dur_d", d) : L.t("wynnlabs.mounts.dur_dh", d, rh);
    }

    /** An estimate: always a range, "~620–700" ("~700" only when both ends agree). */
    static String range(Range r) {
        return r == null ? "—" : r.low() == r.high() ? "~" + r.low() : "~" + r.low() + "–" + r.high();
    }

    /** Emeralds, short: "40 E", "14 EB", "2.1 LE"; "—" when unknown. */
    static String em(double e) {
        return e < 0 ? "—" : Prices.fmt(e);
    }

    /** One decimal ("21.6"). */
    static String one(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Stand-ins by type, made on first use (tests load this class without the game's items). */
    private static ItemStack[] stacks;

    /** A stand-in item for a mount type (the reins' own model is the server's). */
    static ItemStack stack(MountType t) {
        if (stacks == null) {
            stacks = new ItemStack[] {new ItemStack(Items.SADDLE), new ItemStack(Items.DIAMOND_HORSE_ARMOR),
                new ItemStack(Items.TURTLE_SCUTE)};
        }
        return stacks[t.ordinal()];
    }

    /** The type's item at x, y, {@code size} square. */
    static void icon(GuiGraphics g, MountType t, int x, int y, int size) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.renderItem(stack(t), 0, 0);
        g.pose().popMatrix();
    }

    /** A material's item at x, y, {@code size} square (paper while the item index does not know it). */
    static void item(GuiGraphics g, String name, int x, int y, int size) {
        ItemStack s = CraftIcons.of(name);
        if (s == null) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(size / 16f, size / 16f);
        g.renderItem(s, 0, 0);
        g.pose().popMatrix();
    }
}
