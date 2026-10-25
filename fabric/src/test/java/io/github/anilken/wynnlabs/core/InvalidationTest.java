package io.github.anilken.wynnlabs.core;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.coach.Coach;
import io.github.anilken.wynnlabs.market.Prices;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What invalidates what: prices only touch price-dependent caches; equal calibrations change nothing. */
class InvalidationTest {

    @Test
    void aPriceChangeMovesTheStampButNotTheRules() {
        int rules = Rules.version;
        int stamp = Stamp.now();
        Prices.version++;
        assertEquals(rules, Rules.version, "the build plan and damage report key on Rules.version only");
        assertNotEquals(stamp, Stamp.now(), "advice that weighs prices rebuilds");
    }

    @Test
    void anAdviceChangeMovesTheStamp() {
        int stamp = Stamp.now();
        Advice.version++;
        assertNotEquals(stamp, Stamp.now());
    }

    @Test
    void theRulesHashFollowsTheRules() {
        assertEquals(Rules.get().toString().hashCode(), Rules.hash());
        Rules.version++;                            // a change: worked out again, same document, same hash
        assertEquals(Rules.get().toString().hashCode(), Rules.hash());
    }

}
