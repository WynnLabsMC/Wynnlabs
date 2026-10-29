package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.FeederRead;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.MountBook;
import io.github.anilken.wynnlabs.mounts.MountMaterials;
import io.github.anilken.wynnlabs.mounts.MountStat;
import io.github.anilken.wynnlabs.mounts.MountStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A feed material's tooltip, anywhere: what it would give the mounts last seen in the Mount
 * Feeder ("Row 2 · Crimson: +9 Speed, +3 Acceleration"), the best two, after their queues.
 */
public final class MountTips {

    /** One mount the line may speak of: its feeder row, a short name and its stats after its queue. */
    record Fed(int row, String name, MountStats stats) {}

    private MountTips() {}

    /** The lines for this stack; empty when it is not a material or no mount was seen in a feeder. */
    public static List<Component> lines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        Material mat = MountMaterials.byName(FeederRead.clean(stack.getHoverName().getString()));
        if (mat == null) {
            return List.of();
        }
        List<Fed> mounts = new ArrayList<>();
        for (MountBook.Seen s : MountBook.all()) {
            if (s.fed() == null) {
                continue;
            }
            Mount m = MountsModel.withQueue(MountsModel.convert(s.mount()), s.fed());
            if (m != null) {
                String first = m.name().contains(" ") ? m.name().substring(0, m.name().indexOf(' ')) : m.name();
                mounts.add(new Fed(s.fed().row(), first, m.queue().applyTo(m.stats())));
            }
        }
        List<Component> out = new ArrayList<>();
        for (Line line : texts(mat, mounts, 2)) {
            out.add(Component.literal(L.t("wynnlabs.mounts.tip_gain", line.row(), line.name(), line.gains()))
                    .withStyle(ChatFormatting.GREEN));
        }
        return out;
    }

    /** One line: the row, the mount's short name, its gains ("+9 Speed, +3 Acceleration"). */
    record Line(int row, String name, String gains) {}

    /** One line a mount that gains anything, the biggest total gain first, at most {@code max}. */
    static List<Line> texts(Material mat, List<Fed> mounts, int max) {
        record Gain(Fed fed, int[] g, int total) {}
        List<Gain> gains = new ArrayList<>();
        for (Fed f : mounts) {
            int[] g = f.stats().gainsFrom(mat);
            int total = 0;
            for (int v : g) {
                total += v;
            }
            if (total > 0) {
                gains.add(new Gain(f, g, total));
            }
        }
        gains.sort((a, b) -> b.total() != a.total() ? Integer.compare(b.total(), a.total()) : Integer.compare(a.fed().row(), b.fed().row()));
        List<Line> out = new ArrayList<>();
        for (Gain x : gains.subList(0, Math.min(max, gains.size()))) {
            List<MountStat> up = new ArrayList<>();
            for (MountStat s : MountStat.values()) {
                if (x.g()[s.ordinal()] > 0) {
                    up.add(s);
                }
            }
            up.sort((a, b) -> Integer.compare(x.g()[b.ordinal()], x.g()[a.ordinal()]));     // the biggest gain first
            List<String> parts = new ArrayList<>();
            for (MountStat s : up) {
                parts.add("+" + x.g()[s.ordinal()] + " " + s.display());
            }
            out.add(new Line(x.fed().row(), x.fed().name(), String.join(", ", parts)));
        }
        return out;
    }
}
