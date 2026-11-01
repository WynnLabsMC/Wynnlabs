package io.github.anilken.wynnlabs.advice;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Stamp;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.BaseStats;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Requirement;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.StatRange;
import io.github.anilken.wynnlabs.ui.TooltipRows;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The short tooltip of an identified gear piece (setting {@code gearCompactTooltip}): the icon,
 * name and overall roll, tier, type, level and rerolls; the verdict in a tinted band; up to six
 * identifications with their roll bars; the requirements as chips; then health or damage, powder
 * slots, the Trade Market price and the bank line. Shift shows the full tooltip again. The rows
 * are plain data ({@link TipRow}); {@link TooltipRows} draws them.
 */
public final class GearTooltip {

    private GearTooltip() {}


    /** The rows kept per stack, with what they were built from. */
    private record Built(long until, int stamp, int inSize, List<ClientTooltipComponent> out) {}

    private static final java.util.WeakHashMap<ItemStack, Built> BUILT = new java.util.WeakHashMap<>();

    /**
     * The short tooltip for {@code stack}, whose full tooltip is {@code lines}, as the game's
     * tooltip parts; null to leave the tooltip as it is (not identified gear, the setting off,
     * Shift held). Kept per stack a quarter second, and rebuilt at once when the stamp changes or
     * the full tooltip has a different number of lines. Client thread only.
     */
    public static List<ClientTooltipComponent> compact(ItemStack stack, List<Component> lines) {
        if (stack == null || stack.isEmpty() || !Settings.on("gearCompactTooltip")
                || Minecraft.getInstance().hasShiftDown()) {
            return null;
        }
        long now = System.currentTimeMillis();
        int stamp = Stamp.now();
        Built b = BUILT.get(stack);
        if (b != null && now < b.until() && b.stamp() == stamp && b.inSize() == lines.size()) {
            return b.out();
        }
        // Wynntils draws identified gear itself, so the bank line is not among its lines: take
        // it from the advice the full tooltip would add
        List<Component> withBank = lines;
        if (BoxTooltip.bank(lines) == null) {
            withBank = new ArrayList<>(lines);
            withBank.addAll(Advice.linesCached(stack));
        }
        List<TipRow> rows = rows(stack, withBank, false);
        List<ClientTooltipComponent> out = rows == null ? null
                : List.copyOf(TooltipRows.of(rows, Minecraft.getInstance().font));
        BUILT.put(stack, new Built(now + 250 + (System.identityHashCode(stack) & 127), stamp, lines.size(), out));
        return out;
    }

    /** The short tooltip's rows, or null when the stack keeps its full tooltip. */
    static List<TipRow> rows(ItemStack stack, List<Component> lines, boolean shift) {
        if (shift || stack == null || stack.isEmpty() || !Settings.on("gearCompactTooltip")) {
            return null;
        }
        Optional<ItemView> found = GameState.current().view(stack);
        if (found.isEmpty() || !found.get().isGear() || found.get().rolled() == null || found.get().data() == null) {
            return null;
        }
        ItemView view = found.get();
        ItemData data = view.data();
        ItemView.Rolled rolled = view.rolled();
        List<TipRow> rows = new ArrayList<>();

        rows.add(header(stack, data, rolled));
        TipRow.Band band = band(stack);
        if (band != null) {
            rows.add(band);
        }
        rows.addAll(base(data));
        // the fixed stats first in the same columns without a bar, a rule, then every rolled
        // identification with its bar
        List<Roll> fixed = new ArrayList<>();
        List<TipRow> ids = new ArrayList<>();
        for (Roll r : rolled.rolls()) {
            if (percent(r) == null) {
                fixed.add(r);
            } else {
                ids.add(id(r));
            }
        }
        for (Roll r : fixed) {
            rows.add(id(r));
        }
        if (!fixed.isEmpty() && !ids.isEmpty()) {
            rows.add(new TipRow.Separator());
        }
        rows.addAll(ids);
        List<TipRow.Chip> chips = chips(data, rolled.marks());
        if (!chips.isEmpty()) {
            rows.add(new TipRow.Chips(chips));
        }
        List<TipRow> footer = footer(data, rolled.powders(), lines);
        if (!footer.isEmpty()) {
            rows.add(new TipRow.Separator());
            rows.addAll(footer);
        }
        rows.add(new TipRow.Centred(Component.literal(L.t("wynnlabs.boxtip.shift")).withStyle(ChatFormatting.DARK_GRAY)));
        return rows;
    }

    /** Icon, name and overall roll; tier · type · level and the rerolls under them. */
    private static TipRow.Header header(ItemStack stack, ItemData data, ItemView.Rolled rolled) {
        Component name = Component.literal(data.name()).withStyle(data.tier().colour(), ChatFormatting.BOLD);
        Component overall = !rolled.hasOverall() ? null
                : Component.literal(L.t("wynnlabs.box.pct", Math.round(rolled.overallPercent())))
                        .withStyle(quality(rolled.overallPercent()), ChatFormatting.BOLD);
        String sub = AutoAdvice.nice(data.tier().name()) + " · " + AutoAdvice.nice(data.slot().name()).replace('_', ' ')
                + " · " + L.t("wynnlabs.geartip.level", data.level());
        Component rerolls = rolled.rerolls() <= 0 ? null
                : Component.literal(L.n("wynnlabs.box.rerolls", rolled.rerolls(), rolled.rerolls()))
                        .withStyle(ChatFormatting.DARK_GRAY);
        return new TipRow.Header(stack, name, overall, Component.literal(sub).withStyle(ChatFormatting.DARK_GRAY), rerolls);
    }

    /** The verdict the badge and the full tooltip show, with where to sell; null without one. */
    private static TipRow.Band band(ItemStack stack) {
        Advice.Entry e = BoxTooltip.entry(stack);
        if (e == null) {
            return null;
        }
        ChatFormatting colour = bandColour(e.verdict());
        MutableComponent title = Component.literal(e.verdict().label()).withStyle(colour, ChatFormatting.BOLD);
        String where = Advice.sellPlace(e);
        if (where != null) {
            title.append(Component.literal(" → " + where).withStyle(colour, ChatFormatting.BOLD));
        }
        String note = e.note() != null && !e.note().isBlank() ? e.note().strip() : e.verdict().meaning();
        return new TipRow.Band(title, note, colour);
    }

    /** Green to wear or keep, yellow to sell, grey for the rest. */
    static ChatFormatting bandColour(Verdict v) {
        return switch (v) {
            case WEAR, USE, BUY, KEEP -> ChatFormatting.GREEN;
            case SELL -> ChatFormatting.YELLOW;
            default -> ChatFormatting.GRAY;
        };
    }

    /** One identification: name, signed value with its unit, and its roll % and colour. */
    static TipRow.Id id(Roll r) {
        Double p = percent(r);
        ChatFormatting colour = p == null ? ChatFormatting.GRAY : quality(p);
        String number = r.value() > 0 ? "+" + r.value() : String.valueOf(r.value());
        Component value = Component.literal(L.unit(number, suffix(r.unit()))).withStyle(colour);
        return new TipRow.Id(Component.literal(r.name()).withStyle(ChatFormatting.GRAY), value, p, colour);
    }

    /**
     * The stats with one value only, on one line: "+6 to all skills" when the five skills share a
     * value, else "+4 Str · +2 Dex · +10% XP" (skills by their three letters).
     */


    /**
     * Where a roll sits between the stat's lowest and highest value, 0-100, the way the game's
     * overall % counts it (a negative stat closer to zero is the better roll); flipped for a stat
     * where less is better. Null for a stat with one value only or an unknown range.
     */
    static Double percent(Roll r) {
        StatRange range = r.range();
        if (range == null || range.fixed()) {
            return null;
        }
        int lo = Math.min(range.min(), range.max());
        int hi = Math.max(range.min(), range.max());
        double p = 100.0 * (r.value() - lo) / (hi - lo);
        if (range.inverted()) {
            p = 100.0 - p;
        }
        return Math.max(0.0, Math.min(100.0, p));
    }

    /** A roll's colour: red under 40, gold to 69, green to 89, aqua from 90. */
    static ChatFormatting quality(double p) {
        long r = Math.round(p);
        return r >= 90 ? ChatFormatting.AQUA : r >= 70 ? ChatFormatting.GREEN : r >= 40 ? ChatFormatting.GOLD
                : ChatFormatting.RED;
    }

    /** The stat unit as the game writes it after the number. */
    static String suffix(String unit) {
        if (unit == null) {
            return "";
        }
        return switch (unit) {
            case "PERCENT" -> "%";
            case "PER_5_S", "PER_FIVE_SECONDS" -> "/5s";
            case "PER_3_S", "PER_THREE_SECONDS" -> "/3s";
            case "TIER" -> " tier";
            default -> "";
        };
    }

    /**
     * Level, class and skill requirements as chips. Met or not from the game's own checkmarks
     * when they were read, else from the character's level, class and skill points; no mark when
     * nothing is known. A failing one carries your own value.
     */
    static List<TipRow.Chip> chips(ItemData data, ItemView.Marks marks) {
        GameState state = GameState.current();
        Requirement req = data.requirements();
        List<TipRow.Chip> out = new ArrayList<>();
        int level = state.level();
        Boolean levelOk = level > 0 ? level >= req.level() : null;
        out.add(chip(L.t("wynnlabs.geartip.level", req.level()), levelOk, level > 0 ? level : null));
        Klass need = req.klass().orElse(data.slot().klass());
        if (need != null && need != Klass.NONE) {
            Klass have = state.classType();
            Boolean ok = have == null || have == Klass.NONE ? null : have == need;
            out.add(chip(AutoAdvice.nice(need), ok, null));
        }
        boolean known = marks != null && marks.known();
        for (Requirement.SkillReq s : req.skills()) {
            Integer have = state.skillsKnown() ? total(state, s.skill()) : null;
            Boolean ok = known ? marks.skills().get(s.skill()) : have != null ? have >= s.points() : null;
            out.add(chip(s.skill().displayName().substring(0, 3) + " " + s.points(), ok, have));
        }
        return out;
    }

    private static TipRow.Chip chip(String text, Boolean ok, Object have) {
        String mark = ok == null ? "" : ok ? " ✓" : " ✗";
        String own = Boolean.FALSE.equals(ok) && have != null ? " (" + have + ")" : "";
        return new TipRow.Chip(text + mark + own, ok);
    }

    /** Points in a skill as the skill advice counts them: assigned, gear, tomes and set bonus. */
    private static int total(GameState state, SkillKind s) {
        return state.assignedSeen(s) + state.gearPoints(s) + state.tomePoints(s) + state.setBonusPoints(s);
    }

    /**
     * Two labelled lines: health (or a weapon's damage) and powder slots; the Trade Market price
     * and the bank line from the full tooltip. A line with nothing on it is left out.
     */
    /** The elements' symbols and colours as the game writes them; neutral damage in gold. */
    private static final String[] ELEMENTS = {"NEUTRAL", "EARTH", "THUNDER", "WATER", "FIRE", "AIR"};
    private static final String[] SYMBOLS = {"✣", "✤", "✦", "❉", "✹", "❋"};
    private static final ChatFormatting[] INKS = {ChatFormatting.GOLD, ChatFormatting.DARK_GREEN, ChatFormatting.YELLOW,
            ChatFormatting.AQUA, ChatFormatting.RED, ChatFormatting.WHITE};

    /**
     * What never rolls, right under the verdict: a weapon's DPS with its attack speed, then its
     * damage per element; a piece of armour's health with its elemental defences.
     */
    static List<TipRow> base(ItemData data) {
        List<TipRow> out = new ArrayList<>(2);
        BaseStats base = data.base();
        if (base == null) {
            return out;
        }
        if (data.slot().isWeapon()) {
            MutableComponent dps = Component.empty();
            if (base.averageDps() > 0) {
                dps.append(Component.literal("⚔ ").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal(L.t("wynnlabs.geartip.dps", base.averageDps())).withStyle(ChatFormatting.GRAY));
            }
            base.attackSpeed().ifPresent(sp -> {
                if (!dps.getSiblings().isEmpty()) {
                    dps.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
                }
                dps.append(Component.literal(speed(sp)).withStyle(ChatFormatting.GRAY));
            });
            if (!dps.getSiblings().isEmpty()) {
                out.add(new TipRow.Text(dps, null));
            }
            MutableComponent dmg = Component.empty();
            for (BaseStats.Damage d : base.damages()) {
                int i = element(d.type());
                if (!dmg.getSiblings().isEmpty()) {
                    dmg.append(Component.literal("  "));
                }
                dmg.append(Component.literal(SYMBOLS[i] + " ").withStyle(INKS[i]))
                        .append(Component.literal(d.low() + "–" + d.high()).withStyle(ChatFormatting.GRAY));
            }
            if (!dmg.getSiblings().isEmpty()) {
                out.add(new TipRow.Text(dmg, null));
            }
        } else {
            Component health = base.health() == 0 ? null : Component.literal("❤ ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(L.t("wynnlabs.geartip.health", base.health())).withStyle(ChatFormatting.GRAY));
            MutableComponent def = Component.empty();
            for (BaseStats.Defence d : base.defences()) {
                if (d.value() == 0) {
                    continue;
                }
                int i = element(d.element());
                if (!def.getSiblings().isEmpty()) {
                    def.append(Component.literal(" "));
                }
                def.append(Component.literal(SYMBOLS[i] + " ").withStyle(INKS[i]))
                        .append(Component.literal((d.value() > 0 ? "+" : "") + d.value())
                                .withStyle(d.value() > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            }
            if (health != null) {
                out.add(new TipRow.Text(health, null));
            }
            if (!def.getSiblings().isEmpty()) {
                out.add(new TipRow.Text(def, null));
            }
        }
        return out;
    }

    private static int element(String name) {
        for (int i = 0; i < ELEMENTS.length; i++) {
            if (ELEMENTS[i].equals(name)) {
                return i;
            }
        }
        return 0;
    }

    /** Hits per second by attack speed, the game's own figures. */
    private static final Map<String, String> HITS = Map.of("SUPER_FAST", "4.3", "VERY_FAST", "3.1", "FAST", "2.5",
            "NORMAL", "2.05", "SLOW", "1.5", "VERY_SLOW", "0.83", "SUPER_SLOW", "0.51");

    /** "VERY_FAST" as the game writes it, with its hits per second: "Very Fast (3.1 hits/s)". */
    static String speed(String upper) {
        String hits = HITS.get(upper);
        return hits == null ? words(upper) : L.t("wynnlabs.geartip.speed", words(upper), hits);
    }

    /** "VERY_FAST" as the game writes it: "Very Fast". */
    static String words(String upper) {
        StringBuilder b = new StringBuilder();
        for (String w : upper.split("_")) {
            if (!w.isEmpty()) {
                b.append(b.isEmpty() ? "" : " ").append(AutoAdvice.nice(w));
            }
        }
        return b.toString();
    }

    /** "◈ ✹ ✦ 2/3 powder slots": the powders in the item, each in its element's colour, then the slots. */
    static Component powders(int slots, List<String> applied) {
        MutableComponent out = Component.literal("◈ ").withStyle(ChatFormatting.GRAY);
        for (String p : applied) {
            int i = element(p);
            out.append(Component.literal(SYMBOLS[i] + " ").withStyle(INKS[i]));
        }
        String count = applied.isEmpty() ? String.valueOf(slots) : applied.size() + "/" + slots;
        return out.append(Component.literal(L.n("wynnlabs.geartip.powder_slots", slots, count)).withStyle(ChatFormatting.GRAY));
    }

    static List<TipRow> footer(ItemData data, List<String> applied, List<Component> lines) {
        List<TipRow> out = new ArrayList<>(2);
        Component powders = data.powderSlots() <= 0 ? null : powders(data.powderSlots(), applied);
        String p = Prices.text(data.name(), false);
        Component price = p == null ? null
                : Component.literal("◇ " + L.t("wynnlabs.geartip.tm", BoxTooltip.shortPrice(p))).withStyle(ChatFormatting.YELLOW);
        Component bankLine = BoxTooltip.bank(lines);
        Component bank = bankLine == null ? null : Component.literal(bankLine.getString()).withStyle(ChatFormatting.GRAY);
        if (powders != null) {
            out.add(new TipRow.Pair(powders, null));
        }
        if (price != null || bank != null) {
            out.add(new TipRow.Pair(price, bank));
        }
        return out;
    }
}
