package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.BoxTooltip;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.mixin.GuiGraphicsAccessor;
import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The game's tooltip parts for {@link BoxTooltip}'s rows: a text row with a part at each end, an
 * item row (16 px icon, name, level, price at the far end) and a thin line. Everything is drawn
 * in {@code renderImage}, which the game hands the whole tooltip's width, so the right-hand parts
 * line up at its edge.
 */
public final class TooltipRows {

    private TooltipRows() {}

    /** Space between the left part of a row and its right part. */
    private static final int GAP = 12;
    /** Icon width plus a pixel of air before the item's name. */
    private static final int ICON = 18;
    /** Space between the item's name and its level. */
    private static final int COL_GAP = 6;
    private static final int LINE = 0xFF505050;

    /** The rows as tooltip parts; the item rows share one name and one level column. */
    public static List<ClientTooltipComponent> of(List<BoxTooltip.Row> rows, Font font) {
        int nameW = 0;
        int levelW = 0;
        for (BoxTooltip.Row r : rows) {
            if (r instanceof BoxTooltip.Item it) {
                nameW = Math.max(nameW, font.width(name(it.data())));
                levelW = Math.max(levelW, font.width(level(it.level())));
            }
        }
        int[] columns = {nameW, levelW};
        List<ClientTooltipComponent> out = new ArrayList<>(rows.size());
        for (BoxTooltip.Row r : rows) {
            if (r instanceof BoxTooltip.Text t) {
                out.add(new TextRow(t.left(), t.right()));
            } else if (r instanceof BoxTooltip.Item it) {
                out.add(new ItemRow(it, columns));
            } else {
                out.add(new LineRow());
            }
        }
        return out;
    }

    /**
     * Shows {@code rows} as this frame's tooltip at the mouse, as the game does with an item's
     * own tooltip: kept for the end of the frame and drawn there in a layer above the screen,
     * with the game's background and positioner (and so the menu scale and the tooltip size
     * setting). A tooltip already waiting is left in place, as the game leaves it.
     */
    public static void show(GuiGraphics g, Font font, List<ClientTooltipComponent> rows, int x, int y) {
        var deferred = (GuiGraphicsAccessor) g;
        if (deferred.wynnlabs$deferred() == null) {
            deferred.wynnlabs$setDeferred(() -> g.renderTooltip(font, rows, x, y, DefaultTooltipPositioner.INSTANCE, null));
        }
    }

    private static Component name(ItemData data) {
        return Component.literal(data.name()).withStyle(data.tier().colour());
    }

    private static Component level(int level) {
        return Component.literal(String.valueOf(level)).withStyle(ChatFormatting.DARK_GRAY);
    }

    /** Text at the start, and optionally text at the far end. */
    private record TextRow(Component left, Component right) implements ClientTooltipComponent {

        @Override
        public int getHeight(Font font) {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            return font.width(left) + (right == null ? 0 : GAP + font.width(right));
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            g.drawString(font, left, x, y, -1, true);
            if (right != null) {
                g.drawString(font, right, x + width - font.width(right), y, -1, true);
            }
        }
    }

    /** A possible drop: icon, name, level, and the price at the far end. */
    private static final class ItemRow implements ClientTooltipComponent {

        private final BoxTooltip.Item item;
        private final int[] columns;
        private final Component name;
        private final Component level;
        /** The icon, looked up on the first draw; EMPTY when it could not be. */
        private ItemStack icon;

        ItemRow(BoxTooltip.Item item, int[] columns) {
            this.item = item;
            this.columns = columns;
            this.name = name(item.data());
            this.level = level(item.level());
        }

        @Override
        public int getHeight(Font font) {
            return 18;
        }

        @Override
        public int getWidth(Font font) {
            return ICON + columns[0] + COL_GAP + columns[1] + GAP + font.width(item.price());
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            if (icon == null) {
                try {
                    icon = WynntilsItems.icon(item.data());
                } catch (Throwable t) {
                    icon = ItemStack.EMPTY;
                }
                if (icon == null) {
                    icon = ItemStack.EMPTY;
                }
            }
            if (!icon.isEmpty()) {
                g.renderItem(icon, x, y);
            }
            int ty = y + 5;
            g.drawString(font, name, x + ICON, ty, -1, true);
            g.drawString(font, level, x + ICON + columns[0] + COL_GAP, ty, -1, true);
            g.drawString(font, item.price(), x + width - font.width(item.price()), ty, -1, true);
        }
    }

    /** A 1 px line with 2 px of air above and below, the tooltip's full width. */
    private record LineRow() implements ClientTooltipComponent {

        @Override
        public int getHeight(Font font) {
            return 5;
        }

        @Override
        public int getWidth(Font font) {
            return 0;
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            g.fill(x, y + 2, x + width, y + 3, LINE);
        }
    }
}
