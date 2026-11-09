package io.github.anilken.wynnlabs.crafting;

import io.github.anilken.wynnlabs.search.Area;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A gather step's spot list: nodes grouped into fields, then listed near and small to far and big. */
class GatherSpotsTest {

    /** {@code n} nodes side by side from x (2 blocks apart: one field). */
    private static void field(List<Area> nodes, int x, int n) {
        for (int i = 0; i < n; i++) {
            nodes.add(Area.point(x + 2 * i, 60, 0));
        }
    }

    private static ProfessionPlan.Resource salmon(List<Area> nodes) {
        return new ProfessionPlan.Resource("Salmon", "fishing", 20, nodes, List.of("Salmon Oil"));
    }

    @Test
    void aFieldIsGatheredAroundTheNodeWithMostAroundIt() {
        List<Area> nodes = new ArrayList<>();
        for (int x : new int[] {0, 20, 40, 60, 80}) {
            nodes.add(Area.point(x, 60, 0));
        }
        List<ProfessionPlan.Field> f = ProfessionPlan.fields(nodes);
        assertEquals(1, f.size(), "one field, not a row per node");
        assertEquals(5, f.get(0).nodes());
        assertEquals(20, f.get(0).node().x(), "the first node with all five within reach");
    }

    @Test
    void aFringeTouchingABiggerFieldIsThatField() {
        List<Area> nodes = new ArrayList<>();
        for (int x : new int[] {0, 10, 20, 30, 50, 100, 140}) {
            nodes.add(Area.point(x, 60, 0));
        }
        field(nodes, 1000, 3);
        List<ProfessionPlan.Field> f = ProfessionPlan.fields(nodes);
        assertEquals(2, f.size(), "the field with its fringe, and the one far off");
        assertEquals(7, f.get(0).nodes(), "the node at 140 joins its neighbour's field");
        assertEquals(50, f.get(0).node().x());
        assertEquals(3, f.get(1).nodes());
        assertEquals(50, ProfessionPlan.fieldNode(f, Area.point(140, 60, 0)).x(), "the field that holds a node");
        assertNull(ProfessionPlan.fieldNode(f, Area.point(5000, 60, 0)));
    }

    @Test
    void spotsAreAlwaysApart() {
        List<Area> nodes = new ArrayList<>();
        for (int x = 0; x <= 600; x += 30) {
            nodes.add(Area.point(x, 60, (x / 30) % 2 * 20));       // a long ribbon of nodes
        }
        List<ProfessionPlan.Field> f = ProfessionPlan.fields(nodes);
        for (int i = 0; i < f.size(); i++) {
            for (int j = i + 1; j < f.size(); j++) {
                Area a = f.get(i).node();
                Area b = f.get(j).node();
                assertTrue(Math.hypot(a.x() - b.x(), a.z() - b.z()) > 64, "two spots never on one patch: " + a + " " + b);
            }
        }
        assertEquals(nodes.size(), f.stream().mapToInt(ProfessionPlan.Field::nodes).sum(), "every node in one field");
    }

    @Test
    void eachNextSpotIsFartherAndBigger() {
        List<Area> nodes = new ArrayList<>();
        field(nodes, 100, 4);
        field(nodes, 300, 1);            // a lone node while real fields exist: never listed
        field(nodes, 500, 6);
        field(nodes, 900, 5);            // farther and smaller than the one at 500: only to fill in
        field(nodes, 1500, 10);
        ProfessionPlan.Resource r = salmon(nodes);
        double[] pos = {0, 0};
        List<ProfessionPlan.Spot> three = ProfessionPlan.spotsFor(r, pos, 3);
        assertEquals(List.of(4, 6, 10), three.stream().map(ProfessionPlan.Spot::nodes).toList());
        List<ProfessionPlan.Spot> four = ProfessionPlan.spotsFor(r, pos, 4);
        assertEquals(List.of(4, 6, 5, 10), four.stream().map(ProfessionPlan.Spot::nodes).toList(),
                "too few: the nearest of the rest fills in, all by distance");
        assertEquals(4, ProfessionPlan.spotsFor(r, pos, 10).size(), "the lone node is left out");
        assertEquals(List.of(4, 6), ProfessionPlan.spotsFor(r, pos, 2).stream().map(ProfessionPlan.Spot::nodes).toList(),
                "the limit keeps the nearest of the list");
        assertTrue(three.get(0).distance() < three.get(1).distance() && three.get(1).distance() < three.get(2).distance());
    }

    @Test
    void withoutAPositionTheBiggestComeFirst() {
        List<Area> nodes = new ArrayList<>();
        field(nodes, 100, 2);
        field(nodes, 500, 6);
        field(nodes, 1500, 10);
        List<ProfessionPlan.Spot> s = ProfessionPlan.spotsFor(salmon(nodes), null, 2);
        assertEquals(List.of(10, 6), s.stream().map(ProfessionPlan.Spot::nodes).toList());
        assertTrue(Double.isNaN(s.get(0).distance()));
        assertTrue(ProfessionPlan.spotsFor(salmon(List.of()), new double[] {0, 0}, 4).isEmpty(), "no nodes, no spots");
    }
}
