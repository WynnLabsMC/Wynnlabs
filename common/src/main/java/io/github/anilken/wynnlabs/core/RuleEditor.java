package io.github.anilken.wynnlabs.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import io.github.anilken.wynnlabs.advice.MarketSlots;
import io.github.anilken.wynnlabs.market.Prices;

import java.util.List;

/**
 * The advice rules a player can change in the J menu's settings (Rules category): numbers with
 * step buttons and on/off switches for what upgrades may come from. Each change goes through the
 * same merge as POST /rules, so it is saved for this character and applies at once.
 */
public final class RuleEditor {

    /**
     * {@code group} null = a top-level rule. {@code choices}: fixed values to step through
     * (emerald amounts) instead of min..max by step. {@code item}: for a list switch, the entry
     * whose presence means "on".
     */
    public record Rule(String title, String desc, String group, String key, double def, double min, double max,
            double step, double[] choices, String unit, String item) {

        public boolean isSwitch() {
            return item != null;
        }
    }

    public static final List<Rule> RULES = List.of(
            num("wynnlabs.rules.too_low", "wynnlabs.rules.too_low_desc", null, "tooLowLevels", 4, 1, 20, 1, "lv"),
            num("wynnlabs.rules.too_high", "wynnlabs.rules.too_high_desc", null, "tooHighLevels", 5, 1, 20, 1, "lv"),
            num("wynnlabs.rules.old_gear", "wynnlabs.rules.old_gear_desc", "shopping", "oldGearLevels", 8, 2, 20, 1, "lv"),
            num("wynnlabs.rules.min_gain", "wynnlabs.rules.min_gain_desc", "shopping", "minGainPercent", 10, 1, 50, 1, "%"),
            num("wynnlabs.rules.ahead", "wynnlabs.rules.ahead_desc", "shopping", "aheadLevels", 5, 1, 15, 1, "lv"),
            num("wynnlabs.rules.off_skills", "wynnlabs.rules.off_skills_desc", "shopping", "maxOffSkillPoints", 10, 0, 100, 5, ""),
            new Rule("wynnlabs.rules.tm_price", "wynnlabs.rules.tm_price_desc", "trade", "minTradeMarketPrice", 1920, 0, 0, 0,
                    new double[] {320, 640, 1280, 1920, 2560, 4096, 8192, 16384, 32768, 65536}, "E", null),
            num("wynnlabs.rules.reroll", "wynnlabs.rules.reroll_desc", null, "rerollBelowPercent", 30, 0, 100, 5, "%"),
            num("wynnlabs.rules.quest_margin", "wynnlabs.rules.quest_margin_desc", null, "questLevelMargin", 4, 0, 15, 1, "lv"),
            num("wynnlabs.rules.grind_below", "wynnlabs.rules.grind_below_desc", "grind", "levelsBelow", 3, 0, 10, 1, "lv"),
            num("wynnlabs.rules.grind_above", "wynnlabs.rules.grind_above_desc", "grind", "levelsAbove", 3, 0, 10, 1, "lv"),
            flag("wynnlabs.rules.no_mythic", "wynnlabs.rules.no_mythic_desc", "excludeTiers", "MYTHIC"),
            flag("wynnlabs.rules.no_event", "wynnlabs.rules.no_event_desc", "excludeSources", "EVENT"),
            flag("wynnlabs.rules.no_raid", "wynnlabs.rules.no_raid_desc", "excludeSources", "RAID"),
            new Rule("wynnlabs.rules.reveal_profit", "wynnlabs.rules.reveal_profit_desc", "trade", "revealMinProfit", 640,
                    0, 0, 0, new double[] {0, 64, 128, 320, 640, 1280, 1920, 4096}, "E", null),
            num("wynnlabs.rules.market_fee", "wynnlabs.rules.market_fee_desc", "trade", "marketFeePercent", 5, 0, 20, 1, "%"),
            // 0 = from the account's rank (MarketSlots)
            num("wynnlabs.rules.market_slots", "wynnlabs.rules.market_slots_desc", "trade", "marketSlots", 0, 0,
                    MarketSlots.MAX, 1, "slots"),
            // one threshold for every item (it replaced the Legendary and any-item roll rules, Rules.mergeRollRules)
            num("wynnlabs.rules.keep_roll", "wynnlabs.rules.keep_roll_desc", "trade", "keepRollPercent", 80, 50, 100, 5, "%"),
            // how much more an untradable dungeon, raid or quest piece must gain than a market one
            num("wynnlabs.rules.effort", "wynnlabs.rules.effort_desc", "shopping", "untradableEffortPercent", 5, 0, 30, 1, "%"),
            // picks this close to the best: what you have first, then the cheaper
            num("wynnlabs.rules.cost_tie", "wynnlabs.rules.cost_tie_desc", "shopping", "costTieBreakPercent", 15, 0, 50, 5, "%"),
            // per piece a road map milestone keeps from the one before (Roadmap.judged)
            num("wynnlabs.rules.keep_milestone", "wynnlabs.rules.keep_milestone_desc", "roadmap", "keepBonusPercent", 1, 0, 5, 1, "%"));

    private RuleEditor() {}

    private static Rule num(String title, String desc, String group, String key, double def, double min, double max,
            double step, String unit) {
        return new Rule(title, desc, group, key, def, min, max, step, null, unit, null);
    }

    private static Rule flag(String title, String desc, String key, String item) {
        return new Rule(title, desc, "shopping", key, 1, 0, 1, 1, null, "", item);
    }

    public static double value(Rule r) {
        if (r.isSwitch()) {
            return on(r) ? 1 : 0;
        }
        return r.group() == null ? Rules.num(r.key(), r.def()) : Rules.sub(r.group(), r.key(), r.def());
    }

    private static boolean on(Rule r) {
        JsonArray list = list(r);
        return list != null && list.contains(new JsonPrimitive(r.item()));
    }

    private static JsonArray list(Rule r) {
        JsonElement g = Rules.get().get(r.group());
        if (g == null || !g.isJsonObject() || !g.getAsJsonObject().has(r.key())
                || !g.getAsJsonObject().get(r.key()).isJsonArray()) {
            return null;
        }
        return g.getAsJsonObject().getAsJsonArray(r.key()).deepCopy();
    }

    /** The rule with this short id ("too_low"), or null. */
    public static Rule byId(String id) {
        for (Rule r : RULES) {
            if (id(r).equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** The index of the rule with this short id in {@link #RULES}, -1 when none. */
    public static int indexOf(String id) {
        for (int i = 0; i < RULES.size(); i++) {
            if (id(RULES.get(i)).equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // ---- "How picky should advice be?": Relaxed, Normal, Strict ----

    /** The rules a preset sets, by short id. */
    public static final List<String> PRESET_RULES = List.of("too_low", "too_high", "reroll", "old_gear", "min_gain", "effort",
            "cost_tie", "ahead", "off_skills");
    /** Each preset rule's value under Relaxed, Normal and Strict; Normal is every default, so no one's advice changed. */
    private static final double[][] PRESET_VALUES = {{6, 4, 2}, {8, 5, 3}, {20, 30, 45}, {6, 8, 12}, {5, 10, 15}, {3, 5, 10},
        {25, 15, 5}, {8, 5, 3}, {20, 10, 5}};
    /** Normal: the preset of the defaults. */
    public static final int NORMAL = 1;

    /** A preset's value of one of its rules. */
    public static double presetValue(int preset, String id) {
        return PRESET_VALUES[PRESET_RULES.indexOf(id)][preset];
    }

    /** The preset (0 Relaxed, 1 Normal, 2 Strict) every preset rule now matches; -1 (Custom) when none. */
    public static int preset() {
        for (int p = 0; p < 3; p++) {
            boolean all = true;
            for (int i = 0; i < PRESET_RULES.size() && all; i++) {
                all = Math.abs(value(byId(PRESET_RULES.get(i))) - PRESET_VALUES[i][p]) < 1e-9;
            }
            if (all) {
                return p;
            }
        }
        return -1;
    }

    /** Every preset rule set to the preset's value at once, for this character. */
    public static void applyPreset(int preset) {
        JsonObject change = new JsonObject();
        for (int i = 0; i < PRESET_RULES.size(); i++) {
            Rule r = byId(PRESET_RULES.get(i));
            JsonPrimitive v = new JsonPrimitive((long) PRESET_VALUES[i][preset]);
            if (r.group() == null) {
                change.add(r.key(), v);
            } else {
                if (!change.has(r.group())) {
                    change.add(r.group(), new JsonObject());
                }
                change.getAsJsonObject(r.group()).add(r.key(), v);
            }
        }
        Rules.update(change);
    }

    /** "8 lv", "%10", "1.9 LE", "on", "Auto (HERO: 15)". */
    public static String display(Rule r) {
        return format(r, value(r));
    }

    /** A value of this rule as its row shows it. */
    public static String format(Rule r, double v) {
        if (r.isSwitch()) {
            return L.t(v > 0 ? "wynnlabs.rules.on" : "wynnlabs.rules.off");
        }
        long n = Math.round(v);
        return switch (r.unit()) {
            case "E" -> Prices.fmt(v);
            case "%" -> L.t("wynnlabs.screen.pct", n);
            case "lv" -> L.t("wynnlabs.rules.levels", n);
            case "slots" -> n <= 0 ? MarketSlots.autoText() : String.valueOf(n);
            default -> String.valueOf(n);
        };
    }

    /** The lowest and highest value the rule takes (an emerald rule's first and last amount). */
    public static double[] range(Rule r) {
        if (r.choices() != null) {
            return new double[] {r.choices()[0], r.choices()[r.choices().length - 1]};
        }
        return new double[] {r.min(), r.max()};
    }

    /** Whether this character's rule differs from its default. */
    public static boolean changed(Rule r) {
        return Math.abs(value(r) - r.def()) > 1e-9;
    }

    /** One rule back to its default, for this character. */
    public static void reset(Rule r) {
        if (r.isSwitch()) {
            if (!changed(r)) {
                return;
            }
            step(r, 1);                         // a switch's default is on: flip it back
            return;
        }
        write(r, new JsonPrimitive((long) r.def()));
    }

    /** The rule's short name ("too_low" of "wynnlabs.rules.too_low"), for its example text. */
    public static String id(Rule r) {
        return r.title().substring(r.title().lastIndexOf('.') + 1);
    }

    /** One step up (+1) or down (-1); a switch flips. */
    public static void step(Rule r, int dir) {
        if (r.isSwitch()) {
            JsonArray list = list(r);
            if (list == null) {
                list = new JsonArray();
            }
            JsonPrimitive it = new JsonPrimitive(r.item());
            if (list.contains(it)) {
                list.remove(it);
            } else {
                list.add(it);
            }
            write(r, list);
            return;
        }
        double v = value(r);
        double next;
        if (r.choices() != null) {
            double[] c = r.choices();
            int i = 0;
            while (i < c.length - 1 && c[i] < v - 0.5) {
                i++;
            }
            if (dir > 0 && c[i] <= v + 0.5) {
                i = Math.min(c.length - 1, i + 1);
            } else if (dir < 0) {
                i = Math.max(0, c[i] >= v - 0.5 ? i - 1 : i);
            }
            next = c[i];
        } else {
            next = Math.max(r.min(), Math.min(r.max(), v + dir * r.step()));
        }
        write(r, new JsonPrimitive(next == Math.rint(next) ? (Number) (long) next : next));
    }

    /**
     * Sets a rule to a typed value. Emerald rules read "30", "30 eb", "1.5 le", "500 e" (a plain
     * number is EB); others a plain number, kept within the rule's range ("auto" for the Trade
     * Market slots). Returns false when the text is no value.
     */
    public static boolean set(Rule r, String typed) {
        if (r.isSwitch() || typed == null) {
            return false;
        }
        String t = typed.trim().toLowerCase(java.util.Locale.ROOT).replace(',', '.').replace(" ", "");
        double mult = 1;
        if (r.unit().equals("E")) {
            mult = 64;                          // a bare number means EB
            if (t.endsWith("le")) {
                mult = 4096;
                t = t.substring(0, t.length() - 2);
            } else if (t.endsWith("eb")) {
                t = t.substring(0, t.length() - 2);
            } else if (t.endsWith("e")) {
                mult = 1;
                t = t.substring(0, t.length() - 1);
            }
        } else if (t.endsWith("%")) {
            t = t.substring(0, t.length() - 1);
        } else if (r.unit().equals("slots") && (t.startsWith("auto") || t.startsWith("oto"))) {
            t = "0";                            // "auto" / "otomatik": from the rank again
        }
        double v;
        try {
            v = Double.parseDouble(t) * mult;
        } catch (NumberFormatException e) {
            return false;
        }
        if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) {
            return false;
        }
        if (r.choices() == null) {
            v = Math.max(r.min(), Math.min(r.max(), v));
        }
        long n = Math.round(v);
        write(r, new JsonPrimitive(n));
        return true;
    }

    public static void reset() {
        MarketSlots.setLimited(true);
        for (Rule r : RULES) {
            if (r.isSwitch()) {
                JsonArray list = list(r);
                if (list == null) {
                    list = new JsonArray();
                }
                if (!list.contains(new JsonPrimitive(r.item()))) {
                    list.add(r.item());
                }
                write(r, list);
            } else {
                write(r, new JsonPrimitive((long) r.def()));
            }
        }
    }

    private static void write(Rule r, JsonElement v) {
        JsonObject change = new JsonObject();
        if (r.group() == null) {
            change.add(r.key(), v);
        } else {
            JsonObject g = new JsonObject();
            g.add(r.key(), v);
            change.add(r.group(), g);
        }
        Rules.update(change);
    }
}
