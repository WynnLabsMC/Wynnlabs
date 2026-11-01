package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.BoxTooltip;
import io.github.anilken.wynnlabs.advice.GearTooltip;
import io.github.anilken.wynnlabs.advice.TipRow;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.mixin.GuiGraphicsAccessor;
import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The game's tooltip parts for the short tooltips' rows ({@link BoxTooltip}, {@link GearTooltip}):
 * a text row with a part at each end, an item row (16 px icon, name, level, price at the far end),
 * a thin line, and for gear the header with the stack's icon, the verdict band, identification
 * rows with roll bars, requirement chips, footer pairs and a centred line. Everything is drawn
 * in {@code renderImage}, which the game hands the whole tooltip's width, so the right-hand parts
 * line up at its edge. The band, the chips and the pairs wrap to the width the other rows set.
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

    /** Below this, rows that wrap (band, chips, pairs) wrap anyway: the gear tooltip's least width. */
    private static final int MIN_WRAP = 150;
    /** The roll bar's width and height. */
    private static final int BAR_W = 40;
    private static final int BAR_H = 5;
    private static final int BAR_BG = 0xFF2A2A2A;
    /** A chip's inner padding and the space between chips. */
    private static final int CHIP_PAD = 3;
    private static final int CHIP_GAP = 3;
    private static final int CHIP_BG = 0xFF2C2C2C;
    private static final int CHIP_BAD_BG = 0xFF4A1616;

    /**
     * The rows as tooltip parts. The item rows share one name and one level column, the
     * identification rows one name and one value column; the band, chips and pairs wrap to the
     * width the other rows need (at least {@link #MIN_WRAP} when there is one of them).
     */
    public static List<ClientTooltipComponent> of(List<TipRow> rows, Font font) {
        int nameW = 0;
        int levelW = 0;
        int idNameW = 0;
        int idValueW = 0;
        boolean wraps = false;
        for (TipRow r : rows) {
            if (r instanceof TipRow.Item it) {
                nameW = Math.max(nameW, font.width(name(it.data())));
                levelW = Math.max(levelW, font.width(level(it.level())));
            } else if (r instanceof TipRow.Id id) {
                idNameW = Math.max(idNameW, font.width(id.name()));
                idValueW = Math.max(idValueW, font.width(id.value()));
            } else if (r instanceof TipRow.Band || r instanceof TipRow.Chips || r instanceof TipRow.Pair) {
                wraps = true;
            }
        }
        int[] columns = {nameW, levelW};
        int[] idColumns = {idNameW, idValueW, font.width("100%")};
        // the width the fixed rows need: what the wrapping rows fit into
        int width = 0;
        for (TipRow r : rows) {
            width = Math.max(width, switch (r) {
                case TipRow.Text t -> font.width(t.left()) + (t.right() == null ? 0 : GAP + font.width(t.right()));
                case TipRow.Item it -> ICON + nameW + COL_GAP + levelW + GAP + font.width(it.price());
                case TipRow.Header h -> HeaderRow.width(h, font);
                case TipRow.Id id -> IdRow.width(idColumns);
                case TipRow.Centred c -> font.width(c.text());
                case TipRow.Pair p -> Math.max(p.left() == null ? 0 : font.width(p.left()),
                        p.right() == null ? 0 : font.width(p.right()));
                case TipRow.Band b -> font.width(b.title()) + BandRow.INSET * 2;
                case TipRow.Chips c -> c.chips().stream().mapToInt(ch -> ChipsRow.chipWidth(ch, font)).max().orElse(0);
                case TipRow.Separator s -> 0;
            });
        }
        if (wraps) {
            width = Math.max(width, MIN_WRAP);
        }
        List<ClientTooltipComponent> out = new ArrayList<>(rows.size());
        for (TipRow r : rows) {
            switch (r) {
                case TipRow.Text t -> out.add(new TextRow(t.left(), t.right()));
                case TipRow.Item it -> out.add(new ItemRow(it, columns));
                case TipRow.Separator s -> out.add(new LineRow());
                case TipRow.Header h -> out.add(new HeaderRow(h));
                case TipRow.Id id -> out.add(new IdRow(id, idColumns));
                case TipRow.Centred c -> out.add(new CentredRow(c.text()));
                case TipRow.Band b -> out.add(BandRow.of(b, width, font));
                case TipRow.Chips c -> out.add(new ChipsRow(c.chips(), width, font));
                case TipRow.Pair p -> {
                    int lw = p.left() == null ? 0 : font.width(p.left());
                    int rw = p.right() == null ? 0 : font.width(p.right());
                    if (p.left() == null || p.right() == null || lw + GAP + rw <= width) {
                        out.add(new TextRow(p.left() == null ? Component.empty() : p.left(), p.right(), true));
                    } else {
                        // no room side by side: the right part goes under, still at the far end
                        out.add(new TextRow(p.left(), null, true));
                        out.add(new TextRow(Component.empty(), p.right(), true));
                    }
                }
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
    public static void show(GuiGraphics g, Font font, List<ClientTooltipComponent> rows, int x, int y, ItemStack stack) {
        var deferred = (GuiGraphicsAccessor) g;
        if (deferred.wynnlabs$deferred() == null) {
            // the stack's own tooltip style: the rarity-coloured frame Wynncraft's pack gives it
            Identifier style = stack == null ? null : stack.get(DataComponents.TOOLTIP_STYLE);
            deferred.wynnlabs$setDeferred(() -> g.renderTooltip(font, rows, x, y, DefaultTooltipPositioner.INSTANCE, style));
        }
    }

    private static Component name(ItemData data) {
        return Component.literal(data.name()).withStyle(data.tier().colour());
    }

    private static Component level(int level) {
        return Component.literal(String.valueOf(level)).withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Text at the start, and optionally text at the far end. A soft row (a footer pair) asks for
     * no more width than its longer part: it was split before it could need more.
     */
    private record TextRow(Component left, Component right, boolean soft) implements ClientTooltipComponent {

        TextRow(Component left, Component right) {
            this(left, right, false);
        }

        @Override
        public int getHeight(Font font) {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            int r = right == null ? 0 : font.width(right);
            return soft ? Math.max(font.width(left), r) : font.width(left) + (right == null ? 0 : GAP + r);
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

        private final TipRow.Item item;
        private final int[] columns;
        private final Component name;
        private final Component level;
        /** The icon, looked up on the first draw; EMPTY when it could not be. */
        private ItemStack icon;

        ItemRow(TipRow.Item item, int[] columns) {
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

    /** The stack's icon beside two lines: name and overall roll; tier, type, level and rerolls. */
    private record HeaderRow(TipRow.Header h) implements ClientTooltipComponent {

        static int width(TipRow.Header h, Font font) {
            int top = font.width(h.name()) + (h.overall() == null ? 0 : GAP + font.width(h.overall()));
            int bottom = font.width(h.sub()) + (h.rerolls() == null ? 0 : GAP + font.width(h.rerolls()));
            return ICON + Math.max(top, bottom);
        }

        @Override
        public int getHeight(Font font) {
            return 24;
        }

        @Override
        public int getWidth(Font font) {
            return width(h, font);
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            if (h.icon() != null && !h.icon().isEmpty()) {
                g.renderItem(h.icon(), x, y + 2);
            }
            g.drawString(font, h.name(), x + ICON, y + 1, -1, true);
            if (h.overall() != null) {
                g.drawString(font, h.overall(), x + width - font.width(h.overall()), y + 1, -1, true);
            }
            g.drawString(font, h.sub(), x + ICON, y + 12, -1, true);
            if (h.rerolls() != null) {
                g.drawString(font, h.rerolls(), x + width - font.width(h.rerolls()), y + 12, -1, true);
            }
        }
    }

    /**
     * The verdict: a tinted box with a 2 px edge in its colour, the title bold, the note in a
     * lighter tint after it when it fits, else on up to two lines under it ("…" when cut).
     */
    private record BandRow(Component title, Component inline, List<Component> below, int rgb)
            implements ClientTooltipComponent {

        /** The edge and the padding before the text, and the padding after it. */
        static final int INSET = 5;
        private static final int MAX_LINES = 2;

        static BandRow of(TipRow.Band b, int width, Font font) {
            Integer c = b.colour().getColor();
            int rgb = c == null ? 0xAAAAAA : c;
            // the note in the band's colour, half way to white
            int light = (((rgb >> 16 & 0xFF) + 0xFF) / 2) << 16 | (((rgb >> 8 & 0xFF) + 0xFF) / 2) << 8
                    | ((rgb & 0xFF) + 0xFF) / 2;
            int inner = width - INSET * 2;
            if (b.note() == null || b.note().isBlank()) {
                return new BandRow(b.title(), null, List.of(), rgb);
            }
            String note = b.note().strip();
            if (font.width(b.title()) + 4 + font.width(note) <= inner) {
                return new BandRow(b.title(), Component.literal(note).withColor(light), List.of(), rgb);
            }
            List<String> lines = wrap(note, inner, font);
            List<Component> below = new ArrayList<>(MAX_LINES);
            for (int i = 0; i < lines.size() && i < MAX_LINES; i++) {
                String line = lines.get(i);
                if (i == MAX_LINES - 1 && lines.size() > MAX_LINES) {
                    String rest = String.join(" ", lines.subList(i, lines.size()));
                    line = font.plainSubstrByWidth(rest, Math.max(0, inner - font.width("…"))).stripTrailing() + "…";
                }
                below.add(Component.literal(line).withColor(light));
            }
            return new BandRow(b.title(), null, List.copyOf(below), rgb);
        }

        /** Words in lines no wider than {@code w}; a word longer than a line gets one of its own. */
        private static List<String> wrap(String text, int w, Font font) {
            List<String> out = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(next) > w) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
            }
            return out;
        }

        @Override
        public int getHeight(Font font) {
            return 2 + 3 + 10 * (1 + below.size()) + 1 + 3;
        }

        @Override
        public int getWidth(Font font) {
            return font.width(title) + INSET * 2;
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            int top = y + 2;
            int bottom = y + getHeight(font) - 3; // height is the whole tooltip's, not this band's
            g.fill(x, top, x + width, bottom, 0x30000000 | rgb);
            g.fill(x, top, x + 2, bottom, 0xFF000000 | rgb);
            int ty = top + 3;
            g.drawString(font, title, x + INSET, ty, -1, true);
            if (inline != null) {
                g.drawString(font, inline, x + INSET + font.width(title) + 4, ty, -1, true);
            }
            for (Component line : below) {
                ty += 10;
                g.drawString(font, line, x + INSET, ty, -1, true);
            }
        }
    }

    /** One identification: name, value right-aligned in its column, roll bar and %. */
    private record IdRow(TipRow.Id id, int[] columns) implements ClientTooltipComponent {

        static int width(int[] columns) {
            return columns[0] + GAP + columns[1] + 4 + BAR_W + 4 + columns[2];
        }

        @Override
        public int getHeight(Font font) {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            return width(columns);
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            g.drawString(font, id.name(), x, y, -1, true);
            // value, bar and % keep to the far end; every value's right edge on one line
            int pctX = x + width - columns[2];
            int barX = pctX - 4 - BAR_W;
            int valueRight = barX - 4;
            g.drawString(font, id.value(), valueRight - font.width(id.value()), y, -1, true);
            if (id.percent() == null) {
                return;
            }
            Integer c = id.colour().getColor();
            int rgb = 0xFF000000 | (c == null ? 0xAAAAAA : c);
            int by = y + 2;
            g.fill(barX, by, barX + BAR_W, by + BAR_H, BAR_BG);
            int fill = (int) Math.round(BAR_W * id.percent() / 100.0);
            if (fill > 0) {
                g.fill(barX, by, barX + fill, by + BAR_H, rgb);
            }
            Component pct = Component.literal(L.t("wynnlabs.box.pct", Math.round(id.percent())))
                    .withStyle(ChatFormatting.DARK_GRAY);
            g.drawString(font, pct, x + width - font.width(pct), y, -1, true);
        }
    }

    /** Requirement chips, laid out in lines no wider than the tooltip. */
    private static final class ChipsRow implements ClientTooltipComponent {

        private static final int CHIP_H = 11;
        private final List<TipRow.Chip> chips;
        /** Each chip's line, worked out for the tooltip's width. */
        private final int[] line;
        private final int lines;

        ChipsRow(List<TipRow.Chip> chips, int width, Font font) {
            this.chips = chips;
            this.line = new int[chips.size()];
            int at = 0;
            int row = 0;
            for (int i = 0; i < chips.size(); i++) {
                int w = chipWidth(chips.get(i), font);
                if (at > 0 && at + w > width) {
                    row++;
                    at = 0;
                }
                line[i] = row;
                at += w + CHIP_GAP;
            }
            this.lines = chips.isEmpty() ? 0 : row + 1;
        }

        static int chipWidth(TipRow.Chip c, Font font) {
            return font.width(c.text()) + CHIP_PAD * 2;
        }

        @Override
        public int getHeight(Font font) {
            return 3 + lines * (CHIP_H + 2) + 1;
        }

        @Override
        public int getWidth(Font font) {
            int w = 0;
            for (TipRow.Chip c : chips) {
                w = Math.max(w, chipWidth(c, font));
            }
            return w;
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            int at = x;
            int row = 0;
            for (int i = 0; i < chips.size(); i++) {
                TipRow.Chip c = chips.get(i);
                if (line[i] != row) {
                    row = line[i];
                    at = x;
                }
                int top = y + 3 + row * (CHIP_H + 2);
                int w = chipWidth(c, font);
                boolean bad = Boolean.FALSE.equals(c.met());
                g.fill(at, top, at + w, top + CHIP_H, bad ? CHIP_BAD_BG : CHIP_BG);
                g.drawString(font, Component.literal(c.text()).withStyle(bad ? ChatFormatting.RED : ChatFormatting.GRAY),
                        at + CHIP_PAD, top + 2, -1, true);
                at += w + CHIP_GAP;
            }
        }
    }

    /** One line in the middle. */
    private record CentredRow(Component text) implements ClientTooltipComponent {

        @Override
        public int getHeight(Font font) {
            return 10;
        }

        @Override
        public int getWidth(Font font) {
            return font.width(text);
        }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            g.drawString(font, text, x + (width - font.width(text)) / 2, y, -1, true);
        }
    }
}
