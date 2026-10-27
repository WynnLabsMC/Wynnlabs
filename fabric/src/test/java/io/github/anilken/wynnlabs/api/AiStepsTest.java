package io.github.anilken.wynnlabs.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The AI page's state and its three setup steps. */
class AiStepsTest {

    @Test
    void theStateFollowsTheSwitchTheServerAndTheRequests() {
        assertEquals(AiSteps.State.OFF, AiSteps.state(false, "listening", 5));
        assertEquals(AiSteps.State.OFF, AiSteps.state(true, "off", 0), "switched on, not listening yet");
        assertEquals(AiSteps.State.BUSY, AiSteps.state(true, "busy", 0));
        assertEquals(AiSteps.State.NEW, AiSteps.state(true, "listening", 0));
        assertEquals(AiSteps.State.CONNECTED, AiSteps.state(true, "listening", 1234));
    }

    @Test
    void stepsTickInOrderAndTheFirstRequestDoesTheLast() {
        assertArrayEquals(new boolean[] {false, false, false}, AiSteps.done(AiSteps.State.OFF, true, 0), "nothing before the API");
        assertEquals(0, AiSteps.next(AiSteps.done(AiSteps.State.OFF, false, 0)));
        assertArrayEquals(new boolean[] {true, false, false}, AiSteps.done(AiSteps.State.NEW, false, 0));
        assertEquals(1, AiSteps.next(AiSteps.done(AiSteps.State.NEW, false, 0)));
        assertArrayEquals(new boolean[] {true, true, false}, AiSteps.done(AiSteps.State.NEW, true, 0));
        assertEquals(2, AiSteps.next(AiSteps.done(AiSteps.State.NEW, true, 0)));
        assertArrayEquals(new boolean[] {true, true, true}, AiSteps.done(AiSteps.State.CONNECTED, false, 99),
                "a request came: the tool is set up whatever this session did");
        assertEquals(-1, AiSteps.next(AiSteps.done(AiSteps.State.CONNECTED, false, 99)));
        assertArrayEquals(new boolean[] {false, false, false}, AiSteps.done(AiSteps.State.BUSY, true, 0));
    }

    @Test
    void everyEndpointHasAGroupOfThePageAndTheWritesAreTheThree() {
        for (AiLink.Endpoint e : AiLink.ENDPOINTS) {
            assertEquals(true, e.write() || AiLink.GROUPS.contains(e.group()), e.path());
        }
        assertEquals(3, AiLink.ENDPOINTS.stream().filter(AiLink.Endpoint::write).count());
        assertEquals(26, AiLink.ENDPOINTS.stream().filter(e -> !e.write()).count(), "the 20 read routes before and the 9 missing, less the 3 writes");
    }
}
