package io.github.anilken.wynnlabs.advice;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Stamp;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.ui.TooltipRows;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The short tooltip of an unidentified gear box (setting {@code boxCompactTooltip}): the name and
 * level range, the rarity pill and how many items it can hold, the verdict and where to sell, the
 * bank line, then up to eight possible drops with their icon, level and price. Shift shows the
 * full tooltip again. The rows are plain data ({@link Row}), so they are built and tested without
 * the game; {@link TooltipRows} turns them into the game's tooltip parts.
 */
public final class BoxTooltip {

    private BoxTooltip() {}

    /** How many possible drops the short tooltip lists before "+N more". */
    static final int MAX_ITEMS = 8;

    /** One row of the short tooltip. */
    public sealed interface Row permits Text, Item, Separator {}

    /** A text row: {@code left} at the start, {@code right} (may be null) at the far end. */
    public record Text(Component left, Component right) implements Row {}

    /** A possible drop: its icon and name (in its rarity's colour), level and price ("?" without one). */
    public record Item(ItemData data, int level, Component price) implements Row {}

    /** A thin line between the parts. */
    public record Separator() implements Row {}

    /** The rows kept per stack, with what they were built from. */
    private record Built(long until, int stamp, int inSize, List<ClientTooltipComponent> out) {}

    private static final java.util.WeakHashMap<ItemStack, Built> BUILT = new java.util.WeakHashMap<>();

    /**
     * The short tooltip for {@code stack}, whose full tooltip is {@code lines}, as the game's
     * tooltip parts; null to leave the tooltip as it is (not a box, the setting off, Shift held).
     * Kept per stack a quarter second, like the full one, and rebuilt at once when the stamp
     * changes or the full tooltip has a different number of lines. Client thread only.
     */
    public static List<ClientTooltipComponent> compact(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty() || !Settings.on("boxCompactTooltip")
                || Minecraft.getInstance().hasShiftDown()) {
            return null;
        }
        long now = System.currentTimeMillis();
        int stamp = Stamp.now();
        Built b = BUILT.get(stack);
        if (b != null && now < b.until() && b.stamp() == stamp && b.inSize() == lines.size()) {
            return b.out();
        }
        List<Row> rows = rows(stack, lines, false);
        List<ClientTooltipComponent> out = rows == null ? null
                : List.copyOf(TooltipRows.of(rows, Minecraft.getInstance().font));
        BUILT.put(stack, new Built(now + 250 + (System.identityHashCode(stack) & 127), stamp, lines.size(), out));
        return out;
    }

    /** The short tooltip's rows, or null when the stack keeps its full tooltip. */
    static List<Row> rows(ItemStack stack, List<Component> lines, boolean shift) {
        if (shift || stack == null || stack.isEmpty() || !Settings.on("boxCompactTooltip")) {
            return null;
        }
        Optional<ItemView> view = GameState.current().view(stack);
        if (view.isEmpty() || !view.get().isBox() || view.get().box() == null) {
            return null;
        }
        ItemView.Box box = view.get().box();
        ChatFormatting rarity = box.tier().colour();
        List<ItemData> possible = new ArrayList<>(GameState.current().possibleGears(box));
        possible.sort(Comparator.comparingInt((ItemData i) -> i.requirements().level()).thenComparing(ItemData::name));

        List<Row> rows = new ArrayList<>();
        // the name in its rarity's colour, the level range at the far end
        String name = ItemJson.clean(stack.getHoverName().getString());
        rows.add(new Text(Component.literal(name).withStyle(rarity, ChatFormatting.BOLD),
                Component.literal(L.t("wynnlabs.boxtip.levels", box.low(), box.high())).withStyle(ChatFormatting.DARK_GRAY)));
        // the rarity pill as Wynncraft draws it (its own word when the lore has none), then the count
        Component pill = pill(lines, name);
        MutableComponent second = Component.empty()
                .append(pill != null ? pill : Component.literal("[" + box.tier().name() + "]").withStyle(rarity))
                .append(Component.literal("  · " + L.n("wynnlabs.boxtip.drops", possible.size(), possible.size()))
                        .withStyle(ChatFormatting.DARK_GRAY));
        rows.add(new Text(second, null));
        // the verdict and where to sell it, with what the box fetches on the Trade Market
        List<Row> middle = new ArrayList<>();
        Text verdict = verdict(stack, box);
        if (verdict != null) {
            middle.add(verdict);
        }
        Component bank = bank(lines);
        if (bank != null) {
            middle.add(new Text(bank, null));
        }
        if (!middle.isEmpty()) {
            rows.add(new Separator());
            rows.addAll(middle);
        }
        // the possible drops, by level then name
        if (!possible.isEmpty()) {
            rows.add(new Separator());
            for (int i = 0; i < possible.size() && i < MAX_ITEMS; i++) {
                ItemData info = possible.get(i);
                String p = Prices.text(info.name(), true);
                Component price = p == null ? Component.literal("?").withStyle(ChatFormatting.DARK_GRAY)
                        : Component.literal(shortPrice(p)).withStyle(ChatFormatting.YELLOW);
                rows.add(new Item(info, info.requirements().level(), price));
            }
            if (possible.size() > MAX_ITEMS) {
                rows.add(new Text(Component.literal(L.t("wynnlabs.boxtip.more", possible.size() - MAX_ITEMS))
                        .withStyle(ChatFormatting.DARK_GRAY), null));
            }
        }
        rows.add(new Text(Component.literal(L.t("wynnlabs.boxtip.shift")).withStyle(ChatFormatting.DARK_GRAY), null));
        return rows;
    }

    /** "~1.0 LE (median 1.4 LE)" → "1.0 LE": the price alone, as the box's list shows it. */
    static String shortPrice(String p) {
        String s = p.contains(" (") ? p.substring(0, p.indexOf(" (")) : p;
        return s.replace("~", "").strip();
    }

    /**
     * "SELL → Blacksmith", the TM worth at the far end: the advice the slot's badge and the full
     * tooltip show (a saved verdict, else the rules' own), never worked out anew. Null without one.
     */
    private static Text verdict(ItemStack stack, ItemView.Box box) {
        if (!Rules.adviceHere() || !Settings.on("itemAdvice")) {
            return null;
        }
        Advice.Entry e = Advice.find(stack, k -> !k.startsWith("ability:") && !k.startsWith("name:"));
        if (e == null) {
            e = AutoAdvice.cached(stack);
        }
        if (e == null || e.verdict() == null || e.verdict() == Verdict.NOTE) {
            return null;
        }
        MutableComponent left = Component.empty()
                .append(Component.literal(e.verdict().label()).withStyle(e.verdict().colour(), ChatFormatting.BOLD));
        String where = Advice.sellPlace(e);
        if (where != null) {
            left.append(Component.literal(" → " + where).withStyle(ChatFormatting.WHITE));
        }
        // what it fetches revealed and listed unidentified: the mean over its priced outcomes
        Double worth = BoxRoutes.value(BoxAdvice.outcomes(box, null), SellAdvice.marketFee()).expected();
        Component right = worth == null ? null
                : Component.literal(L.t("wynnlabs.boxtip.tm", "~" + Prices.fmt(worth))).withStyle(ChatFormatting.DARK_GRAY);
        return new Text(left, right);
    }

    /** The bank line the full tooltip has ("▣ Mage bank p. 3"), its wrapped parts joined; null without one. */
    static Component bank(List<Component> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Component c = lines.get(i);
            String t = c.getString();
            if (!t.startsWith("▣ ")) {
                continue;
            }
            StringBuilder sb = new StringBuilder(t.strip());
            var colour = c.getStyle().getColor();
            for (int j = i + 1; j < lines.size(); j++) {
                Component n = lines.get(j);
                String nt = n.getString();
                if (nt.isBlank() || nt.startsWith("◆ ") || nt.startsWith("▣ ") || colour == null
                        || !colour.equals(n.getStyle().getColor())) {
                    break;
                }
                sb.append(' ').append(nt.strip());   // a wrapped part of the same line
            }
            return Component.literal(sb.toString()).withStyle(c.getStyle());
        }
        return null;
    }

    /**
     * The rarity pill Wynncraft draws under the name: among the first lines, one in a font of its
     * own (the resource pack's glyphs) that is neither the name nor the level range. Null when
     * there is none.
     */
    static Component pill(List<Component> lines, String name) {
        for (int i = 1; i < Math.min(5, lines.size()); i++) {
            Component c = lines.get(i);
            String plain = ItemJson.clean(c.getString());
            if (plain.contains(name) || plain.chars().anyMatch(Character::isDigit) || !ownFont(c)) {
                continue;
            }
            return c;
        }
        return null;
    }

    /** Whether any part of the line is drawn in a font other than the game's default. */
    private static boolean ownFont(Component c) {
        boolean[] own = {false};
        c.visit((style, text) -> {
            FontDescription f = style.getFont();
            if (!text.isBlank() && f != null && !FontDescription.DEFAULT.equals(f)) {
                own[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);
        return own[0];
    }
}
