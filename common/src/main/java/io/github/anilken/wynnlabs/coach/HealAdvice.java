package io.github.anilken.wynnlabs.coach;

import com.google.gson.JsonArray;

import io.github.anilken.wynnlabs.build.ArchetypeMix;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Words;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.Klass;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who the survival warning and the death coach talk to. A player who heals themself is told to
 * heal, not to drink pots; one who is meant to stay in the fight (goal Tank or Healer, a Paladin,
 * or in a boss fight) is not told to back off, and their health is judged by what they net lose, so
 * healing it out is not a reason to warn. All of it behind the healerAdvice switch: off, both
 * speak as they did before.
 */
final class HealAdvice {

    private HealAdvice() {}

    /**
     * A self-heal: the spell cast for it ("Heal", "Aura"; "" when there is none to name), its
     * click combo ("R-L-R", "" when unknown), mana cost (0 when unknown) and Blood Pool cost in
     * percent (0 for none).
     */
    record Healer(String spell, String combo, int cost, int blood) {

        static final Healer UNKNOWN = new Healer("", "", 0);

        Healer(String spell, String combo, int cost) {
            this(spell, combo, cost, 0);
        }
    }

    /**
     * The moment's picture: the self-heal (null for none), whether the player stays in the fight
     * rather than backing off, whether that is a boss fight, the mana and the Blood Pool percent
     * (-1 when unknown).
     */
    record Stance(Healer healer, boolean holds, boolean boss, int mana, int blood) {

        static final Stance NONE = new Stance(null, false, false, -1);

        Stance(Healer healer, boolean holds, boolean boss, int mana) {
            this(healer, holds, boss, mana, -1);
        }

        /** Whether the warning judges health by its net loss (heals count) rather than damage alone. */
        boolean net() {
            return healer != null || holds;
        }

        Stance withMana(int m) {
            return new Stance(healer, holds, boss, m, blood);
        }
    }

    /** Mage's Heal: its 1st spell, the same combo for every Mage. Its cost when the tree does not say. */
    static final String MAGE_HEAL = "Heal";
    static final String MAGE_COMBO = "R-L-R";
    static final int MAGE_COST = 35;

    /**
     * Shaman's Aura heals the party once Sacrificial Shrine is taken, for its mana and 15% of the
     * Blood Pool. Its combo and cost when the tree does not say.
     */
    static final String SHAMAN_AURA = "Aura";
    static final String SHAMAN_SHRINE = "Sacrificial Shrine";
    static final String SHAMAN_COMBO = "R-L-L";
    static final int SHAMAN_COST = 40;
    static final int SHAMAN_BLOOD = 15;

    /** Warrior's Paladin: no heal spell, it lives on regeneration and stays in the fight like a tank. */
    static final String PALADIN = "Paladin";

    /** Heal nodes (the heal spell itself not counted) that make a player a healer whatever the archetype. */
    static final int HEAL_NODES = 2;
    /** Unlocked nodes of one archetype (and more than of any other) that make it the one you lean to. */
    static final int LEAN_NODES = 5;

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
        List<String> unlocked = GameState.current().unlockedAbilities();
        List<AbilityNode> tree = Wyn.abilityTree(cls);
        Healer h = selfHeal(cls, Setup.goal(), TreePlanner.chosen(), unlocked, tree);
        int max = Wyn.health().map(c -> c.max()).orElse(0);
        boolean boss = bossFight(Wyn.focusedMobMaxHealth(), max);
        int mana = Wyn.mana().map(c -> c.current()).orElse(-1);
        boolean holds = holds(Setup.goal(), boss) || paladin(cls, TreePlanner.chosen(), unlocked, tree);
        return new Stance(h, holds, boss, mana, h != null && h.blood() > 0 ? Wyn.bloodPool() : -1);
    }

    /*
     * Who heals, from the Wynncraft 2.x ability trees (the Wynncraft API's ability map as Wynntils
     * caches it, read 2026-10-29):
     * - Mage, Light Bender: the archetype built on Heal (Orphion's Pulse, Larger Heal,
     *   Purification, Sunflare, ... and the Dawn ultimate). Arcanist turns Heal into a mana
     *   transfer and Riftwalker uses it to bind its rift: there it is no healer's heal.
     * - Shaman, Acolyte: Sacrificial Shrine makes Aura heal every ally (it then also costs 15%
     *   Blood Pool); Blood Sorrow and the Monument to Gloom ultimate heal too. Regeneration (any
     *   archetype) makes the Totem heal.
     * - Warrior, Paladin: no heal spell to cast; it lives on regeneration (Rejuvenating Skin) and
     *   War Scream with Holy Power, so it is no healer but stays in the fight like a tank.
     * - Archer and Assassin have no heal worth the name (Assassin's Soul Siphon is a little
     *   overhealth); only the Healer goal makes them healers.
     */

    /** The archetype that heals, for a class ("" for none). */
    static String healArchetype(Klass cls) {
        return switch (cls) {
            case MAGE -> "Light Bender";
            case SHAMAN -> "Acolyte";
            default -> "";
        };
    }

    /** The nodes that make a player heal more (the heal spell itself not among them). */
    static List<String> healNodes(Klass cls) {
        return switch (cls) {
            case MAGE -> List.of("Orphion's Pulse", "Larger Heal", "Purification", "Fortitude", "Sunshower",
                    "Lightweaver", "Interweave", "Sunflare", "Dawn");
            case SHAMAN -> List.of(SHAMAN_SHRINE, "Regeneration", "Blood Sorrow", "Blood Rite", "Greater Sacrifice",
                    "Monument to Gloom");
            default -> List.of();
        };
    }

    /**
     * The player's self-heal, or null for one who does not heal. A healer has the Healer goal,
     * plays the class's healing archetype ({@code chosen}, the planner's choice as
     * {@link ArchetypeMix#spec}, as the larger part; with none chosen the archetype most unlocked
     * nodes are of), or took {@link #HEAL_NODES} heal nodes. A Mage with only the plain Heal and
     * a damage archetype is none. {@code unlocked} may be empty and {@code tree} null when not
     * read yet: then only the goal and the chosen archetype count. The heal cast is a Mage's
     * Heal, a Shaman's Aura with Sacrificial Shrine, else one without a combo.
     */
    static Healer selfHeal(Klass cls, String goal, String chosen, List<String> unlocked, List<AbilityNode> tree) {
        List<String> have = unlocked == null ? List.of() : unlocked;
        if (cls == Klass.MAGE && !have.isEmpty() && !have.contains(MAGE_HEAL)) {
            return "HEALER".equals(goal) ? Healer.UNKNOWN : null;   // no Heal to cast
        }
        List<String> heals = healNodes(cls);
        boolean healer = "HEALER".equals(goal) || playsHealer(cls, chosen, have, tree)
                || have.stream().filter(heals::contains).count() >= HEAL_NODES;
        if (!healer) {
            return null;
        }
        if (cls == Klass.MAGE) {
            return spell(tree, MAGE_HEAL, MAGE_COMBO, MAGE_COST);
        }
        if (cls == Klass.SHAMAN && have.contains(SHAMAN_SHRINE)) {
            Healer aura = spell(tree, SHAMAN_AURA, SHAMAN_COMBO, SHAMAN_COST);
            return new Healer(aura.spell(), aura.combo(), aura.cost(), SHAMAN_BLOOD);
        }
        return Healer.UNKNOWN;
    }

    /** Whether the player is a Warrior playing Paladin (chosen as the larger part, else leaned to). */
    static boolean paladin(Klass cls, String chosen, List<String> unlocked, List<AbilityNode> tree) {
        return cls == Klass.WARRIOR && plays(PALADIN, chosen, unlocked == null ? List.of() : unlocked, tree);
    }

    private static boolean playsHealer(Klass cls, String chosen, List<String> have, List<AbilityNode> tree) {
        String heal = healArchetype(cls);
        return !heal.isEmpty() && plays(heal, chosen, have, tree);
    }

    /**
     * Whether the player plays an archetype: chosen as the larger part, else (none chosen) with
     * {@link #LEAN_NODES} unlocked nodes of it and more than of any other.
     */
    private static boolean plays(String archetype, String chosen, List<String> have, List<AbilityNode> tree) {
        ArchetypeMix mix = ArchetypeMix.parse(chosen);
        if (!mix.auto()) {
            return mix.weight(archetype) > 0.5;
        }
        if (tree == null || have.isEmpty()) {
            return false;
        }
        Map<String, Integer> count = new HashMap<>();
        for (AbilityNode n : tree) {
            if (n.archetype() != null && !n.archetype().isEmpty() && have.contains(n.name())) {
                count.merge(n.archetype(), 1, Integer::sum);
            }
        }
        int mine = count.getOrDefault(archetype, 0);
        return mine >= LEAN_NODES
                && count.entrySet().stream().allMatch(e -> e.getKey().equals(archetype) || e.getValue() < mine);
    }

    /** A spell as the heal: its combo and cost from the tree's text, else the defaults given. */
    private static Healer spell(List<AbilityNode> tree, String name, String combo, int cost) {
        AbilityNode node = null;
        if (tree != null) {
            for (AbilityNode n : tree) {
                if (name.equals(n.name())) {
                    node = n;
                    break;
                }
            }
        }
        String text = node == null ? "" : text(node);
        Matcher c = COMBO.matcher(text);
        Matcher m = COST.matcher(text);
        return new Healer(name, c.find() ? letter(c.group(1)) + "-" + letter(c.group(2)) + "-" + letter(c.group(3)) : combo,
                m.find() ? Integer.parseInt(m.group(1)) : cost);
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

    /** Whether the player stays in the fight: a tank or healer goal, or a boss to fight (a Paladin too, see {@link #paladin}). */
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

    /** The same, with the Blood Pool too when the heal costs some: unknown, or enough of it. */
    static boolean canCast(Healer h, int mana, int blood) {
        return canCast(h, mana) && (h.blood() <= 0 || blood < 0 || blood >= h.blood());
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
     * player who stays in. A heal that costs Blood Pool while the pool is not read offers both the
     * heal and a pot. {@code slot} the hotbar slot (0-8) holding a pot, -1 for none.
     */
    static Words warning(String secs, Stance s, int potions, int slot) {
        Healer h = s.healer();
        if (h != null && canCast(h, s.mana(), s.blood())) {
            if (h.blood() > 0 && s.blood() < 0 && potions > 0 && !h.combo().isEmpty()) {
                return Words.of("wynnlabs.coach.warn_heal_or_potion", secs, h.spell(), h.combo());
            }
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
