package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PowerupLabelsTest {

    @Test
    void theScaleGrowsWithDistanceFromHalfANameTag() {
        // (d × 0.01 + 0.025) × 0.5 at 100 %
        assertEquals(0.0125f, PowerupLabels.scale(0, 100, 1f), 1e-6);
        assertEquals(0.0625f, PowerupLabels.scale(10, 100, 1f), 1e-6);
        assertEquals(0.3325f, PowerupLabels.scale(64, 100, 1f), 1e-6);
        // the size setting scales the 0.5 base: 75 % → 0.375, 150 % → 0.75
        assertEquals(0.046875f, PowerupLabels.scale(10, 75, 1f), 1e-6);
        assertEquals(0.09375f, PowerupLabels.scale(10, 150, 1f), 1e-6);
        // a partial Energy Boost is 0.75 of that
        assertEquals(0.046875f, PowerupLabels.scale(10, 100, 0.75f), 1e-6);
    }

    @Test
    void aMaxedStatsTextIsFadedToThirtyPercent() {
        assertEquals(0xFFFDDA61, PowerupLabels.colour(0xFDDA61, false));
        assertEquals(77 << 24 | 0xFDDA61, PowerupLabels.colour(0xFDDA61, true));
    }
}
