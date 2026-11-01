package io.github.anilken.wynnlabs.advice;

import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One row of a short tooltip ({@link BoxTooltip}, {@link GearTooltip}). The rows are plain data,
 * so they are built and tested without the game; {@code ui/TooltipRows} turns them into the
 * game's tooltip parts.
 */
public sealed interface TipRow permits TipRow.Text, TipRow.Item, TipRow.Separator, TipRow.Header, TipRow.Band,
        TipRow.Id, TipRow.Chips, TipRow.Pair, TipRow.Centred {

    /** A text row: {@code left} at the start, {@code right} (may be null) at the far end. */
    record Text(Component left, Component right) implements TipRow {}

    /** A possible drop: its icon and name (in its rarity's colour), level and price ("?" without one). */
    record Item(ItemData data, int level, Component price) implements TipRow {}

    /** A thin line between the parts. */
    record Separator() implements TipRow {}

    /**
     * Two lines beside the stack's own icon: the name and, at the far end, the overall roll
     * (may be null); under them tier, type and level and, at the far end, the rerolls (may be null).
     */
    record Header(ItemStack icon, Component name, Component overall, Component sub, Component rerolls)
            implements TipRow {}

    /**
     * The verdict in a tinted box with a coloured left edge: the title, then the note on the same
     * line when it fits, else wrapped under it. {@code note} may be null.
     */
    record Band(Component title, String note, ChatFormatting colour) implements TipRow {}

    /**
     * One identification: the stat's name, its value (sign and unit) in a shared right-aligned
     * column, and the roll bar and % in {@code colour}. {@code percent} is null for a stat that
     * cannot roll (one value only): no bar then.
     */
    record Id(Component name, Component value, Double percent, ChatFormatting colour) implements TipRow {}

    /** Requirement chips in one row, wrapped to more when they do not fit. */
    record Chips(List<Chip> chips) implements TipRow {}

    /** One requirement chip: its text and whether it is met (null: not known). */
    record Chip(String text, Boolean met) {}

    /**
     * {@code left} at the start and {@code right} at the far end, either may be null; when the two
     * do not fit side by side, {@code right} goes on a line of its own under it, never over it.
     */
    record Pair(Component left, Component right) implements TipRow {}

    /** One line in the middle of the tooltip. */
    record Centred(Component text) implements TipRow {}
}
