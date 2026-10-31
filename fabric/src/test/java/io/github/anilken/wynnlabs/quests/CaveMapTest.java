package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.game.Wyn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which cave icons on Wynntils' map fade: the content book's COMPLETED caves, matched by name. */
class CaveMapTest {

    private static Wyn.ContentLine cave(String name, String status) {
        return new Wyn.ContentLine(name, status, 20, "");
    }

    @Test
    void onlyCompletedCavesAreDone() {
        Set<String> done = CaveMap.doneNames(List.of(
                cave("Abandoned Steelworks", "COMPLETED"),
                cave("Ancestor Grotto", "AVAILABLE"),
                cave("Abyssal Shine", "STARTED"),
                cave("Aelumia Metalmill", "UNKNOWN"),
                cave("Corrupted Mine", "COMPLETED")));
        assertEquals(Set.of("abandoned steelworks", "corrupted mine"), done);
    }

    @Test
    void mapLabelsMatchIgnoringCaseAndSpacing() {
        Set<String> done = CaveMap.doneNames(List.of(cave(" Abandoned Steelworks ", "COMPLETED")));
        assertTrue(CaveMap.isDone("Abandoned Steelworks", done));
        assertTrue(CaveMap.isDone("abandoned steelworks", done));
        assertFalse(CaveMap.isDone("Ancestor Grotto", done), "a cave still to do keeps Wynntils' colours");
        assertFalse(CaveMap.isDone(null, done), "a feature without a label is left alone");
    }

    @Test
    void nothingFadesWithoutCompletedCaves() {
        Set<String> done = CaveMap.doneNames(List.of(cave("Ancestor Grotto", "AVAILABLE")));
        assertTrue(done.isEmpty());
        assertFalse(CaveMap.isDone("Ancestor Grotto", done));
    }

    @Test
    void bookAndWynntilsListsAreUnited() {
        // the usual case: the character's book copy has the caves, Wynntils' own model is empty
        List<Wyn.ContentLine> book = List.of(cave("Hill Hovel", "COMPLETED"), cave("Wolf Den", "AVAILABLE"));
        assertEquals(Set.of("hill hovel"), CaveMap.doneNames(book, List.of()));
        // and the other way round, or both with something: a cave finished in either is done
        assertEquals(Set.of("hill hovel"), CaveMap.doneNames(List.of(), book));
        assertEquals(Set.of("hill hovel", "wolf den"),
                CaveMap.doneNames(book, List.of(cave("Wolf Den", "COMPLETED"))));
        assertTrue(CaveMap.doneNames(List.of(), List.of()).isEmpty());
    }

    @Test
    void oddLinesAreSkipped() {
        Set<String> done = CaveMap.doneNames(List.of(cave(null, "COMPLETED"), cave("X", null)));
        assertTrue(done.isEmpty());
    }

    @Test
    void coloursAreRedAndDimmed() {
        // both colours are reds (red channel well above green and blue) and below full alpha, so a
        // finished cave reads as faded next to the untouched ones
        for (int argb : new int[] {CaveMap.ICON_ARGB, CaveMap.LABEL_ARGB}) {
            int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            assertTrue(r > g + 0x40 && r > b + 0x40, "red: " + Integer.toHexString(argb));
            assertTrue(a < 0xFF, "not fully opaque: " + Integer.toHexString(argb));
        }
    }
}
