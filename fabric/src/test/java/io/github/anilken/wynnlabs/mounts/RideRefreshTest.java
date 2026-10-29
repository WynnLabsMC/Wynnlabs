package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.mounts.RideRefresh.Ctx;
import io.github.anilken.wynnlabs.mounts.RideRefresh.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class RideRefreshTest {

    /** What the refresh sent, in order: "slot 3", "open", "close 7". */
    private static final class Sent implements RideRefresh.Net {
        final List<String> log = new ArrayList<>();

        @Override
        public void selectSlot(int slot) {
            log.add("slot " + slot);
        }

        @Override
        public void openInventory() {
            log.add("open");
        }

        @Override
        public void close(int containerId) {
            log.add("close " + containerId);
        }

        long count(String what) {
            return log.stream().filter(s -> s.startsWith(what)).count();
        }
    }

    private static Ctx riding() {
        return new Ctx(true, true, true, true, false, false, 3, true);
    }

    private static Ctx screen() {
        return new Ctx(true, true, true, true, true, false, 3, true);
    }

    /** Ticks from..to (inclusive) in one situation. */
    private static void run(RideRefresh r, long from, long to, Ctx c) {
        for (long t = from; t <= to; t++) {
            r.tick(t, c);
        }
    }

    @Test
    void theHeldSlotIsSentEverySecondWhileRiding() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        run(r, 0, 60, riding());
        assertEquals(List.of("slot 3", "slot 3", "slot 3", "slot 3"), net.log);
    }

    @Test
    void aSignalOpensTheInventoryAfterItSettlesThenSwallowsReadsAndCloses() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        run(r, 0, 0, riding());
        net.log.clear();
        r.signal(100);
        run(r, 101, 103, riding());
        assertEquals(0, net.count("open"), "not before the debounce");
        r.signal(103);                                // a second change restarts it
        run(r, 104, 106, riding());
        assertEquals(0, net.count("open"));
        run(r, 107, 107, riding());
        assertEquals(1, net.count("open"));
        assertEquals(State.PENDING, r.state());
        net.log.clear();
        assertTrue(r.onOpenScreen(7), "our answer: the client shows nothing");
        assertEquals(State.OPEN, r.state());
        assertFalse(r.onContent(0, () -> { }), "the player's own inventory contents go on");
        boolean[] read = {false};
        assertTrue(r.onContent(7, () -> {
            assertFalse(net.log.contains("close 7"), "read before the close");
            read[0] = true;
        }));
        assertTrue(read[0]);
        assertEquals(List.of("close 7"), net.log);
        assertEquals(State.IDLE, r.state());
        assertFalse(r.onOpenScreen(8), "a later menu is not ours");
    }

    @Test
    void aScreenThePlayerOpensCancelsOursAndIsLeftAlone() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        run(r, 0, 0, riding());
        r.signal(0);
        run(r, 1, 4, riding());
        assertEquals(State.PENDING, r.state());
        net.log.clear();
        r.tick(5, screen());
        assertEquals(State.IDLE, r.state());
        assertFalse(r.onOpenScreen(9), "their menu opens as usual");
        assertTrue(net.log.isEmpty(), "nothing closed under the player");
    }

    @Test
    void anOpenMenuIsForgottenWithoutACloseWhenThePlayerOpensAScreen() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        r.signal(0);
        run(r, 0, 4, riding());
        r.onOpenScreen(4);
        net.log.clear();
        r.tick(5, screen());
        assertEquals(State.IDLE, r.state());
        assertFalse(r.onContent(4, () -> { }));
        assertTrue(net.log.isEmpty());
    }

    @Test
    void usingOrAttackingCancelsAWaitingRequest() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        r.signal(0);
        run(r, 0, 4, riding());
        assertEquals(State.PENDING, r.state());
        r.tick(5, new Ctx(true, true, true, true, false, true, 3, true));
        assertEquals(State.IDLE, r.state());
        assertFalse(r.onOpenScreen(2), "an NPC's or chest's menu could be theirs");
    }

    @Test
    void nothingIsSentWhileAScreenIsOpen() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        r.signal(0);
        run(r, 0, 200, screen());
        assertTrue(net.log.isEmpty());
    }

    @Test
    void aMountInAnotherSlotIsNeverSelectedButStillRefreshedThroughTheInventory() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        Ctx other = new Ctx(true, true, true, true, false, false, 5, false);
        run(r, 0, 100, other);
        assertTrue(net.log.isEmpty(), "the held slot holds something else: no slot packet at all");
        r.signal(100);
        run(r, 101, 104, other);
        assertEquals(List.of("open"), net.log);
    }

    @Test
    void withThePanelOffNothingIsEverSent() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        Ctx off = new Ctx(true, false, true, true, false, false, 3, true);
        for (long t = 0; t < 400; t++) {
            if (t % 7 == 0) {
                r.signal(t);
            }
            r.tick(t, off);
        }
        assertTrue(net.log.isEmpty());
        assertFalse(r.onOpenScreen(5));
    }

    @Test
    void withTheRefreshOffNotRidingOrNoMountNothingIsSent() {
        for (Ctx c : List.of(new Ctx(true, true, false, true, false, false, 0, true), new Ctx(false, true, true, true, false, false, 0, true),
                new Ctx(true, true, true, false, false, false, 0, true))) {
            Sent net = new Sent();
            RideRefresh r = new RideRefresh(net);
            r.signal(0);
            run(r, 0, 100, c);
            assertTrue(net.log.isEmpty(), c.toString());
        }
    }

    @Test
    void aRequestWithoutAnswerTimesOutAndAnOpenMenuWithoutContentsIsClosed() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        r.signal(0);
        run(r, 0, 4, riding());
        assertEquals(State.PENDING, r.state());
        run(r, 5, 4 + RideRefresh.TIMEOUT + 1, riding());
        assertEquals(State.IDLE, r.state());
        assertFalse(r.onOpenScreen(3), "too late: no longer ours");

        Sent net2 = new Sent();
        RideRefresh r2 = new RideRefresh(net2);
        r2.signal(0);
        run(r2, 0, 4, riding());
        r2.onOpenScreen(6);
        run(r2, 5, 4 + RideRefresh.TIMEOUT + 1, riding());
        assertEquals(State.IDLE, r2.state());
        assertTrue(net2.log.contains("close 6"));
    }

    @Test
    void refreshesKeepTheirGapSoADrainingBarIsNotOneASecond() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        long opens = 0;
        for (long t = 0; t < 200; t++) {
            if (t % 6 == 0) {
                r.signal(t);                          // a frame every 6 ticks: each settles before the next
            }
            r.tick(t, riding());
            if (r.state() == State.PENDING) {
                r.onOpenScreen(1);
                r.onContent(1, () -> { });
                opens++;
            }
        }
        assertTrue(opens <= 200 / RideRefresh.MIN_GAP + 1, "opens " + opens);
        assertTrue(opens >= 3, "opens " + opens);
    }

    /** Riding with the mount panel off and the powerup labels on (or off). */
    private static Ctx labelsOnly(boolean labels) {
        return new Ctx(true, false, true, true, false, false, 3, true, labels);
    }

    @Test
    void anEnergyBoostInSightAsksForOneRefreshWithThePanelOff() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        run(r, 0, 40, labelsOnly(true));
        assertEquals(List.of(), net.log, "no slot re-selects and no opens without the panel");
        r.signal(41);                                 // the action bar's signals serve the panel only
        run(r, 41, 60, labelsOnly(true));
        assertEquals(0, net.count("open"));
        assertTrue(r.ask(61));
        run(r, 61, 61, labelsOnly(true));
        assertEquals(List.of("open"), net.log);
        assertTrue(r.onOpenScreen(9));
        assertTrue(r.onContent(9, () -> { }));
        assertEquals(List.of("open", "close 9"), net.log);
    }

    @Test
    void theLabelsAskAtMostOnceEveryFiveSeconds() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        int opens = 0;
        for (long t = 0; t < 300; t++) {
            r.ask(t);                                 // the scan asks every time it sees the boost
            r.tick(t, labelsOnly(true));
            if (r.state() == State.PENDING) {
                opens++;
                r.onOpenScreen(5);
                r.onContent(5, () -> { });
            }
        }
        assertEquals(3, opens);                       // at 0, 100 and 200
        assertFalse(r.ask(250));
    }

    @Test
    void withTheLabelsOffAnAskSendsNothing() {
        Sent net = new Sent();
        RideRefresh r = new RideRefresh(net);
        r.ask(0);
        run(r, 0, 200, labelsOnly(false));
        assertEquals(List.of(), net.log);
    }
}
