package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.build.roadmap.BeamCache;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.AbilityNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Two archetypes combined: the choice and its save (old single saves load as they were), the tree
 * plan keeping to the tree's locks, the points and the split, the class profile's weights as the
 * split's blend, and the shaman-44 road map with a hybrid.
 */
class CombinedArchetypeTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Path shaman44() {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        return dir;
    }

    // ---- the choice ---------------------------------------------------------------------------

    @Test
    void aChoiceReadsBackFromItsText() {
        ArchetypeMix m = ArchetypeMix.parse("Summoner+Ritualist@70");
        assertTrue(m.hybrid());
        assertEquals("Summoner", m.first());
        assertEquals("Ritualist", m.second());
        assertEquals(70, m.split());
        assertEquals(0.7, m.weight("summoner"), 1e-9);
        assertEquals(0.3, m.weight("Ritualist"), 1e-9);
        assertEquals(0, m.weight("Acolyte"));
        assertEquals("Summoner", m.lead());
        assertEquals("Summoner + Ritualist 70/30", m.label());
        assertEquals(m, ArchetypeMix.parse(m.spec()));
        assertEquals("Ritualist", ArchetypeMix.parse("Summoner+Ritualist@30").lead());
        assertTrue(ArchetypeMix.parse("Ritualist+Summoner@30").same(m), "the same pair turned round");

        ArchetypeMix half = ArchetypeMix.parse("Summoner+Ritualist");
        assertEquals(50, half.split());
        assertEquals("Summoner+Ritualist", half.spec());
        assertEquals("Summoner + Ritualist", half.label());
        assertEquals(1.0, half.affinity("Ritualist"), 1e-9, "half each: both pulled alike");

        // one archetype and automatic are the text they always were
        assertEquals("Summoner", ArchetypeMix.parse("Summoner").spec());
        assertFalse(ArchetypeMix.parse("Summoner").hybrid());
        assertEquals(1.0, ArchetypeMix.parse("Summoner").weight("Summoner"));
        assertTrue(ArchetypeMix.parse("").auto());
        assertEquals(1.0, ArchetypeMix.parse("").affinity(""), "automatic pulls the nodes of no archetype, as before");
        assertFalse(ArchetypeMix.parse("Summoner+Summoner").hybrid(), "one archetype twice is one");
    }

    @Test
    void oldSavesLoadAndTheChoiceIsSavedBesideThem() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        FixtureState.load(shaman44()).withTree(nodes, List.of()).install("en");
        // the fixture's rules are from before combined archetypes: tree.archetype alone
        assertEquals("Summoner", TreePlanner.chosen());
        assertFalse(TreePlanner.chosenMix().hybrid());

        TreePlanner.choose("Summoner+Ritualist@70");
        JsonObject tree = Rules.get().getAsJsonObject("tree");
        assertEquals("Summoner", tree.get("archetype").getAsString(), "the first where old saves read it");
        assertEquals("Ritualist", tree.get("with").getAsString());
        assertEquals(70, tree.get("split").getAsInt());
        assertEquals("Summoner+Ritualist@70", TreePlanner.chosen());
        assertEquals("Summoner + Ritualist 70/30", TreePlanner.chosenLabel("Auto"));
        assertEquals("Summoner+Ritualist@70", TreeModel.archetype());

        TreePlanner.choose("Acolyte");
        tree = Rules.get().getAsJsonObject("tree");
        assertFalse(tree.has("with") || tree.has("split"), "one archetype again: the second goes");
        assertEquals("Acolyte", TreePlanner.chosen());

        TreePlanner.choose("");
        assertEquals("", TreePlanner.chosen());
        assertEquals("Auto", TreePlanner.chosenLabel("Auto"));

        // a hybrid written whole through POST /rules reads too
        JsonObject t = new JsonObject();
        t.addProperty("archetype", "Ritualist+Acolyte@30");
        JsonObject change = new JsonObject();
        change.add("tree", t);
        Rules.update(change);
        assertEquals(ArchetypeMix.of("Ritualist", "Acolyte", 30), TreePlanner.chosenMix());
    }

    // ---- the tree plan ------------------------------------------------------------------------

    /** Replays a plan from the first node: every step legal when it is taken, the points within 50. */
    private static Map<String, Integer> replay(List<AbilityNode> nodes, List<TreePlanner.Step> plan) {
        Map<String, AbilityNode> byName = new HashMap<>();
        Map<Integer, AbilityNode> byId = new HashMap<>();
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (AbilityNode n : nodes) {
            byName.put(n.name(), n);
            byId.put(n.id(), n);
        }
        for (AbilityNode n : nodes) {
            for (int c : n.connections()) {
                adj.computeIfAbsent(n.id(), k -> new HashSet<>()).add(c);
                adj.computeIfAbsent(c, k -> new HashSet<>()).add(n.id());
            }
        }
        Set<Integer> taken = new HashSet<>();
        Map<String, Integer> count = new HashMap<>();
        int spent = 0;
        int lastLevel = 0;
        for (TreePlanner.Step s : plan) {
            AbilityNode n = byName.get(s.name());
            assertTrue(n != null, s.name());
            assertTrue(s.level() >= lastLevel, "levels never go down: " + s);
            lastLevel = s.level();
            if (!taken.isEmpty()) {
                assertTrue(adj.getOrDefault(n.id(), Set.of()).stream().anyMatch(taken::contains), s.name() + " touches the path");
            } else {
                assertEquals(nodes.get(0).id(), n.id(), "the tree starts at its first spell");
            }
            if (n.requiredArchetype() != null && !n.requiredArchetype().isBlank()) {
                assertTrue(count.getOrDefault(n.requiredArchetype(), 0) >= n.requiredArchetypePoints(),
                        s.name() + " needs " + n.requiredArchetypePoints() + " " + n.requiredArchetype() + ", had " + count);
            }
            String req = n.requiredAbility();
            if (req != null && !req.isBlank() && !req.equals("None")) {
                assertTrue(taken.contains(byName.get(req).id()), s.name() + " needs " + req);
            }
            for (int id : taken) {
                AbilityNode t = byId.get(id);
                assertFalse(t.willBlock().contains(n.name()) || n.willBlock().contains(t.name()), t.name() + " blocks " + n.name());
            }
            assertTrue(n.requiredLevel() <= Math.max(s.level(), 1), s.name() + " before its level");
            taken.add(n.id());
            spent += n.cost();
            if (n.archetype() != null && !n.archetype().isEmpty()) {
                count.merge(n.archetype(), 1, Integer::sum);
                count.merge("pts:" + n.archetype(), n.cost(), Integer::sum);
            }
        }
        assertTrue(spent <= 50, "50 points at most: " + spent);
        count.put("points", spent);
        return count;
    }

    @Test
    void aHybridPlanKeepsToTheLocksThePointsAndTheSplit() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        FixtureState.load(shaman44()).withTree(nodes, List.of()).install("en");
        Map<Integer, Map<String, Integer>> bySplit = new HashMap<>();
        for (int split : ArchetypeMix.SPLITS) {
            List<TreePlanner.Step> plan = TreePlanner.wholePlan(ArchetypeMix.of("Summoner", "Ritualist", split).spec());
            Map<String, Integer> c = replay(nodes, plan);
            bySplit.put(split, c);
            int sum = c.getOrDefault("Summoner", 0);
            int rit = c.getOrDefault("Ritualist", 0);
            assertTrue(sum > 0 && rit > 0, split + ": nodes of both " + c);
            assertTrue(c.getOrDefault("Acolyte", 0) <= Math.min(sum, rit), split + ": the third only on the way " + c);
            assertTrue(c.get("points") >= 45, split + ": the points are spent " + c);
        }
        // the split is of the points spent on the two, within the planner's slack
        for (int split : ArchetypeMix.SPLITS) {
            Map<String, Integer> c = bySplit.get(split);
            int sum = c.get("pts:Summoner");
            int both = sum + c.get("pts:Ritualist");
            assertEquals(split / 100.0 * both, sum, TreePlanner.splitSlack() + 2, split + ": " + c);
        }
        Map<String, Integer> half = bySplit.get(50);
        assertTrue(bySplit.get(70).get("pts:Summoner") > bySplit.get(70).get("pts:Ritualist"), "70/30: " + bySplit.get(70));
        assertTrue(bySplit.get(30).get("pts:Ritualist") > bySplit.get(30).get("pts:Summoner"), "30/70: " + bySplit.get(30));
        assertTrue(bySplit.get(70).get("pts:Summoner") > half.get("pts:Summoner"), "more Summoner at 70 than at 50");

        // one archetype plans as it did
        Map<String, Integer> single = replay(nodes, TreePlanner.wholePlan("Summoner"));
        assertTrue(single.get("Summoner") > half.get("Summoner"), "a Summoner alone has more Summoner nodes: " + single);
    }

    @Test
    void aHybridPlanFromWhatYouHaveKeepsToTheLocksToo() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        List<String> have = TreeModelTest.summonerAt44(nodes);
        FixtureState.load(shaman44()).withTree(nodes, have).install("en");
        List<TreePlanner.Step> rest = TreePlanner.plan("Summoner+Ritualist", have);
        List<TreePlanner.Step> all = new ArrayList<>();
        for (String h : have) {
            all.add(new TreePlanner.Step(h, 0, 1, ""));
        }
        all.addAll(rest);
        // what you have came from the tree's own order: replayed in it, the plan's steps after
        Map<String, Integer> c = replay(nodes, all);
        assertTrue(c.getOrDefault("Ritualist", 0) > 0, "Ritualist nodes after a Summoner start: " + c);
    }

    // ---- the class profile ---------------------------------------------------------------------

    @Test
    void theProfilesWeightsAreTheSplitsBlend() throws IOException {
        List<AbilityNode> shaman = TreeModelTest.tree("shaman");
        double summoner = ClassProfiles.mainAttackWeight("SHAMAN", "Summoner", shaman);
        double ritualist = ClassProfiles.mainAttackWeight("SHAMAN", "Ritualist", shaman);
        for (int split : ArchetypeMix.SPLITS) {
            double w = split / 100.0;
            assertEquals(w * summoner + (1 - w) * ritualist,
                    ClassProfiles.mainAttackWeight("SHAMAN", ArchetypeMix.of("Summoner", "Ritualist", split).spec(), shaman), 1e-3,
                    "main attack at " + split);
        }
        List<AbilityNode> archer = TreeModelTest.tree("archer");
        double bolt = ClassProfiles.mainAttackWeight("ARCHER", "Boltslinger", archer);
        double sharp = ClassProfiles.mainAttackWeight("ARCHER", "Sharpshooter", archer);
        assertEquals(0.5 * bolt + 0.5 * sharp, ClassProfiles.mainAttackWeight("ARCHER", "Boltslinger+Sharpshooter", archer), 1e-3);
        assertEquals(0.7, ClassProfiles.meleeShare(ArchetypeMix.parse("Boltslinger+Sharpshooter@70")), 1e-9);
        assertEquals(1.0, ClassProfiles.meleeShare(ArchetypeMix.parse("Boltslinger")), 1e-9);

        // the whole profile: the hybrid plan's puppets and the blended main attack go in the rules
        FixtureState.load(shaman44()).withTree(shaman, List.of()).install("en");
        JsonObject mix = ClassProfiles.rulesFor("Summoner+Ritualist", false).getAsJsonObject("model");
        JsonObject one = ClassProfiles.rulesFor("Summoner", false).getAsJsonObject("model");
        assertEquals(0.5 * summoner + 0.5 * ritualist, mix.get("mainAttackWeight").getAsDouble(), 1e-3);
        assertEquals(1.0, mix.get("passiveDpsFactor").getAsDouble(), "the hybrid plan's puppets");
        assertTrue(mix.get("summonNeutral").getAsDouble() <= one.get("summonNeutral").getAsDouble() + 1e-9,
                "no more puppets than a Summoner alone");
    }

    // ---- the road map -----------------------------------------------------------------------------

    @Test
    void theShaman44RoadMapWithAHybridIsSane() throws IOException {
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        List<String> have = TreeModelTest.summonerAt44(nodes);
        FixtureState.load(shaman44()).withTree(nodes, have).install("en");
        BeamCache.clear();
        TreePlanner.choose("Summoner+Ritualist");
        TreeModel.Run run = TreeModel.run();
        assertEquals("Summoner+Ritualist", run.archetype);
        List<Roadmap.Step> steps = Roadmap.build();
        assumeTrue(steps.size() > 2, "no road map");
        int scored = 0;
        for (Roadmap.Step s : steps) {
            if (s.set() == null) {
                continue;
            }
            Damage.Result r = s.set().result();
            assertTrue(r.objective() > 0 && Double.isFinite(r.objective()), "Lv " + s.level() + " objective " + r.objective());
            assertTrue(r.ehp() > 0, "Lv " + s.level() + " survives");
            scored++;
        }
        assertTrue(scored > 2, "milestones scored");
        assertNotEquals("", Rules.subText("tree", "with", ""));
    }

    /**
     * Debug only (-PdebugDump): the shaman-44 road map per milestone for Summoner, Ritualist and
     * the hybrids, with timing, in fabric/build/hybrid-dump.txt.
     */
    @Test
    void dump() throws IOException {
        assumeTrue(Boolean.getBoolean("wynnlabs.debugDump"), "debug dump off");
        List<AbilityNode> nodes = TreeModelTest.tree("shaman");
        List<String> have = TreeModelTest.summonerAt44(nodes);
        StringBuilder out = new StringBuilder();
        String[] choices = {"Summoner", "Summoner", "Ritualist", "Summoner+Ritualist", "Summoner+Ritualist@70",
            "Summoner+Ritualist@30", "Ritualist+Acolyte"};
        for (int i = 0; i < choices.length; i++) {
            FixtureState.load(shaman44()).withTree(nodes, have).install("en");
            BeamCache.clear();
            TreePlanner.choose(choices[i]);
            Map<String, Integer> c = new HashMap<>();
            for (TreePlanner.Step s : TreePlanner.wholePlan(choices[i])) {
                c.merge(s.archetype().isEmpty() ? "-" : s.archetype(), 1, Integer::sum);
            }
            long t0 = System.nanoTime();
            List<Roadmap.Step> steps = Roadmap.build();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            out.append(i == 0 ? "== warm-up " : "== ").append(choices[i]).append(" (").append(ms).append(" ms) plan ")
                    .append(c).append('\n');
            for (Roadmap.Step s : steps) {
                if (s.set() == null) {
                    continue;
                }
                Damage.Result r = s.set().result();
                List<String> picks = new ArrayList<>();
                s.build().forEach((slot, p) -> picks.add(p == null ? "-" : p.name()));
                out.append(String.format(Locale.ROOT, "Lv %3d obj %.1f dps %.0f (spell %.0f main %.0f) ehp %.0f elem %.0f | %s%n",
                        s.level(), r.objective(), r.spellDps() + r.mainDps(), r.spellDps(), r.mainDps(), r.ehp(),
                        r.ehpElemental(), String.join(", ", picks)));
            }
            FixtureState.uninstall();
        }
        Path f = Path.of(System.getProperty("wynnlabs.projectDir", "."), "fabric", "build", "hybrid-dump.txt");
        Files.createDirectories(f.getParent());
        Files.writeString(f, out, StandardCharsets.UTF_8);
    }
}
