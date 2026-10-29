package io.github.anilken.wynnlabs.coach;

import com.google.gson.JsonArray;

import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Words;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.Klass;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who the survival warning and the death coach talk to. A player who heals themself is told to
 * heal, not to drink pots; one who is meant to stay in the fight (goal Tank or Healer, or in a
 * boss fight) is not told to back off, and their health is judged by what they net lose, so
 * healing it out is not a reason to warn. All of it behind the healerAdvice switch: off, both
 * speak as they did before.
 */
final class HealAdvice {

    private HealAdvice() {}

    /** A self-heal spell: its click combo ("R-L-R", "" when unknown) and mana cost (0 when unknown). */
    record Healer(String combo, int cost) {}

    /**
     * The moment's picture: the self-heal (null for none), whether the player stays in the fight
     * rather than backing off, whether that is a boss fight, and the mana (-1 when unknown).
     */
    record Stance(Healer healer, boolean holds, boolean boss, int mana) {

        static final Stance NONE = new Stance(null, false, false, -1);

        /** Whether the warning judges health by its net loss (heals count) rather than damage alone. */
        boolean net() {
            return healer != null || holds;
        }

        Stance withMana(int m) {
            return new Stance(healer, holds, boss, m);
        }
    }

    /** Mage's Heal: its 1st spell, the same combo for every Mage. Its cost when the tree does not say. */
    static final String MAGE_HEAL = "Heal";
    static final String MAGE_COMBO = "R-L-R";
    static final int MAGE_COST = 35;

    /** A mob with this many times your health is a boss (bosses have far more than any pack mob). */
    static final int BOSS_RATIO = 30;

    private static final Pattern COMBO = Pattern.compile("Click Combo:?\\s*([A-Z]+)\\s*-\\s*([A-Z]+)\\s*-\\s*([A-Z]+)");
    private static final Pattern COST = Pattern.compile("Mana Cost:?\\s*(\\d+)");

    /** The stance now, from the game (NONE while the switch is off). */
    static Stance now() {
        if (!Settings.on("healerAdvice")) {
            return Stance.NONE;
        }
        Klass cls = Wyn.classType();
        Healer h = selfHeal(cls, Setup.goal(), GameState.current().unlockedAbilities(), Wyn.abilityTree(cls));
        int max = Wyn.health().map(c -> c.max()).orElse(0);
        boolean boss = bossFight(Wyn.focusedMobMaxHealth(), max);
        int mana = Wyn.mana().map(c -> c.current()).orElse(-1);
        return new Stance(h, holds(Setup.goal(), boss), boss, mana);
    }

    /**
     * The player's self-heal: a Mage's Heal (every Mage; unless the tree was read and Heal is not
     * in it), else with the Healer goal a heal of unknown combo. {@code unlocked} may be empty
     * when the tree was not read yet; {@code tree} may be null.
     */
    static Healer selfHeal(Klass cls, String goal, List<String> unlocked, List<AbilityNode> tree) {
        if (cls == Klass.MAGE && (unlocked == null || unlocked.isEmpty() || unlocked.contains(MAGE_HEAL))) {
            AbilityNode node = null;
            if (tree != null) {
                for (AbilityNode n : tree) {
                    if (MAGE_HEAL.equals(n.name())) {
                        node = n;
                        break;
                    }
                }
            }
            String text = node == null ? "" : text(node);
            Matcher c = COMBO.matcher(text);
            Matcher m = COST.matcher(text);
            String combo = c.find() ? letter(c.group(1)) + "-" + letter(c.group(2)) + "-" + letter(c.group(3)) : MAGE_COMBO;
            return new Healer(combo, m.find() ? Integer.parseInt(m.group(1)) : MAGE_COST);
        }
        return "HEALER".equals(goal) ? new Healer("", 0) : null;
    }

    private static String letter(String click) {
        return click.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String text(AbilityNode n) {
        StringBuilder sb = new StringBuilder();
        if (n.description() != null) {
            for (JsonArray line : n.description()) {
                line.forEach(e -> {
                    if (e.isJsonObject() && e.getAsJsonObject().has("text")) {
                        sb.append(e.getAsJsonObject().get("text").getAsString());
                    }
                });
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /** Whether the player stays in the fight: a tank or healer goal, or a boss to fight. */
    static boolean holds(String goal, boolean boss) {
        return boss || "TANK".equals(goal) || "HEALER".equals(goal);
    }

    /** A boss fight: the mob you are hitting has {@link #BOSS_RATIO} times your health or more. */
    static boolean bossFight(long mobMaxHealth, int playerMaxHealth) {
        return playerMaxHealth > 0 && mobMaxHealth >= (long) playerMaxHealth * BOSS_RATIO;
    }

    /** Whether the heal can be cast: mana unknown, cost unknown, or enough of it. */
    static boolean canCast(Healer h, int mana) {
        return mana < 0 || h.cost() <= 0 || mana >= h.cost();
    }

    /**
     * Health lost over the window: the drops alone, or with {@code net} the drops less the heals
     * (the first sample less the last; zero or less when healing keeps up).
     */
    static double lost(List<Integer> hp, boolean net) {
        if (hp.size() < 2) {
            return 0;
        }
        if (net) {
            return hp.get(0) - hp.get(hp.size() - 1);
        }
        double lost = 0;
        for (int i = 1; i < hp.size(); i++) {
            lost += Math.max(0, hp.get(i - 1) - hp.get(i));
        }
        return lost;
    }

    /**
     * The warning: heal now when the heal can be cast, else the pots; without "back off" for a
     * player who stays in. {@code slot} the hotbar slot (0-8) holding a pot, -1 for none.
     */
    static Words warning(String secs, Stance s, int potions, int slot) {
        Healer h = s.healer();
        if (h != null && canCast(h, s.mana())) {
            return h.combo().isEmpty() ? Words.of("wynnlabs.coach.warn_heal_now", secs)
                    : Words.of("wynnlabs.coach.warn_heal_combo", secs, h.combo());
        }
        if (s.holds()) {
            return potions <= 0 ? Words.of("wynnlabs.coach.warn_fast_no_potion", secs)
                    : slot >= 0 ? Words.of("wynnlabs.coach.warn_fast_potion_slot", secs, slot + 1)
                    : Words.of("wynnlabs.coach.warn_fast_potion_bag", secs);
        }
        return potions <= 0 ? Words.of("wynnlabs.coach.warn_no_potion", secs)
                : slot >= 0 ? Words.of("wynnlabs.coach.warn_potion_slot", secs, slot + 1)
                : Words.of("wynnlabs.coach.warn_potion_bag", secs);
    }
}
