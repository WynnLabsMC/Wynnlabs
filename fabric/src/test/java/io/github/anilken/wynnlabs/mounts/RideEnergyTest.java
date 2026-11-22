package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.anilken.wynnlabs.mounts.RideEnergy.Shown;
import org.junit.jupiter.api.Test;

class RideEnergyTest {

    @Test
    void withoutWynntilsTheItemsPointsShow() {
        assertEquals(new Shown(196, 204, false), RideEnergy.shown(196, 204, null, -1));
    }

    @Test
    void aFrameOutOf48IsNeverShownAsPointsButEstimatedFromTheItemsMax() {
        // the in-game case: Wynntils [44, 48], the flute 196/204, the bar moved since the read
        assertEquals(new Shown(187, 204, true), RideEnergy.shown(196, 204, new int[] {44, 48}, 46));
        assertEquals(new Shown(0, 204, true), RideEnergy.shown(196, 204, new int[] {0, 48}, 46));
        assertEquals(new Shown(204, 204, true), RideEnergy.shown(196, 204, new int[] {48, 48}, 46));
    }

    @Test
    void aFrameOutOf47FromWynntils4219IsEstimatedToo() {
        // 2.2.6 gave a frame to the toughness bar: Wynntils 4.2.19 reports [frame, 47]
        assertEquals(new Shown(191, 204, true), RideEnergy.shown(196, 204, new int[] {44, 47}, 46));
        assertEquals(new Shown(204, 204, true), RideEnergy.shown(196, 204, new int[] {47, 47}, 46));
        assertEquals(new Shown(196, 204, false), RideEnergy.shown(196, 204, new int[] {46, 47}, 46));
    }

    @Test
    void theItemsNumberHoldsUntilTheFrameMovesAfterARead() {
        assertEquals(new Shown(196, 204, false), RideEnergy.shown(196, 204, new int[] {46, 48}, 46));
    }

    @Test
    void anyOtherMaxFromWynntilsIsRealPoints() {
        assertEquals(new Shown(180, 204, false), RideEnergy.shown(196, 204, new int[] {180, 204}, -1));
    }
}
