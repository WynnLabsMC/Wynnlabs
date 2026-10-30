package io.github.anilken.wynnlabs.game;

import io.github.anilken.wynnlabs.model.SkillKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The tomes read from the Mastery Tomes menu, and which source wins. */
class TomeMemoryTest {

    private static List<TomeMemory.Seen> menu() {
        return Arrays.asList(
                new TomeMemory.Seen("Mythical Armour Tome of Trolls' Adamance", Map.of()),
                new TomeMemory.Seen("Tome of Allegiance", Map.of(SkillKind.STRENGTH, 1, SkillKind.DEXTERITY, 1,
                        SkillKind.INTELLIGENCE, 1, SkillKind.DEFENCE, 1, SkillKind.AGILITY, 1)),
                new TomeMemory.Seen("Tome of Allegiance", Map.of(SkillKind.STRENGTH, 2)),
                new TomeMemory.Seen(" ", Map.of(SkillKind.AGILITY, 9)),     // no name: not a tome
                null);
    }

    @Test
    void readsNamesAndSumsSkillPoints() {
        TomeMemory.Known k = TomeMemory.of(menu());
        assertEquals(List.of("Mythical Armour Tome of Trolls' Adamance", "Tome of Allegiance", "Tome of Allegiance"),
                k.names());
        assertEquals(3, k.points().get(SkillKind.STRENGTH));
        assertEquals(1, k.points().get(SkillKind.AGILITY));
        assertNull(TomeMemory.of(List.of()), "a menu with no tomes yet (still loading) is not remembered");
    }

    @Test
    void survivesSaveAndLoad() {
        TomeMemory.Known k = TomeMemory.of(menu());
        assertEquals(k, TomeMemory.parse(TomeMemory.json(k)));
    }

    @Test
    void wynntilsFirstThenTheMenu() {
        TomeMemory.Known k = TomeMemory.of(menu());
        assertEquals(List.of("Tome A"), TomeMemory.pickNames(List.of("Tome A"), k));
        assertEquals(k.names(), TomeMemory.pickNames(List.of(), k));
        assertEquals(List.of(), TomeMemory.pickNames(List.of(), null));

        // Wynntils read tome points: its number, even a 0 for one skill
        assertEquals(4, TomeMemory.pickPoints(SkillKind.STRENGTH, 4, true, k));
        assertEquals(0, TomeMemory.pickPoints(SkillKind.AGILITY, 0, true, k));
        // Wynntils has none: the menu's tomes
        assertEquals(3, TomeMemory.pickPoints(SkillKind.STRENGTH, 0, false, k));
        assertEquals(1, TomeMemory.pickPoints(SkillKind.DEFENCE, 0, false, k));
        assertEquals(0, TomeMemory.pickPoints(SkillKind.DEFENCE, 0, false, null));
    }
}
