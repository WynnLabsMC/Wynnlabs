package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The Healer goal scores the healing a build keeps up (Healing): the Mage tree's heals as the
 * game words them, as often as the mana pays for the Heal, raised by Healing Efficiency. A real
 * Lv 121 Light Bender raid healer's sets (two WynnBuilder links) must beat the main attack set the
 * old heal term picked (Thicket, Soul Unmade, Atomizer, two Stagnant Breath: no Healing Efficiency).
 */
class HealerModelTest {

    private static final List<String> MAGE_SPELLS = List.of("Heal", "Teleport", "Meteor", "Ice Snake");

    /** The healer's 18 Light Bender nodes, its spells and its heal nodes. */
    static final List<String> LIGHT_BENDER = List.of("Meteor", "Heal", "Teleport", "Ice Snake", "Wand Proficiency II",
            "Water Mastery", "Sunshower", "Ophanim", "Purification", "Larger Heal", "Fortitude", "Freezing Sigil",
            "Orphion's Pulse", "Resilient Light", "Lightweaver", "Augury", "Searing Light", "Everlasting Light",
            "Sunflare", "Halo", "Interweave", "Dawn");

    static final String LINK_A = "CZ0g2nDJGI4jWGbHgC2hJq3oupKSeMGeWB1+ra3bqczp9krwkU4D";
    static final String LINK_B = "CY0g2nDJSI4jWWfJCTIhJMbnupKSS90qFluMpT-G3";

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static SourceText.Heal heal(List<SourceText.Heal> heals, String name) {
        return heals.stream().filter(h -> h.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void theMageTreeNamesItsHeals() throws Exception {
        List<SourceText.Heal> heals = SourceText.read(TreeModelTest.tree("mage"), "MAGE", MAGE_SPELLS).heals();
        // Heal: 15 % of your max health, you and nearby allies
        SourceText.Heal h = heal(heals, "Heal");
        assertNotNull(h);
        assertEquals("Heal", h.spell());
        assertEquals(0.15, h.fraction(), 1e-9);
        assertEquals(0, h.every(), 1e-9);
        assertTrue(h.allies() && h.efficiency());
        // Orphion's Pulse: two more pulses of 20 % for you alone, 1.5 s apart
        SourceText.Heal pulse = heal(heals, "Orphion's Pulse");
        assertNotNull(pulse);
        assertEquals("Heal", pulse.spell());
        assertEquals(0.4, pulse.fraction(), 1e-9);
        assertFalse(pulse.allies(), "Pulse Self Heal");
        assertEquals(2, pulse.pulses());
        assertEquals(1.5, pulse.delay(), 1e-9);
        assertEquals(1, pulse.landed(3), 1e-9, "both pulses land when the next Heal comes 3 s later");
        assertEquals(0.5, pulse.landed(1.5), 1e-9, "one of them at 1.5 s");
        // Sunflare: 5 % a second for 10 s every 20 s, regeneration (Healing Efficiency leaves it)
        SourceText.Heal flare = heal(heals, "Sunflare");
        assertNotNull(flare);
        assertEquals(0.025, flare.fraction(), 1e-9);
        assertEquals(1, flare.every(), 1e-9);
        assertTrue(flare.allies());
        assertFalse(flare.efficiency());
    }

    @Test
    void castsAreWhatTheManaPaysFor() {
        // 100 mana and 5 a second over 30 s pay for 250 / 35 casts: 0.238 a second
        assertEquals(250.0 / 35 / 30, Healing.casts(35, 5, 100, 30, 0.8), 1e-9);
        assertTrue(Healing.casts(35, 10, 100, 30, 0.8) > Healing.casts(35, 5, 100, 30, 0.8), "more mana back, more casts");
        assertTrue(Healing.casts(25, 5, 100, 30, 0.8) > Healing.casts(35, 5, 100, 30, 0.8), "a cheaper heal, more casts");
        assertEquals(0.8, Healing.casts(5, 50, 400, 30, 0.8), 1e-9, "never faster than the cast rate");
    }

    private static JsonArray mageSpells() {
        JsonArray a = new JsonArray();
        JsonObject heal = new JsonObject();
        heal.addProperty("name", "Heal");
        heal.addProperty("index", 1);
        heal.addProperty("cost", 35);
        heal.addProperty("share", 0.0);
        a.add(heal);
        return a;
    }

    @Test
    void healPerSecondFollowsHealingEfficiencyAndTheHealsCost() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        List<SourceText.Heal> heals = List.of(new SourceText.Heal("Heal", "Heal", 0.15, 0));
        Healing.Result plain = Healing.of(Map.of(), 10000, 25, 100, 0, mageSpells(), heals);
        assertEquals("Heal", plain.spell());
        assertEquals(35, plain.cost(), 1e-9);
        // the pool and 5 mana a second over a 90 s boss fight
        double casts = 550.0 / 35 / 90;
        assertEquals(casts, plain.casts(), 1e-9);
        assertEquals(1500 * casts, plain.self(), 1e-6);
        assertEquals(1500 * casts, plain.perAlly(), 1e-6);
        assertEquals(1500 * casts * 4, plain.value(), 1e-6, "you and three allies");
        Healing.Result efficient = Healing.of(Map.of("healingEfficiency", 100.0), 10000, 25, 100, 0, mageSpells(), heals);
        assertEquals(2 * plain.value(), efficient.value(), 1e-6);
        // the 1st spell's cost IDs make the Heal cheaper: more casts
        Healing.Result cheap = Healing.of(Map.of("raw1stSpellCost", 10.0), 10000, 25, 100, 0, mageSpells(), heals);
        assertEquals(25, cheap.cost(), 1e-9);
        assertEquals(550.0 / 25 / 90, cheap.casts(), 1e-9);
        // an ally gets at most half their max health from a cast
        Healing.Result huge = Healing.of(Map.of("healingEfficiency", 400.0), 10000, 25, 100, 0, mageSpells(), heals);
        assertEquals(5000 * casts, huge.perAlly(), 1e-6);
    }

    // ---- the Lv 121 Light Bender ------------------------------------------------------------

    private static void install() throws Exception {
        List<AbilityNode> tree = TreeModelTest.tree("mage");
        FixtureState.load(Fixtures.root().resolve("shaman-44")).as(Klass.MAGE).atLevel(121)
                .withTree(tree, LIGHT_BENDER).install("en");
        Rules.update(ClassProfiles.rulesFor("Light Bender", false));
        Setup.setGoal("HEALER");
    }

    private static Map<String, Damage.Piece> fromLink(String link) {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(link);
        LinkImport.Result r = LinkImport.of(GameState.current(), b);
        Fixtures.note("link " + b.version() + " level " + b.level() + " skills " + b.skills() + " tomes " + b.tomes()
                + " rows " + r.rows().stream().map(w -> w.piece().name() + ":" + w.why()).toList());
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        int rings = 0;
        for (LinkImport.Row row : r.rows()) {
            if (row.info() == null) {
                continue;
            }
            Slot k = Locks.kind(row.info());
            String slot = k.isWeapon() ? "WEAPON" : Build.slotOf(k, k == Slot.RING ? rings++ : 0);
            set.put(slot, Damage.piece(row.info()));
        }
        return set;
    }

    private static Damage.Piece item(String name) {
        ItemData info = GameState.current().allGear().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
        return Damage.piece(info);
    }

    /**
     * The set the old heal term picked in game: Thicket, Soul Unmade, Atomizer and two Stagnant
     * Breath, with the Damage goal's helmet, boots, bracelet and necklace (no Healing Efficiency
     * anywhere).
     */
    private static Map<String, Damage.Piece> oldPick() {
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        set.put("HELMET", item("Ornate Shadow Cowl"));
        set.put("CHESTPLATE", item("Soul Unmade"));
        set.put("LEGGINGS", item("Atomizer"));
        set.put("BOOTS", item("Ornate Shadow Cloud"));
        set.put("RING1", item("Stagnant Breath"));
        set.put("RING2", item("Stagnant Breath"));
        set.put("BRACELET", item("Ironleaf Bangle"));
        set.put("NECKLACE", item("Taxonomy"));
        set.put("WEAPON", item("Thicket"));
        return set;
    }

    private static Build.Eval score(String what, Map<String, Damage.Piece> set, int earned) {
        TreeModel.Overlay o = TreeModel.run("Light Bender").at(121);
        Build.Eval e = TreeModel.with(o, () -> Build.allocateBest(set, 121, earned));
        assertNotNull(e, what + " does not fit: needs " + java.util.Arrays.toString(Build.minPoints(set.values())));
        Damage.Result r = e.result();
        StringBuilder names = new StringBuilder();
        set.forEach((slot, p) -> names.append(' ').append(slot).append('=').append(p == null ? "-" : p.name()));
        double he = 0;
        for (Damage.Piece p : set.values()) {
            he += p == null ? 0 : p.stats().getOrDefault("healingEfficiency", 0.0);
        }
        Fixtures.note(String.format(java.util.Locale.ROOT, "HEALER Lv 121 %s: objective %.4g, heal %.0f/s, HE %.0f%%, DPS %.0f, ehp elemental %.0f,"
                + " mana %.1f/5s, factors %s, points %s,%s", what, r.objective(), r.healPerSecond(), he, r.spellDps() + r.mainDps(),
                r.ehpElemental(), r.manaPer5(), java.util.Arrays.toString(java.util.Arrays.stream(r.factors())
                        .map(v -> Math.round(v * 1000) / 1000.0).toArray()), java.util.Arrays.toString(e.allocation()), names));
        return e;
    }

    private static int need(Map<String, Damage.Piece> set) {
        return java.util.Arrays.stream(Build.minPoints(set.values())).sum();
    }

    @Test
    void aRaidHealersSetsBeatTheMainAttackSet() throws Exception {
        assumeTrue(java.nio.file.Files.exists(Fixtures.root().resolve("shaman-44").resolve("state.json")));
        install();
        Map<String, Damage.Piece> a = fromLink(LINK_A);
        Map<String, Damage.Piece> b = fromLink(LINK_B);
        assertEquals("Halcyon", a.get("WEAPON").name());
        // the healer's sets ask for more skill points than the model's 200 at Lv 121 (its link
        // sets them by hand, tomes and all): all three get what the neediest asks, so the score
        // compares the gear and not a skill point check
        int earned = Math.max(200, Math.max(need(a), Math.max(need(b), need(oldPick()))));
        Build.Eval ea = score("A", a, earned);
        Build.Eval eb = score("B", b, earned);
        Build.Eval old = score("old pick", oldPick(), earned);
        assertTrue(ea.result().healPerSecond() > old.result().healPerSecond(), "A heals more than the old pick");
        assertTrue(ea.result().objective() > old.result().objective(),
                "A " + ea.result().objective() + " vs old pick " + old.result().objective());
        assertTrue(eb.result().objective() > old.result().objective(),
                "B " + eb.result().objective() + " vs old pick " + old.result().objective());
    }

    /** Debug only (-PdebugDump): the road map's Lv 121 set for this healer. */
    @Test
    void dumpTheRoadMapsPick() throws Exception {
        assumeTrue(Boolean.getBoolean("wynnlabs.debugDump"), "debug dump off");
        install();
        List<Roadmap.Step> steps = Roadmap.build();
        for (Roadmap.Step st : steps) {
            Build.Eval e = st.set();
            if (e == null) {
                continue;
            }
            Map<String, Damage.Piece> set = new HashMap<>();
            st.build().forEach((slot, p) -> {
                if (p != null) {
                    set.put(slot, p);
                }
            });
            score("road map Lv " + st.level(), set, Math.max(200, need(set)));
        }
    }
}
