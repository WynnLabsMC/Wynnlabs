package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.AbilityNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The breakdown source by source (Sources): what the tree's text says about each summon, heal
 * and ultimate, and on the recorded Shaman with the game's tree, that the entries add up to the
 * model's own spell, main attack and summon DPS.
 */
class SourcesTest {

    private static final List<String> SHAMAN_SPELLS = List.of("Totem", "Haul", "Uproot", "Aura");
    private static final List<String> ARCHER_SPELLS = List.of("Arrow Bomb", "Escape", "Arrow Storm", "Arrow Shield");

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static SourceText.Heal heal(SourceText.Reading r, String name) {
        return r.heals().stream().filter(h -> h.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void theShamanTreeNamesItsSummonsHealsAndUltimates() throws IOException {
        SourceText.Reading r = SourceText.read(TreeModelTest.tree("shaman"), "SHAMAN", SHAMAN_SPELLS);
        assertEquals("Totem", r.spellOf().get("Puppet Master"));
        assertEquals("Totem", r.spellOf().get("Crimson Effigy"));
        assertEquals("Haul", r.spellOf().get("Hummingbird's Song"), "sent out by Haul");
        // Regeneration heals 1 % every 0.4 s while the Totem stands; Sacrificial Shrine 25 % per Aura
        SourceText.Heal regen = heal(r, "Regeneration");
        assertNotNull(regen);
        assertEquals("Totem", regen.spell());
        assertEquals(0.01, regen.fraction(), 1e-9);
        assertEquals(0.4, regen.every(), 1e-9);
        SourceText.Heal shrine = heal(r, "Sacrificial Shrine");
        assertNotNull(shrine);
        assertEquals("Aura", shrine.spell(), "the spell that heals, not the Totem that siphons");
        assertEquals(0, shrine.every(), "its every 0.4 s is the siphon, not the heal");
        // the Patchwork Abomination's close-ranged attack: its first damage block every 1.25 s
        SourceText.Ultimate patch = r.ultimates().stream().filter(u -> u.name().equals("Patchwork Abomination"))
                .findFirst().orElseThrow();
        assertEquals(16, patch.conv()[0], 1e-9);
        assertEquals(5, patch.conv()[1], 1e-9);
        assertEquals(3, patch.conv()[2], 1e-9);
        assertEquals(0, patch.conv()[4], 1e-9, "the explosion is another part");
        assertEquals(0.8, patch.rate(), 1e-9);
    }

    @Test
    void theOverlayKeepsEachSummonAndTheyAddUpToItsPassiveDamage() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        List<TreeModel.Read> reads = TreeModel.read(nodes, "SHAMAN", SHAMAN_SPELLS);
        SourceText.Reading text = SourceText.read(nodes, "SHAMAN", SHAMAN_SPELLS);
        for (double cap : new double[] {0}) {
            TreeModel.Overlay o = TreeReading.overlay(106, reads, SHAMAN_SPELLS, List.of("Totem", "Haul", "Puppet Master",
                    "Crimson Effigy", "Hummingbird's Song"), 1, cap, Map.of(), text);
            List<String> names = new ArrayList<>();
            double[] sum = new double[Damage.ELEMENTS.length];
            for (Sources.Passive p : o.passives) {
                names.add(p.name());
                for (int i = 0; i < sum.length; i++) {
                    sum[i] += p.conv()[i] * p.rate() * p.count() * p.mult() * Sources.fightShare(p.summon());
                }
            }
            assertEquals(List.of("Puppet Master", "Crimson Effigy", "Hummingbird's Song"), names);
            for (int i = 0; i < sum.length; i++) {
                assertEquals(o.passive[i], sum[i], 1e-4, Damage.ELEMENTS[i] + " with cap " + cap);
            }
        }
    }

    @Test
    void archerTrapsAndSummonsAreAreasAndSummons() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("archer");
        List<TreeModel.Read> reads = TreeModel.read(nodes, "ARCHER", ARCHER_SPELLS);
        SourceText.Reading text = SourceText.read(nodes, "ARCHER", ARCHER_SPELLS);
        TreeModel.Overlay o = TreeReading.overlay(100, reads, ARCHER_SPELLS, List.of("Arrow Shield", "Call of the Hound",
                "Arrow Bomb", "Fire Creep"), 1, 0, Map.of(), text);
        Map<String, Sources.Passive> by = new TreeMap<>();
        o.passives.forEach(p -> by.put(p.name(), p));
        assertTrue(by.get("Call of the Hound").summon());
        assertEquals("Arrow Shield", by.get("Call of the Hound").from());
        assertTrue(!by.get("Fire Creep").summon(), "a trail of fire is an area");
        assertEquals("Arrow Bomb", by.get("Fire Creep").from());
        // "(Max 90%)" caps Chilling Snare's slow: one snare, not 90
        TreeModel.Overlay snare = TreeReading.overlay(100, reads, ARCHER_SPELLS, List.of("Arrow Bomb", "Basaltic Trap",
                "Chilling Snare"), 1, 0, Map.of(), text);
        assertEquals(1, snare.passives.get(0).count());
    }

    // ---- the recorded Shaman ----------------------------------------------------------------

    private static Build.Plan shaman(List<String> have, List<AbilityNode> nodes) throws IOException {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        if (nodes == null) {
            FixtureState.load(dir).install("en");
        } else {
            FixtureState.load(dir).withTree(nodes, have).install("en");
        }
        Build.Plan p = Build.plan();
        assertNotNull(p);
        return p;
    }

    private static void addsUp(Damage.Breakdown b) {
        Map<String, Double> byKind = new TreeMap<>();
        for (Sources.Source s : b.sources()) {
            assertTrue(s.dps() >= 0 && Double.isFinite(s.dps()), s.name());
            byKind.merge(s.kind(), s.dps(), Double::sum);
        }
        double spells = byKind.getOrDefault("spell", 0.0);
        double summons = byKind.getOrDefault("lasting", 0.0) + byKind.getOrDefault("summon", 0.0)
                + byKind.getOrDefault("area", 0.0) + byKind.getOrDefault("other", 0.0);
        assertEquals(b.spellTotal(), spells, Math.max(1e-6, b.spellTotal() * 1e-9), "spells");
        assertEquals(b.mainTotal(), byKind.getOrDefault("main", 0.0), Math.max(1e-6, b.mainTotal() * 1e-9), "main");
        assertEquals(b.summonTotal(), summons, Math.max(1e-6, b.summonTotal() * 1e-9), "summons");
    }

    @Test
    void withoutTheTreeTheProfilesSummonsAreOneEntry() throws IOException {
        Damage.Breakdown b = shaman(null, null).currentDetail(null, null).breakdown();
        addsUp(b);
        assertTrue(b.sources().stream().anyMatch(s -> s.kind().equals("other") && s.dps() > 0), "the profile's summons");
    }

    @Test
    void aSummonersSummonsAreSplitAndAddUp() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        List<String> have = new ArrayList<>(TreeModelTest.summonerAt44(nodes));
        for (String n : List.of("Haul", "Crimson Effigy", "Hummingbird's Song", "Regeneration", "Patchwork Abomination")) {
            if (!have.contains(n)) {
                have.add(n);
            }
        }
        Damage.Breakdown b = shaman(have, nodes).currentDetail(null, null).breakdown();
        System.out.println("[sources] " + Sources.json(b.sources()));
        addsUp(b);
        Map<String, Sources.Source> by = new TreeMap<>();
        b.sources().forEach(s -> by.put(s.name(), s));
        for (String n : List.of("Puppet Master", "Crimson Effigy", "Hummingbird's Song")) {
            Sources.Source s = by.get(n);
            assertNotNull(s, n + " in " + by.keySet());
            assertEquals("summon", s.kind(), n);
            assertTrue(s.dps() > 0 && s.perHit() > 0, n);
            assertEquals(s.perHit() * s.hitsPerSecond() * s.count(), s.dps(), s.dps() * 1e-9, n);
        }
        assertTrue(by.get("Puppet Master").count() >= 3, "three puppets and the More Puppets nodes");
        assertEquals(2, by.get("Hummingbird's Song").count(), 1e-9);
        Sources.Source patch = by.get("Patchwork Abomination");
        assertNotNull(patch);
        assertTrue(!patch.counted() && patch.dps() > 0, "an ultimate, apart from the totals");
        Sources.Source regen = by.get("Regeneration");
        assertNotNull(regen);
        assertTrue(regen.heal() > 0 && regen.healPerSecond() > 0, "heals while the Totem stands");
    }

    @Test
    void twoTreesApartOnlyPastTheCapHaveTheirOwnKeyAndDamage() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        List<TreeModel.Read> reads = TreeModel.read(nodes, "SHAMAN", SHAMAN_SPELLS);
        SourceText.Reading text = SourceText.read(nodes, "SHAMAN", SHAMAN_SPELLS);
        List<String> four = List.of("Totem", "Puppet Master", "More Puppets");
        List<String> six = List.of("Totem", "Puppet Master", "More Puppets", "More Puppets II");
        // a cap both are well past: the capped mix is the same, the puppets are not
        TreeModel.Overlay a = TreeReading.overlay(60, reads, SHAMAN_SPELLS, four, 1, 0.1, Map.of(), text);
        TreeModel.Overlay b = TreeReading.overlay(60, reads, SHAMAN_SPELLS, six, 1, 0.1, Map.of(), text);
        assertArrayEquals(a.passive, b.passive, 1e-9, "the capped mix");
        assertNotEquals(a.key(), b.key(), "More Puppets II scores otherwise, so it keys otherwise");
        Map<String, Double> shares = Map.of("neutral", 1.0);
        double[] skill = new double[Damage.ELEMENTS.length];
        Sources.Summons sa = Sources.summons(a, Map.of(), shares, 100, skill, 1, null, null);
        Sources.Summons sb = Sources.summons(b, Map.of(), shares, 100, skill, 1, null, null);
        assertEquals(sa.shown() * 6 / 4, sb.shown(), sb.shown() * 1e-9, "six puppets against four");
        assertTrue(sb.scored() > sa.scored());
    }

    @Test
    void aHealNamingNoSpellIsNoCrash() {
        TreeModel.Read node = new TreeModel.Read("Mending Light", "", null, 0, null, 0, 1, null, false, Map.of(), 0);
        SourceText.Reading text = new SourceText.Reading(Map.of(), List.of(new SourceText.Heal("Mending Light", null, 0.1, 0),
                new SourceText.Heal("Soothing Pulse", null, 0.02, 2)), List.of(), List.of(), Map.of(), Map.of(), Map.of());
        TreeModel.Read pulse = new TreeModel.Read("Soothing Pulse", "", null, 0, null, 0, 1, null, false, Map.of(), 0);
        TreeModel.Overlay o = TreeReading.overlay(60, List.of(node, pulse), SHAMAN_SPELLS,
                List.of("Mending Light", "Soothing Pulse"), 1, 0, Map.of(), text);
        assertEquals(2, o.heals.size(), "a heal of no spell is kept");
        Damage.Breakdown b = new Damage.Breakdown();
        TreeModel.with(o, () -> {
            Sources.fill(b, new Sources.Inputs(Map.of(), Map.of("neutral", 1.0), 100, new double[Damage.ELEMENTS.length], 1,
                    2, 1, 1000, List.of()));
            return null;
        });
        Map<String, Sources.Source> by = new TreeMap<>();
        b.sources().forEach(s -> by.put(s.name(), s));
        // once per cast of no spell: its heal is known, how often it comes is not
        assertEquals(100, by.get("Mending Light").heal(), 1e-9);
        assertEquals(0, by.get("Mending Light").healPerSecond(), 1e-9);
        // its own clock: 20 every 2 s
        assertEquals(10, by.get("Soothing Pulse").healPerSecond(), 1e-9);
    }
}
