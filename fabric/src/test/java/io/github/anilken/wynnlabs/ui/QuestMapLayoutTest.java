package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest map's fit, merging and chip placement (pure arithmetic). */
class QuestMapLayoutTest {

    // Jungle Fever's places in Almuj and Worid
    private static final List<double[]> PLACES = List.of(new double[] {1019, -1976}, new double[] {998, -2030},
            new double[] {1019, -1991}, new double[] {956, -1960});

    @Test
    void thePlayerJoinsTheMapOnlyWhenNear() {
        QuestMapLayout.Fit far = QuestMapLayout.fit(PLACES, new double[] {-1000, -1350}, 260, 220, 1);
        assertFalse(far.near(), "2 km away: an arrow on the border instead");
        QuestMapLayout.Fit near = QuestMapLayout.fit(PLACES, new double[] {900, -1900}, 260, 220, 1);
        assertTrue(near.near());
        assertTrue(near.px(900) >= 0 && near.px(900) <= 260 && near.py(-1900) >= 0 && near.py(-1900) <= 220);
    }

    @Test
    void theMapNeverZoomsPastOneBlockAPixel() {
        QuestMapLayout.Fit one = QuestMapLayout.fit(List.of(new double[] {100, 100}), null, 200, 100, 1);
        assertEquals(QuestMapLayout.MIN_SPAN, one.span());
        QuestMapLayout.Fit in = QuestMapLayout.fit(PLACES, null, 260, 220, 50);
        assertEquals(QuestMapLayout.MIN_SPAN, in.span(), "the + button stops at the closest zoom");
    }

    @Test
    void theRouteFollowsTheStagesWhereThePlacesReallyAreAfterAPan() {
        // 6.1 north of the view, 6.2 and 6.3 in it, 14.1 east: the route goes 6.1 → 6.2 → 6.3 → 14.1
        List<double[]> places = List.of(new double[] {100, -400}, new double[] {100, 100}, new double[] {150, 120},
                new double[] {900, 120});
        QuestMapLayout.Fit panned = new QuestMapLayout.Fit(0, 0, 1, false);
        List<double[]> legs = QuestMapLayout.route(panned, places, 260, 220);
        assertEquals(3, legs.size(), "every leg in stage order, none skipped");
        double[] first = legs.get(0);
        assertEquals(100, first[0], 1e-6, "toward 6.1 straight up, not to an edge chip");
        assertEquals(0, first[1], 1e-6, "clipped at the top border");
        assertEquals(100, first[2], 1e-6);
        assertEquals(100, first[3], 1e-6);
        double[] last = legs.get(2);
        assertEquals(150, last[0], 1e-6, "from 6.3");
        assertEquals(260, last[2], 1e-6, "to the right border on the way to 14.1");
        assertEquals(120, last[3], 1e-6);
        // panned further: the same places, projected again
        List<double[]> moved = QuestMapLayout.route(new QuestMapLayout.Fit(50, 0, 1, false), places, 260, 220);
        assertEquals(50, moved.get(0)[0], 1e-6, "the route moves with the pan");
    }

    @Test
    void thePillSaysHowFarYouAreFromTheView() {
        QuestMapLayout.Fit view = new QuestMapLayout.Fit(0, 0, 1, false);         // blocks 0..260, 0..220
        assertEquals(0, QuestMapLayout.offView(view, 260, 220, new double[] {100, 100}), 1e-6, "in the view");
        assertEquals(1740, QuestMapLayout.offView(view, 260, 220, new double[] {2000, 100}), 1e-6, "east of it");
        double[] at = QuestMapLayout.edge(2000, 100, 60, 13, 260, 220, 4);
        assertEquals(260 - 4 - 60, at[0], 1e-6, "the pill on the right border, toward you");
    }

    @Test
    void edgeChipsStayOnTheBorderWhenTheyMakeRoom() {
        List<double[]> placed = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double[] at = QuestMapLayout.edge(-500, 100 + i, 40, 11, 260, 220, 4);
            at = QuestMapLayout.onBorder(at, 40, 11, 260, 220, 4, placed);
            assertEquals(4, at[0], 1e-6, "on the left border, chip " + i);
            assertTrue(QuestMapLayout.inside(at[0], at[1], 40, 11, 260, 220, 4));
            for (double[] b : placed) {
                assertFalse(at[1] < b[1] + b[3] && b[1] < at[1] + 11, "chips do not overlap");
            }
            placed.add(new double[] {at[0], at[1], 40, 11});
        }
    }

    @Test
    void passedPlacesNeverReachTheMapSoTheRouteStartsAtTheStageYouAreOn() {
        List<QuestMapLayout.Marker> all = List.of(new QuestMapLayout.Marker("4.8", 10, 10, QuestMapLayout.DONE),
                new QuestMapLayout.Marker("4.9", 20, 20, QuestMapLayout.DONE),
                new QuestMapLayout.Marker("5.1", 100, 100, QuestMapLayout.NOW),
                new QuestMapLayout.Marker("5.2", 120, 100, QuestMapLayout.NOW),
                new QuestMapLayout.Marker("6.1", 200, 150, QuestMapLayout.NEXT));
        List<QuestMapLayout.Marker> shown = QuestMapLayout.remaining(all, QuestMapLayout.Marker::state);
        assertEquals(List.of("5.1", "5.2", "6.1"), shown.stream().map(QuestMapLayout.Marker::num).toList());
        List<double[]> xz = shown.stream().map(m -> new double[] {m.x(), m.y()}).toList();
        List<double[]> legs = QuestMapLayout.route(new QuestMapLayout.Fit(0, 0, 1, false), xz, 260, 220);
        assertEquals(2, legs.size(), "5.1 → 5.2 → 6.1, nothing from the passed places");
        assertEquals(100, legs.get(0)[0], 1e-6, "the route starts at the stage you are on");
    }

    @Test
    void aLegWhollyOutsideTheFrameIsLeftOut() {
        assertEquals(null, QuestMapLayout.clip(-50, -50, -10, -80, 260, 220));
    }

    @Test
    void farPlacesDoNotZoomTheMapIntoGrain() {
        List<double[]> spread = List.of(new double[] {-1615, -2924}, new double[] {-1600, -4500});
        QuestMapLayout.Fit f = QuestMapLayout.fit(spread, null, 260, 220, 1);
        assertEquals(QuestMapLayout.MAX_SPAN, f.span(), "never past one block a screen pixel");
        assertTrue(QuestMapLayout.onMap(f.px(-1615), f.py(-2924), 260, 220), "the first place stays in view");
        assertFalse(QuestMapLayout.onMap(f.px(-1600), f.py(-4500), 260, 220), "the far one goes to an edge chip");
        double[] e = QuestMapLayout.edge(f.px(-1600), f.py(-4500), 40, 11, 260, 220, 4);
        assertTrue(QuestMapLayout.inside(e[0], e[1], 40, 11, 260, 220, 4));
        assertTrue(e[1] < 70, "on the top border: the place lies north");
    }

    @Test
    void aFarPlayerNeverStretchesTheMap() {
        QuestMapLayout.Fit f = QuestMapLayout.fit(PLACES, new double[] {-200, 500}, 260, 220, 1);
        assertFalse(f.near());
        assertTrue(f.span() <= QuestMapLayout.MAX_SPAN);
    }

    @Test
    void everyPlaceFitsInsideTheFrame() {
        QuestMapLayout.Fit f = QuestMapLayout.fit(PLACES, null, 260, 220, 1);
        for (double[] p : PLACES) {
            assertTrue(f.px(p[0]) > 10 && f.px(p[0]) < 250, "x " + p[0]);
            assertTrue(f.py(p[1]) > 10 && f.py(p[1]) < 210, "z " + p[1]);
        }
    }

    @Test
    void closePlacesMergeButAPassedOneNeverJoinsALiveOne() {
        List<QuestMapLayout.Marker> m = List.of(new QuestMapLayout.Marker("5.1", 100, 100, QuestMapLayout.NOW),
                new QuestMapLayout.Marker("5.3", 104, 103, QuestMapLayout.NEXT),
                new QuestMapLayout.Marker("4.1", 102, 101, QuestMapLayout.DONE),
                new QuestMapLayout.Marker("1.1", 101, 99, QuestMapLayout.START));
        List<QuestMapLayout.Group> g = QuestMapLayout.merge(m);
        assertEquals(3, g.size());
        assertEquals("5.1–5.3", g.get(0).label());
        assertEquals(QuestMapLayout.NOW, g.get(0).state(), "the merged marker takes the most current state");
    }

    @Test
    void chipsStayInsideTheFrameAndOffEveryDot() {
        List<double[]> dots = List.of(new double[] {12, 12}, new double[] {120, 60}, new double[] {125, 70});
        List<double[]> placed = new ArrayList<>();
        for (double[] d : dots) {
            double[] at = QuestMapLayout.chip(d[0], d[1], 30, 11, 160, 90, 4, dots, placed, List.of());
            assertTrue(QuestMapLayout.inside(at[0], at[1], 30, 11, 160, 90, 4), "chip of " + d[0] + "," + d[1]);
            for (double[] o : dots) {
                assertFalse(o[0] > at[0] && o[0] < at[0] + 30 && o[1] > at[1] && o[1] < at[1] + 11, "covers a dot");
            }
            placed.add(new double[] {at[0], at[1], 30, 11});
        }
    }

    @Test
    void byHandTheMapZoomsOutUntilTheWholeMapFitsAndInCloserThanAFit() {
        float[] continent = {-2500, -6600, 2500, 0};
        double most = QuestMapLayout.handMaxSpan(continent, 400, 300);
        org.junit.jupiter.api.Assertions.assertEquals(22.0, most, 0.01);           // 6600 blocks over 300 px
        org.junit.jupiter.api.Assertions.assertEquals(QuestMapLayout.MAX_SPAN, QuestMapLayout.handMaxSpan(null, 400, 300));
        org.junit.jupiter.api.Assertions.assertTrue(QuestMapLayout.HAND_MIN_SPAN < QuestMapLayout.MIN_SPAN);
    }

    @Test
    void deathsAreSeparateEventsSoTheirMapHasNoRouteLegs() {
        java.util.List<double[]> deaths = java.util.List.of(new double[] {20, 20}, new double[] {120, 80}, new double[] {200, 150});
        QuestMapLayout.Fit f = new QuestMapLayout.Fit(0, 0, 1, false);
        org.junit.jupiter.api.Assertions.assertTrue(QuestMapLayout.route(false, f, deaths, 260, 220).isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(QuestMapLayout.route(true, f, deaths, 260, 220).isEmpty());
    }

    @Test
    void aFocusedMapShowsItsStageTheNextAsGhostsAndAPeekedRow() {
        java.util.List<String> nums = java.util.List.of("4.1", "5.1", "5.2", "6.1", "11.1", "14.6");
        QuestMapLayout.Focus<String> f = QuestMapLayout.focus(nums, n -> n, 5, null);
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("5.1", "5.2"), f.shown());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("6.1"), f.ghosts());
        QuestMapLayout.Focus<String> peek = QuestMapLayout.focus(nums, n -> n, 5, "11.1");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("5.1", "5.2", "11.1"), peek.shown());
    }

    @Test
    void placesOfDifferentStagesNeverMergeAndOneStageMergesOnlyConsecutively() {
        java.util.List<QuestMapLayout.Marker> m = java.util.List.of(
                new QuestMapLayout.Marker("5.11", 100, 100, QuestMapLayout.NOW),
                new QuestMapLayout.Marker("14.6", 101, 100, QuestMapLayout.NEXT),
                new QuestMapLayout.Marker("5.12", 102, 100, QuestMapLayout.NOW),
                new QuestMapLayout.Marker("5.13", 103, 101, QuestMapLayout.NOW));
        java.util.List<QuestMapLayout.Group> g = QuestMapLayout.merge(m, true);
        org.junit.jupiter.api.Assertions.assertEquals(3, g.size());
        org.junit.jupiter.api.Assertions.assertEquals("5.12–5.13", g.get(2).label());
        org.junit.jupiter.api.Assertions.assertEquals("5.9–5.10", new QuestMapLayout.Group(
                new java.util.ArrayList<>(java.util.List.of("5.10", "5.9")), 0, 0, QuestMapLayout.NOW).label());
        // deaths (unstaged): close ones still merge
        org.junit.jupiter.api.Assertions.assertEquals(1, QuestMapLayout.merge(java.util.List.of(
                new QuestMapLayout.Marker("1", 10, 10, QuestMapLayout.NOW),
                new QuestMapLayout.Marker("2", 12, 10, QuestMapLayout.NEXT)), false).size());
    }
}
