package io.github.anilken.wynnlabs.quests;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class StepPlacesTest {

    private static final int[] HOME = {-208, 62, -694};

    @Test
    void aStepWithTwoPlacesInItsTextGivesBothAsAAndB() {
        List<Discoveries.Step> steps = List.of(
                new Discoveries.Step("İki Mysterious Key'i -195, 80, -642 ya da -187, 76, -731 noktasından al.", new int[] {-195, 80, -642}),
                new Discoveries.Step("Kuleye gir ve altın baskı plakasına bas.", null),
                new Discoveries.Step("-208, 62, -694 noktasında bir delik açılır; deliğe gir.", new int[] {-208, 62, -694}));
        List<List<StepPlaces.Mark>> marks = StepPlaces.of(steps, HOME);
        assertEquals(2, marks.get(0).size(), "both keys' places");
        assertEquals("1a", marks.get(0).get(0).label());
        assertEquals("1b", marks.get(0).get(1).label());
        assertArrayEquals(new int[] {-187, 76, -731}, marks.get(0).get(1).at());
        assertTrue(marks.get(0).get(1).inText());
        assertEquals(0, marks.get(1).size());
        assertEquals("3", marks.get(2).get(0).label(), "one place: the step's number alone");
        assertEquals(3, StepPlaces.count(marks));
    }

    @Test
    void aPlaceOnlyInTheDataIsKeptAndMarkedAsNotInTheText() {
        List<List<StepPlaces.Mark>> marks = StepPlaces.of(List.of(new Discoveries.Step("Climb the mountain.", new int[] {281, 94, -804})), HOME);
        assertEquals(1, marks.get(0).size());
        assertFalse(marks.get(0).get(0).inText(), "the row shows it on a line of its own");
    }

    @Test
    void theQueueFromAStepHoldsEveryPlaceAfterIt() {
        List<Discoveries.Step> steps = List.of(
                new Discoveries.Step("Keys at -195, 80, -642 or -187, 76, -731.", null),
                new Discoveries.Step("Door at -209, 68, -784.", null));
        List<int[]> pts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        StepPlaces.from(StepPlaces.of(steps, HOME), 0, pts, labels, "Nilrem");
        assertEquals(List.of("Nilrem 1a", "Nilrem 1b", "Nilrem 2"), labels);
    }
}
