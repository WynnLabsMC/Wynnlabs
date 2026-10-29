package io.github.anilken.wynnlabs.build;

import java.util.function.Supplier;

/**
 * The "Choose items" window's tuning strip: what the road map's set should lean towards (damage,
 * balanced or tanky; the Priority menu's spell cost, mana regen, sustain and walk speed) and the
 * most its buys may cost. The leaning is a few
 * multipliers on the damage model's own weights (rules.model.*), in force only while the road
 * map is built ({@link #with}): Balanced with nothing on multiplies by 1, the scoring as before.
 * Saved per character with the chosen items ({@link Locks}).
 */
public final class Tuning {

    public static final int BALANCED = 0;
    public static final int DAMAGE = 1;
    public static final int TANKY = 2;

    /** The budget's steps in emeralds: no limit, 10 EB, 1 LE, 5 LE. */
    public static final long[] BUDGETS = {0, 10 * 64, 4096, 5 * 4096};

    /** The Priority menu's leanings, as bits of {@link Choice#lean}: spell cost, mana regen, sustain, walk speed. */
    public static final int LEAN_COST = 1;
    public static final int LEAN_MANA = 2;
    public static final int LEAN_SUSTAIN = 4;
    public static final int LEAN_SPEED = 8;
    /** Every leaning in the menu's order, and its name for the saved file. */
    public static final int[] LEANS = {LEAN_COST, LEAN_MANA, LEAN_SUSTAIN, LEAN_SPEED};
    public static final String[] LEAN_NAMES = {"cost", "mana", "sustain", "speed"};

    /**
     * One setting of the strip: the focus, the leanings ({@link #LEANS} as bits) and the budget
     * in emeralds, 0 for no limit.
     */
    public record Choice(int focus, int lean, long budget) {

        public static final Choice PLAIN = new Choice(BALANCED, 0, 0);

        /** The strip as it was before the Priority menu: Mana is spell cost and mana regen together. */
        public Choice(int focus, boolean mana, boolean speed, long budget) {
            this(focus, (mana ? LEAN_COST | LEAN_MANA : 0) | (speed ? LEAN_SPEED : 0), budget);
        }

        /** Balanced, nothing on, no budget: the road map as it always was. */
        boolean plain() {
            return focus == BALANCED && lean == 0 && budget <= 0;
        }

        /** The weights' part of it: Balanced with nothing on leaves every weight as it is. */
        boolean neutral() {
            return focus == BALANCED && lean == 0;
        }

        /** Whether leaning {@code bit} is on. */
        public boolean leans(int bit) {
            return (lean & bit) != 0;
        }

        /** The old Mana switch: spell cost and mana regen both on. */
        public boolean mana() {
            return leans(LEAN_COST) && leans(LEAN_MANA);
        }

        public boolean speed() {
            return leans(LEAN_SPEED);
        }

        /** For the road map's cache keys; "" when plain. */
        public String key() {
            if (plain()) {
                return "";
            }
            StringBuilder k = new StringBuilder().append(focus);
            for (int i = 0; i < LEANS.length; i++) {
                if (leans(LEANS[i])) {
                    k.append("cmrs".charAt(i));
                }
            }
            return k.append('/').append(budget).toString();
        }

        public Choice withFocus(int f) {
            return new Choice(f, lean, budget);
        }

        /** Leaning {@code bit} turned on or off. */
        public Choice withLean(int bit, boolean on) {
            return new Choice(focus, on ? lean | bit : lean & ~bit, budget);
        }

        /** No leaning at all (the menu's "None"). */
        public Choice withoutLeans() {
            return new Choice(focus, 0, budget);
        }

        public Choice withMana(boolean on) {
            return withLean(LEAN_COST | LEAN_MANA, on);
        }

        public Choice withSpeed(boolean on) {
            return withLean(LEAN_SPEED, on);
        }

        public Choice withBudget(long b) {
            return new Choice(focus, lean, b);
        }

        /** The next budget step after this one (back to no limit after the last). */
        public Choice nextBudget() {
            int at = 0;
            for (int i = 0; i < BUDGETS.length; i++) {
                if (BUDGETS[i] == budget) {
                    at = i;
                }
            }
            return new Choice(focus, lean, BUDGETS[(at + 1) % BUDGETS.length]);
        }

        /** The leanings for the saved file: their names. */
        public com.google.gson.JsonArray leanJson() {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (int i = 0; i < LEANS.length; i++) {
                if (leans(LEANS[i])) {
                    a.add(LEAN_NAMES[i]);
                }
            }
            return a;
        }

        /**
         * A saved strip: "lean" when the file has it, else the old "mana" and "speed" switches
         * (Mana: spell cost and mana regen). Writes stay readable by an older version.
         */
        public static Choice read(com.google.gson.JsonObject t) {
            int focus = t.has("focus") ? t.get("focus").getAsInt() : BALANCED;
            focus = focus < BALANCED || focus > TANKY ? BALANCED : focus;
            long budget = t.has("budget") ? Math.max(0, t.get("budget").getAsLong()) : 0;
            int lean = 0;
            if (t.has("lean") && t.get("lean").isJsonArray()) {
                for (var e : t.getAsJsonArray("lean")) {
                    for (int i = 0; i < LEANS.length; i++) {
                        if (LEAN_NAMES[i].equals(e.getAsString())) {
                            lean |= LEANS[i];
                        }
                    }
                }
            } else {
                boolean mana = t.has("mana") && t.get("mana").getAsBoolean();
                boolean speed = t.has("speed") && t.get("speed").getAsBoolean();
                lean = (mana ? LEAN_COST | LEAN_MANA : 0) | (speed ? LEAN_SPEED : 0);
            }
            return new Choice(focus, lean, budget);
        }

        /** The strip into {@code t}: focus, leanings, budget, and the old switches for an older version. */
        public void write(com.google.gson.JsonObject t) {
            t.addProperty("focus", focus);
            t.add("lean", leanJson());
            t.addProperty("mana", mana());
            t.addProperty("speed", speed());
            t.addProperty("budget", budget);
        }
    }

    private static final ThreadLocal<Choice> ACTIVE = new ThreadLocal<>();

    private Tuning() {}

    /** Runs {@code body} with {@code c}'s weights in force on this thread. */
    public static <T> T with(Choice c, Supplier<T> body) {
        Choice before = ACTIVE.get();
        ACTIVE.set(c == null || c.neutral() ? null : c);
        try {
            return body.get();
        } finally {
            if (before == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(before);
            }
        }
    }

    /** The setting in force on this thread (for work handed to other threads); null when neutral. */
    public static Choice active() {
        return ACTIVE.get();
    }

    /** The multiplier on the model weight {@code key} (rules.model.*); 1 outside {@link #with}. */
    static double weight(String key) {
        Choice c = ACTIVE.get();
        if (c == null) {
            return 1.0;
        }
        return switch (key) {
            // the damage total's exponent and how much effective health counts
            case "damageWeight" -> c.focus == DAMAGE ? 1.15 : c.focus == TANKY ? 0.85 : 1.0;
            // the goal already sets how much health counts: Damage leans a little, not by half again
            case "survivalWeight" -> c.focus == DAMAGE ? 0.75 : c.focus == TANKY ? 2.5 : 1.0;
            case "survivalPower" -> c.focus == DAMAGE ? 0.75 : c.focus == TANKY ? 1.5 : 1.0;
            case "ehpFloorScale" -> c.focus == DAMAGE ? 0.9 : c.focus == TANKY ? 1.25 : 1.0;
            // where health stops paying in full: higher when leaning tanky
            case "ehpSoftCap" -> c.focus == TANKY ? 1.5 : 1.0;
            // mana: its two parts apart, the spells' cost cut and the mana back every 5 s
            case "costCutLean" -> c.leans(LEAN_COST) ? 3.0 : 1.0;
            case "manaRegenLean" -> c.leans(LEAN_MANA) ? 3.0 : 1.0;
            // sustain: health regen and life steal (the model's regen part)
            case "regenWeight" -> c.leans(LEAN_SUSTAIN) ? 3.0 : 1.0;
            // walk speed: its weight and the room it may add
            case "speedWeight" -> c.leans(LEAN_SPEED) ? 3.0 : 1.0;
            case "goalSpeedRoom" -> c.leans(LEAN_SPEED) ? 2.0 : 1.0;
            default -> 1.0;
        };
    }
}
