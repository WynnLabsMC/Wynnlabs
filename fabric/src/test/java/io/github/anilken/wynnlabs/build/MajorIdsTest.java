package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.model.SkillKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Major IDs in the build score: the build-wide ones (Sorcery, Transcendence, Divine Right), a
 * spell's conversion (Fissure), a lasting spell's clock (Furious Effigy), one that needs a tree
 * node (Thunderous Strike with Fireworks), and two copies of one counting once.
 */
class MajorIdsTest {

    @BeforeEach
    void rules() {
        Rules.use(JsonParser.parseString("""
                {"model": {"spells": [
                  {"name": "Ice Snake", "index": 4, "cost": 30, "share": 0.5, "conv": {"neutral": 1.2, "water": 0.55}},
                  {"name": "Uppercut", "index": 3, "cost": 40, "share": 0.5, "conv": {"neutral": 2.6, "earth": 0.4}},
                  {"name": "Totem", "index": 1, "cost": 30, "share": 1, "conv": {"neutral": 4.5, "air": 4.5},
                   "hits": 75, "every": 0.4, "duration": 30, "lasting": true}
                ]}}""").getAsJsonObject());
    }

    @AfterEach
    void reset() {
        Rules.use(new com.google.gson.JsonObject());
    }

    private static Damage.Piece piece(String major) {
        Map<String, Double> stats = new HashMap<>();
        if (major != null) {
            stats.put(MajorIds.KEY + major, 1.0);
        }
        return new Damage.Piece(null, "p" + major, "p", null, 0, stats, Map.of(), 0, Map.of("neutral", 1.0), 1000);
    }

    private static final Damage.Piece WEAPON = new Damage.Piece(null, "weapon", "weapon", null, 0, new HashMap<>(),
            Map.of(), 300, Map.of("neutral", 1.0), 0);

    private static Damage.Detail score(String... majors) {
        List<Damage.Piece> pieces = new ArrayList<>();
        for (String m : majors) {
            pieces.add(piece(m));
        }
        if (pieces.isEmpty()) {
            pieces.add(piece(null));
        }
        return Damage.detail(pieces, WEAPON, new int[SkillKind.values().length], 100);
    }

    private static Damage.SpellPart spell(Damage.Detail d, String name) {
        return d.breakdown().spells().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void sorceryRepeatsSpellsAndAttacks() {
        Damage.Result none = score().result();
        Damage.Result sorcery = score("Sorcery").result();
        assertEquals(none.spellDps() * 1.25, sorcery.spellDps(), 1e-6);
        assertEquals(none.mainDps() * 1.25, sorcery.mainDps(), 1e-6);
        assertEquals(none.dmgPerCast(), sorcery.dmgPerCast(), 1e-6, "one cast hits as hard as before");
    }

    @Test
    void twoCopiesCountOnce() {
        // the same pieces, one of them without it: the same score
        assertEquals(score("Sorcery", null).result().objective(), score("Sorcery", "Sorcery").result().objective(), 1e-6);
    }

    @Test
    void differentOnesAddUp() {
        Damage.Result both = score("Sorcery", "Transcendence").result();
        // Sorcery's x1.25, and Transcendence's free casts leave mana for more of them
        assertTrue(both.spellDps() >= score().result().spellDps() * 1.25 - 1e-6);
        assertEquals(score("Transcendence").result().spellDps() * 1.25, both.spellDps(), 1e-6);
        assertTrue(both.costCut() > 0.2, "a quarter of the casts free");
    }

    @Test
    void transcendenceCutsTheCost() {
        assertEquals(0.25, score("Transcendence").result().costCut(), 1e-9);
        assertEquals(0.3125, score("Enlightenment").result().costCut(), 1e-9);
    }

    @Test
    void divineRightTakesThreeQuartersOfTheHealth() {
        assertEquals(score().result().health() * 0.25, score("Divine Right").result().health(), 1e-6);
    }

    @Test
    void fissureRaisesIceSnake() {
        double before = spell(score(), "Ice Snake").perCast();
        double after = spell(score("Fissure"), "Ice Snake").perCast();
        assertEquals((1.75 + 8.5) / 1.75, after / before, 1e-6, "+500% neutral, +350% water");
        assertEquals(spell(score(), "Uppercut").perCast(), spell(score("Fissure"), "Uppercut").perCast(), 1e-9);
    }

    @Test
    void furiousEffigyDoublesTheTotemPerSecondAndItsMana() {
        Damage.SpellPart before = spell(score(), "Totem");
        Damage.SpellPart after = spell(score("Furious Effigy"), "Totem");
        assertEquals(before.perCast(), after.perCast(), 1e-6, "the same ticks a cast");
        assertEquals(before.duration() / 2, after.duration(), 1e-9);
        assertTrue(score("Furious Effigy").result().costCut() < score().result().costCut(), "recast twice as often");
        Damage.SpellPart peaceful = spell(score("Peaceful Effigy"), "Totem");
        assertEquals(before.perCast() / before.duration(), peaceful.perCast() / peaceful.duration(), 1e-6,
                "lasting twice as long: the same damage a second");
    }

    @Test
    void anEffectThatNeedsANodeWaitsForIt() {
        double plain = spell(score("Thunderous Strike"), "Uppercut").perCast();
        assertEquals(spell(score(), "Uppercut").perCast(), plain, 1e-9, "no tree: no Fireworks");
        TreeModel.Overlay withFireworks = new TreeModel.Overlay(100, Map.of(), Set.of(), Set.of(), new double[6], 0, 1,
                TreeBonuses.Sum.NONE, List.of(), List.of(), List.of(), Map.of(), 1, Set.of("Fireworks"));
        double lit = TreeModel.with(withFireworks, () -> spell(score("Thunderous Strike"), "Uppercut").perCast());
        assertEquals((3.0 + 1.2) / 3.0, lit / plain, 1e-6, "+80% thunder, +40% water");
    }

    @Test
    void everyMajorIdOfTheItemDataIsInTheTable() throws Exception {
        String db;
        try (var in = MajorIdsTest.class.getResourceAsStream("/fixtures/shaman-44/geardb.json")) {
            db = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Matcher m = Pattern.compile("\"majorId\":\"([^\"]+)\"").matcher(db);
        int n = 0;
        while (m.find()) {
            assertNotNull(MajorIds.table().get(m.group(1)), m.group(1));
            n++;
        }
        assertTrue(n > 100);
        assertEquals(175, MajorIds.table().size());
    }
}
