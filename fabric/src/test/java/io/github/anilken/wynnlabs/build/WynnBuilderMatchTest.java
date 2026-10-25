package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.StatRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A Lv 121 Summoner rebuilt in the model and held to what WynnBuilder shows for the same build
 * (Propeller Hat, Flexibility, Omniverse, Conviction, Bloodborne, Taxation, Mydium, Diamond
 * Fusion Necklace, Masterwork Olympic with three Air VII powders; every ID at its best roll, as
 * WynnBuilder shows them; skill points 75/39/35/31/140). The mod does not read a link's tree, so
 * the tree is the Summoner nodes WynnBuilder's numbers call for: eight puppets with Crystal
 * Knives, three Totems, Crimson Effigy, Hummingbird's Song, Rebound, the Patchwork Abomination.
 * Item and tree data are the mod's own (Wynntils' database, the game's tree text).
 */
class WynnBuilderMatchTest {

    private static final List<String> SPELLS = List.of("Totem", "Haul", "Uproot", "Aura");
    private static final List<String> NODES = List.of("Totem", "Haul", "Uproot", "Aura", "Relik Proficiency I",
            "Cheaper Totem", "Cheaper Haul", "Cheaper Uproot", "Cheaper Aura", "Earth Mastery", "Air Mastery",
            "Puppet Master", "More Puppets", "More Puppets II", "Puppetry", "Crystal Knives", "Double Totem",
            "Triple Totem", "Crimson Effigy", "Hummingbird's Song", "Rebound", "Patchwork Abomination");
    private static final List<String> GEAR = List.of("Propeller Hat", "Flexibility", "Omniverse", "Conviction",
            "Bloodborne", "Taxation", "Mydium", "Diamond Fusion Necklace");
    private static final String[] SLOTS = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "RING1", "RING2", "BRACELET",
        "NECKLACE"};
    private static final int[] SKILLS = {75, 39, 35, 31, 140};

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private record Built(Damage.Detail detail, BuildStats.Side side) {}

    /** Every ID at its best roll: the larger end (a negative ID at its smaller cut). */
    private static Damage.Piece best(Damage.Piece p, ItemData info) {
        Map<String, Double> stats = new HashMap<>(p.stats());
        for (StatRange r : info.stats()) {
            stats.put(r.key(), (double) Math.max(r.min(), r.max()));
        }
        return new Damage.Piece(p.stack(), p.key(), p.name(), p.type(), p.level(), stats, p.reqs(), p.baseDps(),
                p.weaponShares(), p.health());
    }

    private static Built build() throws IOException {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("geardb.json")), "no shaman-44 fixture");
        List<AbilityNode> tree = TreeModelTest.tree("shaman");
        FixtureState.load(dir).withTree(tree, NODES).install("en");
        JsonObject rules = Rules.get().deepCopy();
        JsonObject model = rules.has("model") ? rules.getAsJsonObject("model") : new JsonObject();
        model.add("spells", ClassProfiles.spells());
        model.addProperty("assumePowders", 1);
        model.addProperty("powderTier", 7);
        rules.add("model", model);
        Rules.use(rules);
        Map<String, ItemData> byName = new TreeMap<>();
        GameState.current().allGear().filter(i -> GEAR.contains(i.name()) || i.name().equals("Masterwork Olympic"))
                .forEach(i -> byName.putIfAbsent(i.name(), i));
        ItemData olympic = byName.get("Masterwork Olympic");
        assertNotNull(olympic);
        Damage.Piece weapon = best(Damage.piece(olympic), olympic);       // three Air VII powders in
        model.addProperty("assumePowders", 0);                             // the armour has none
        Rules.use(rules);
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        List<Damage.Piece> armour = new ArrayList<>();
        for (int i = 0; i < GEAR.size(); i++) {
            ItemData info = byName.get(GEAR.get(i));
            assertNotNull(info, GEAR.get(i));
            Damage.Piece p = best(Damage.piece(info), info);
            armour.add(p);
            set.put(SLOTS[i], p);
        }
        set.put("WEAPON", weapon);
        Map<String, Double> gear = Damage.totals(armour, weapon);
        int[] assigned = new int[SkillKind.values().length];
        for (int i = 0; i < SKILLS.length; i++) {
            SkillKind sk = SkillKind.values()[i];
            assigned[i] = (int) Math.round(SKILLS[i] - Damage.skillPoints(gear, new int[assigned.length], sk));
        }
        List<TreeModel.Read> reads = TreeModel.read(tree, "SHAMAN", SPELLS);
        Map<String, TreeBonuses.Bonus> bonuses = TreeBonuses.read(tree, "SHAMAN", SPELLS,
                List.of("puppet", "effigy", "hummingbird"));
        TreeModel.Overlay o = TreeReading.overlay(121, reads, SPELLS, NODES, 1, 2.5, bonuses,
                SourceText.read(tree, "SHAMAN", SPELLS));
        Damage.Detail d = TreeModel.with(o, () -> Damage.detail(armour, weapon, assigned, 121));
        return new Built(d, BuildStats.of(set, assigned, d.result(), o, 0));
    }

    private static Sources.Source source(Damage.Detail d, String name) {
        return d.breakdown().sources().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static double part(Sources.Source s, String name) {
        return s.parts().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow().value();
    }

    private static void near(double wynnBuilder, double ours, double share, String what) {
        assertEquals(wynnBuilder, ours, Math.abs(wynnBuilder) * share, what + ": ours " + ours + ", WynnBuilder " + wynnBuilder);
    }

    @Test
    void eachSourceIsWhereWynnBuilderPutsIt() throws IOException {
        Damage.Detail d = build().detail();
        System.out.println("[wb] " + Sources.json(d.breakdown().sources()));
        near(27863, source(d, "Puppet Master").dps(), 0.05, "Total Puppet DPS");
        // WynnBuilder's Effigy hits for 100 % (×0.64 for the two extra Totems' Effigies) and a
        // Hummingbird for 30 %; the game's own text, which the mod reads, gives 115 % (90 % and
        // 25 % fire) and 35 % (25 %, 5 % thunder, 5 % air): the rest of the pipeline is the same
        near(25085 * 1.15, source(d, "Crimson Effigy").dps(), 0.05, "Total Effigy DPS (at 115 %)");
        near(16483 * 35 / 30.0, source(d, "Hummingbird's Song").dps(), 0.05, "Total Hummingbird DPS (at 35 %)");
        assertEquals(3, source(d, "Crimson Effigy").count(), 1e-9, "one Effigy per Totem");
        assertEquals(8, source(d, "Puppet Master").count(), 1e-9);
        assertEquals(2, source(d, "Hummingbird's Song").count(), 1e-9);
        near(3356, source(d, "Totem").dps(), 0.05, "Totem Tick DPS");
        Sources.Source aura = source(d, "Aura");
        near(18353, part(aura, "First Wave"), 0.05, "Aura First Wave");
        near(18353 / 3.0, part(aura, "Single Wave"), 0.05, "Aura Single Wave");
        near(18353 * 2, part(aura, "Total Damage"), 0.05, "Aura with Rebound");
        near(8160, source(d, "Uproot").perCast(), 0.05, "Uproot");
        near(2102, source(d, "Main Attack").perHit(), 0.05, "Melee per attack");
        near(153579, source(d, "Patchwork Abomination").perHit(), 0.05, "Patch Hit");
    }

    @Test
    void healthDefencesAndManaAreWynnBuildersToo() throws IOException {
        BuildStats.Side s = build().side();
        near(14560, s.result().health(), 0.001, "Total HP");
        near(38338, s.result().ehp(), 0.005, "Effective HP (with Agility)");
        near(13553, s.ehpNoAgility(), 0.005, "Effective HP (no Agility)");
        near(200, s.regen(), 0.001, "HP regen");
        near(260.70, s.defenceTotal("earth"), 0.001, "Earth defence");
        near(233.05, s.defenceTotal("thunder"), 0.001, "Thunder defence");
        near(130.35, s.defenceTotal("water"), 0.001, "Water defence");
        near(114.55, s.defenceTotal("fire"), 0.001, "Fire defence");
        near(1152.00, s.defenceTotal("air"), 0.001, "Air defence");
        near(35, 25 + s.stat("manaRegen"), 0.001, "Mana regen with base");
        near(-119, s.stat("lifeSteal"), 0.001, "Life steal");
        System.out.println("[wb] mana " + s.mana());
    }
}
