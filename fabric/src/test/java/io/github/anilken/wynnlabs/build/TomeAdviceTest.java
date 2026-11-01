package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tomes offered for a build: by the model, within the level, the slots and the twins. */
class TomeAdviceTest {

    private static final int[] POINTS = {60, 60, 60, 60, 60};

    @BeforeEach
    void setUp() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
    }

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Map<String, Damage.Piece> set() {
        Map<String, Damage.Piece> b = new LinkedHashMap<>();
        b.put("WEAPON", new Damage.Piece(null, "W", "W", Slot.RELIK, 100, Map.of(), Map.of(), 400,
                Map.of("neutral", 1.0), 0, false));
        b.put("HELMET", new Damage.Piece(null, "H", "H", Slot.HELMET, 100, Map.of("rawHealth", 4000.0), Map.of(), 0, Map.of(), 0, false));
        return b;
    }

    private static TomeAdvice.Advice advise(int level, Set<String> owned) {
        Map<String, Damage.Piece> set = set();
        return TomeAdvice.advise(set, new Build.Eval(POINTS, null), level, owned);
    }

    private static Set<String> names(TomeAdvice.Advice a) {
        Set<String> out = new HashSet<>();
        a.rows().forEach(r -> out.add(r.tome().name()));
        return out;
    }

    @Test
    void theListIsFilteredAndSpellCostsAreKeptPositive() {
        List<TomeAdvice.Tome> all = TomeAdvice.catalog();
        assertEquals(89, all.size());
        assertTrue(all.stream().allMatch(t -> t.name().indexOf('֎') < 0));
        assertTrue(all.stream().flatMap(t -> t.stats().entrySet().stream())
                .filter(e -> e.getKey().endsWith("SpellCost")).allMatch(e -> e.getValue() > 0));
        assertEquals(14, Arrays.stream(TomeAdvice.Kind.values()).mapToInt(k -> k.slots).sum());
    }

    @Test
    void noTomeAboveTheMilestoneAndNoMoreThanTheSlots() {
        long start = System.nanoTime();
        TomeAdvice.Advice a = advise(100, Set.of());
        System.out.println("tome advice took " + (System.nanoTime() - start) / 1_000_000 + " ms");
        assertTrue(a.total() > 0);
        for (TomeAdvice.Kind k : TomeAdvice.Kind.values()) {
            assertTrue(a.rows().stream().filter(r -> r.tome().kind() == k).count() <= k.slots);
        }
        assertTrue(a.rows().stream().allMatch(r -> r.tome().level() <= 100));
        assertTrue(a.unscored().stream().allMatch(r -> r.tome().level() <= 100 && Double.isNaN(r.gain())));
        assertTrue(a.rows().stream().anyMatch(r -> r.tome().level() == 100));
    }

    @Test
    void theGainsAddUpToTheTotal() {
        TomeAdvice.Advice a = advise(120, Set.of());
        double product = a.rows().stream().mapToDouble(r -> 1 + r.gain()).reduce(1, (x, y) -> x * y);
        assertEquals(1 + a.total(), product, 1e-9);
    }

    @Test
    void aTwinOfATomeYouOwnCounts() {
        String name = advise(100, Set.of()).rows().get(0).tome().name();
        TomeAdvice.Advice a = advise(100, Set.of(TomeAdvice.plain(name + "֎")));
        assertTrue(a.rows().stream().anyMatch(r -> r.tome().name().equals(name) && r.owned()));
    }

    @Test
    void anAssumedGuildTomeIsAlreadyCounted() {
        Map<String, Damage.Piece> set = set();
        Build.Eval e = new Build.Eval(POINTS, null, Tomes.ALL);
        TomeAdvice.Advice a = TomeAdvice.advise(set, e, 110, Set.of());
        TomeAdvice.Row guild = a.rows().stream().filter(r -> r.tome().kind() == TomeAdvice.Kind.GUILD).findFirst().orElseThrow();
        assertEquals("Assimilator's Tome of Allegiance", guild.tome().name());
        assertTrue(guild.counted());
        assertEquals(1, a.rows().stream().filter(r -> r.tome().kind() == TomeAdvice.Kind.GUILD).count());
    }

    @Test
    void belowTheFirstTomesThereIsNoList() {
        assertNull(TomeAdvice.forSet(set(), new Build.Eval(POINTS, null), 59, null));
        assertNotNull(TomeAdvice.forSet(set(), new Build.Eval(POINTS, null), 60, null));
    }
}
