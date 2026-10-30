package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A Lv 107 Mage whose tree is Arcanist but who chose Light Bender and the Healer goal: the levels
 * ahead are scored with the Light Bender tree the plan respecs into (its heals: Orphion's Pulse,
 * Sunflare, Larger Heal), not the Arcanist nodes the character has now. With the Arcanist tree the
 * Lv 110 and Lv 120 sets were the same damage set (Ascended Lament, Masterwork Lament in this item
 * data), carried at Lv 120 by its +126 % XP that the Healer goal counted in a straight line.
 */
class HealerRespecTest {

    /** A Lv 107 Arcanist's 36 nodes, as the game lists them. */
    static final List<String> ARCANIST = List.of("Meteor", "Wand Proficiency I", "Cheaper Meteor", "Shooting Star",
            "Teleport", "Wisdom", "Heal", "Cheaper Teleport", "Ice Snake", "Earth Mastery", "Fire Mastery",
            "Thunder Mastery", "Air Mastery", "Burning Sigil", "Etheric Slash", "Crashing Comet", "Arcane Transfer",
            "Sentient Snake", "Larger Mana Bank", "Cheaper Ice Snake", "Pyrokinesis", "Runic Searing",
            "Arcane Restoration", "Arcane Speed", "Larger Mana Bank II", "Cheaper Ice Snake II", "Chaos Explosion",
            "Arcane Power", "Frost Dragon's Breath", "Larger Mana Bank III", "Judrajim", "Arcane Overflow", "Blitz",
            "Induced Instability", "Thunderstorm", "Tangled Origin");

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static void install() throws Exception {
        assumeTrue(java.nio.file.Files.exists(Fixtures.root().resolve("shaman-44").resolve("state.json")));
        List<AbilityNode> tree = TreeModelTest.tree("mage");
        FixtureState.load(Fixtures.root().resolve("shaman-44")).as(Klass.MAGE).atLevel(107)
                .withTree(tree, ARCANIST).install("en");
        TreePlanner.choose("Light Bender");
        Setup.setGoal("HEALER");
    }

    private static boolean heals(TreeModel.Overlay o, String name) {
        return o.heals != null && o.heals.stream().anyMatch(h -> h.name().equals(name));
    }

    @Test
    void theLevelsAheadFollowTheChosenArchetype() throws Exception {
        install();
        assertTrue(TreePlanner.mostUnlocked().equals("Arcanist"), "the tree leans Arcanist");
        TreeModel.Run run = TreeModel.run("Light Bender");
        assertNotNull(run);
        // now: the tree you have (what the game measures)
        assertTrue(run.taken(107).contains("Arcane Power"), "now is the Arcanist tree");
        assertFalse(heals(run.at(107), "Orphion's Pulse"));
        // ahead: the Light Bender plan the tree respecs into
        for (int lv : new int[] {110, 120}) {
            TreeModel.Overlay o = run.at(lv);
            assertTrue(heals(o, "Orphion's Pulse") && heals(o, "Sunflare"), "Lv " + lv + " heals " + o.heals);
            assertFalse(run.taken(lv).contains("Arcane Power"), "Lv " + lv + " leaves the Arcanist nodes");
        }
    }

    @Test
    void theGoalNamesTheArchetypeChosenLast() throws Exception {
        install();
        TreePlanner.choose("Arcanist");
        TreePlanner.choose("Light Bender");
        assertTrue(io.github.anilken.wynnlabs.core.Rules.get().get("goal").getAsString().equals("healer build, Light Bender"),
                io.github.anilken.wynnlabs.core.Rules.get().get("goal").getAsString());
    }

    private static Damage.Piece item(String name) {
        return Damage.piece(io.github.anilken.wynnlabs.game.GameState.current().allGear()
                .filter(i -> i.name().equals(name)).findFirst().orElseThrow());
    }

    private static Map<String, Damage.Piece> set(String... names) {
        String[] slots = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "RING1", "RING2", "BRACELET", "NECKLACE", "WEAPON"};
        Map<String, Damage.Piece> out = new LinkedHashMap<>();
        for (int i = 0; i < slots.length; i++) {
            out.put(slots[i], item(names[i]));
        }
        return out;
    }

    private static Damage.Result score(String what, Map<String, Damage.Piece> set, int level, int earned) {
        TreeModel.Overlay o = TreeModel.run("Light Bender").at(level);
        Build.Eval e = TreeModel.with(o, () -> Build.allocateBest(set, level, earned));
        assertNotNull(e, what + " does not fit");
        Damage.Result r = e.result();
        Fixtures.note(String.format(java.util.Locale.ROOT, "Lv %d %s: objective %.4g, heal %.0f/s, DPS %.0f, ehp %.0f, xp %.0f",
                level, what, r.objective(), r.healPerSecond(), r.spellDps() + r.mainDps(), r.ehpElemental(), r.xp()));
        return r;
    }

    /** The Lv 110 and Lv 120 pick before (Ascended Lament is Masterwork Lament in this item data). */
    private static Map<String, Damage.Piece> damageSet() {
        return set("Liffmail Coif", "Liffmail Hauberk", "Vaward", "Amberwalkers", "Harden Core", "Harden Core",
                "Synchro Core", "Overload Core", "Masterwork Lament");
    }

    /** The guild healer's set (HealerModelTest LINK_A). */
    private static Map<String, Damage.Piece> healerSet() {
        return set("Nexus", "Fidelius", "Champion's Valiance", "Dragon Dance", "Dormancy", "Dormancy",
                "Rewritten History", "Xenotransplantation", "Halcyon");
    }

    @Test
    void atLv120TheHealersSetBeatsTheDamageSet() throws Exception {
        install();
        Map<String, Damage.Piece> dmg = damageSet();
        Map<String, Damage.Piece> heal = healerSet();
        int earned = Math.max(200, Math.max(java.util.Arrays.stream(Build.minPoints(dmg.values())).sum(),
                java.util.Arrays.stream(Build.minPoints(heal.values())).sum()));
        Damage.Result d = score("damage set", dmg, 120, earned);
        Damage.Result h = score("healer set", heal, 120, earned);
        // both heal past what a party takes in (Lament's Healing Efficiency and cheaper Heal); the
        // healer set's health decides it, once its +126 % XP no longer doubles the damage set's score
        assertTrue(h.objective() > d.objective(), "healer " + h.objective() + " vs damage " + d.objective());
    }

    /** Debug only (-PdebugDump): the road map's sets for this healer. */
    @Test
    void dumpTheRoadMapsPicks() throws Exception {
        assumeTrue(Boolean.getBoolean("wynnlabs.debugDump"), "debug dump off");
        install();
        for (io.github.anilken.wynnlabs.build.roadmap.Roadmap.Step st : io.github.anilken.wynnlabs.build.roadmap.Roadmap.build()) {
            if (st.set() == null) {
                continue;
            }
            Map<String, Damage.Piece> set = new LinkedHashMap<>();
            st.build().forEach((slot, p) -> {
                if (p != null) {
                    set.put(slot, p);
                }
            });
            StringBuilder names = new StringBuilder();
            set.forEach((slot, p) -> names.append(' ').append(slot).append('=').append(p.name()));
            score("road map" + names, set, st.level(), Math.max(200, java.util.Arrays.stream(Build.minPoints(set.values())).sum()));
        }
    }

    @Test
    void oldHealerRulesCountXpAsTheDamageGoalDoes() {
        com.google.gson.JsonObject rules = com.google.gson.JsonParser.parseString(
                "{\"goalPreset\": \"HEALER\", \"model\": {\"survivalModel\": 11, \"goalXp\": 1, \"xpLog\": 0}}").getAsJsonObject();
        Setup.upgrade(rules);
        com.google.gson.JsonObject m = rules.getAsJsonObject("model");
        org.junit.jupiter.api.Assertions.assertEquals(Setup.DAMAGE_XP, m.get("goalXp").getAsDouble(), 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(1, m.get("xpLog").getAsDouble(), 1e-9);
        // the home's XP switch turned off by hand stays off
        com.google.gson.JsonObject off = com.google.gson.JsonParser.parseString(
                "{\"goalPreset\": \"HEALER\", \"model\": {\"survivalModel\": 11, \"goalXp\": 0, \"byHand\": [\"goalXp\"]}}")
                .getAsJsonObject();
        Setup.upgrade(off);
        org.junit.jupiter.api.Assertions.assertEquals(0, off.getAsJsonObject("model").get("goalXp").getAsDouble(), 1e-9);
    }
}
