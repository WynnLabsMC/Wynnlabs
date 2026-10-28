package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The suggestions' filters: goal groups, wearable, the search, a set's status and what is kept. */
class SuggestionFilterTest {

    @Test
    void goalGroupsMatchGoalsAndAnyPartOfACombination() {
        SuggestionFilter f = new SuggestionFilter();
        assertTrue(f.goalMatches("loot"), "no group: everything");
        f.groups.add("mana");
        assertTrue(f.goalMatches("mana"));
        assertFalse(f.goalMatches("rawspell"));
        assertTrue(f.goalMatches("combo:rawspell+mana+spellcost"), "a combination with mana");
        assertFalse(f.goalMatches("combo:melee+attackspeed"));
        f.groups.add("elements");
        assertTrue(f.goalMatches("thunder"));
        assertTrue(f.goalMatches("combo:water+rawspell"));
        assertTrue(f.active());
    }

    @Test
    void wearableComparesEachSkillAndHidesNothingWhilePointsAreUnknown() {
        assertTrue(SuggestionFilter.wearable(new int[] {0, 0, 60, 0, 0}, new int[] {0, 0, 60, 0, 10}));
        assertFalse(SuggestionFilter.wearable(new int[] {0, 0, 61, 0, 0}, new int[] {0, 0, 60, 0, 10}));
        assertTrue(SuggestionFilter.wearable(new int[] {0, 0, 99, 0, 0}, new int[5]));
        assertTrue(SuggestionFilter.wearable(new int[] {0, 0, 99, 0, 0}, null));
    }

    @Test
    void theSearchNeedsEveryWordInSomeName() {
        List<String> names = List.of("Raw spell", "Leggings", "Foul Fairy Dust", "Obelisk Core");
        assertTrue(SuggestionFilter.queryMatches("", names));
        assertTrue(SuggestionFilter.queryMatches("fairy", names));
        assertTrue(SuggestionFilter.queryMatches("LEGG obelisk", names));
        assertFalse(SuggestionFilter.queryMatches("fairy helmet", names));
    }

    @Test
    void aSetCountsAsItsWorstPiece() {
        assertEquals(SuggestionBoard.Status.LEVEL, SuggestionFilter.setStatus(Map.of(SuggestionBoard.Status.READY, List.of(1),
                SuggestionBoard.Status.BUY, List.of(1), SuggestionBoard.Status.LEVEL, List.of(1))));
        assertEquals(SuggestionBoard.Status.BUY, SuggestionFilter.setStatus(Map.of(SuggestionBoard.Status.READY, List.of(1),
                SuggestionBoard.Status.BUY, List.of(1), SuggestionBoard.Status.LEVEL, List.of())));
        assertEquals(SuggestionBoard.Status.READY, SuggestionFilter.setStatus(Map.of(SuggestionBoard.Status.READY, List.of(1),
                SuggestionBoard.Status.BUY, List.of(), SuggestionBoard.Status.LEVEL, List.of())));
    }

    @Test
    void filtersAreKeptAndClear() {
        SuggestionFilter f = new SuggestionFilter();
        f.groups.add("spell");
        f.groups.add("nonsense");
        f.wearable = true;
        f.query = "fairy";
        f.kind = "armour";
        f.level = 105;
        f.singles = SuggestionBoard.Status.READY;
        f.sets = SuggestionBoard.Status.BUY;
        SuggestionFilter back = SuggestionFilter.fromJson(f.toJson());
        assertEquals(List.of("spell"), List.copyOf(back.groups), "unknown groups are dropped");
        assertTrue(back.wearable);
        assertEquals("fairy", back.query);
        assertEquals("armour", back.kind);
        assertEquals(105, back.level);
        assertEquals(SuggestionBoard.Status.READY, back.singles);
        assertEquals(SuggestionBoard.Status.BUY, back.sets);
        back.clear();
        assertFalse(back.active());
        assertNull(SuggestionFilter.fromJson("junk").kind);
    }
}
