package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.build.roadmap.RoadmapLocksTest;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.FixtureState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The build editor's changes, kept at once: keep a slot's piece, swap it (the kept mark moves
 * with it, the score is worked out again), take it out, rename, the strip; Recompute's overwrite
 * and its Undo; a delete and its Undo; and the saved file read back with every change in it.
 */
class BuildEditsTest {

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        FixtureState.load(RoadmapLocksTest.shaman()).install("en");
        Locks.rootOverride = tmp;
        Plans.forget();
    }

    @AfterEach
    void reset() {
        Saver.flush();
        Locks.rootOverride = null;
        Plans.forget();
        FixtureState.uninstall();
    }

    @Test
    void aMilestoneSetBecomesABuildOnce() {
        Damage.Piece w = new Damage.Piece(null, "Covetous Thunder", "Covetous Thunder", io.github.anilken.wynnlabs.model.Slot.RELIK, 40, Map.of(), Map.of(), 0, Map.of(), 0, false);
        Plans.Build b = BuildEdits.ofMilestone(Map.of("WEAPON", w), null, 44, 1000);
        assertEquals(Map.of("WEAPON", "Covetous Thunder"), b.items());
        assertTrue(b.name().contains("Covetous Thunder"));
        assertFalse(BuildEdits.inMyBuilds(b));
        Plans.save(b, 1000);
        assertTrue(BuildEdits.inMyBuilds(b));
        assertNull(BuildEdits.ofMilestone(Map.of(), null, 44, 1000));
    }

    private static Plans.Build saved() {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("HELMET", "Yume");
        items.put("WEAPON", "Covetous Thunder");
        items.put("RING1", "Morph-Emerald");
        return Plans.save(new Plans.Build("", "Thunder", List.of("Covetous Thunder"), Tuning.Choice.PLAIN, items, 100, 44, 0, 0), 1000);
    }

    @Test
    void keepSwapRemoveAndRename() {
        Plans.Build b = saved();
        // keep: the helmet joins the kept items, a second click lets it go
        Plans.update(BuildEdits.toggleKeep(b, "HELMET"), 2000);
        assertTrue(Plans.byId(b.id()).locked("Yume"));
        Plans.update(BuildEdits.toggleKeep(Plans.byId(b.id()), "HELMET"), 2001);
        assertFalse(Plans.byId(b.id()).locked("Yume"));
        // swap a kept piece: the new one is kept in its place, the score worked out again
        double before = Plans.byId(b.id()).score();
        Plans.update(BuildEdits.swap(Plans.byId(b.id()), "WEAPON", "Lightshow"), 2002);
        Plans.Build s = Plans.byId(b.id());
        assertEquals("Lightshow", s.items().get("WEAPON"));
        assertEquals(List.of("Lightshow"), s.locks(), "the kept mark moves with the slot");
        assertNotEquals(before, s.score(), "scored again with the new piece");
        // remove: the slot is empty and the piece no longer kept
        Plans.update(BuildEdits.remove(s, "WEAPON"), 2003);
        Plans.Build r = Plans.byId(b.id());
        assertNull(r.items().get("WEAPON"));
        assertTrue(r.locks().isEmpty());
        assertEquals(2, r.items().size());
        // rename, kept with the creation time; an empty name keeps the old one
        Plans.update(BuildEdits.renamed(r, "  Light  "), 2004);
        assertEquals("Light", Plans.byId(b.id()).name());
        Plans.update(BuildEdits.renamed(Plans.byId(b.id()), "   "), 2005);
        assertEquals("Light", Plans.byId(b.id()).name());
        assertEquals(1000, Plans.byId(b.id()).created());
        // the strip
        Plans.Build t = Plans.byId(b.id());
        Plans.update(new Plans.Build(t.id(), t.name(), t.locks(), Tuning.Choice.PLAIN.withLean(Tuning.LEAN_COST, true), t.items(),
                t.score(), t.level(), t.created(), t.updated(), t.points()), 2006);
        assertTrue(Plans.byId(b.id()).tuning().leans(Tuning.LEAN_COST));
        // the current plan follows its saved build
        Plans.apply(Plans.byId(b.id()));
        Plans.update(BuildEdits.renamed(Plans.byId(b.id()), "Plan name"), 2007);
        assertEquals("Plan name", Plans.current().name());
    }

    @Test
    void recomputeOverwriteAndDeleteUndo() {
        Plans.Build b = saved();
        Map<String, String> items = new LinkedHashMap<>(b.items());
        items.put("HELMET", "Venison");
        Plans.Build fresh = new Plans.Build("p1", "", b.locks(), b.tuning(), items, 200, 44, 0, 0);
        Plans.overwrite(b.id(), fresh, 3000);
        assertEquals("Venison", Plans.byId(b.id()).items().get("HELMET"));
        assertEquals("Thunder", Plans.byId(b.id()).name(), "the name stays");
        assertEquals(b.items(), Plans.overwriteUndoable(3001).items(), "the build as it was, for Undo");
        assertTrue(Plans.undoOverwrite(3002));
        assertEquals("Yume", Plans.byId(b.id()).items().get("HELMET"), "back as it was");
        assertFalse(Plans.undoOverwrite(3003), "once");
        Plans.overwrite(b.id(), fresh, 4000);
        assertNull(Plans.overwriteUndoable(4000 + Plans.UNDO_MS + 1), "too late");
        // a delete and its Undo
        assertTrue(Plans.delete(b.id(), 5000));
        assertNull(Plans.byId(b.id()));
        assertTrue(Plans.undo(5001));
        assertEquals("Thunder", Plans.byId(b.id()).name());
    }

    @Test
    void aCopyAndTheFileReadBack() {
        Plans.Build b = saved();
        Plans.Build copy = Plans.save(BuildEdits.copyOf(b), 6000);
        assertNotEquals(b.id(), copy.id());
        assertEquals("Thunder (copy)", copy.name());
        assertEquals(b.items(), copy.items());
        Plans.update(BuildEdits.toggleKeep(Plans.byId(b.id()), "HELMET"), 6001);
        Plans.update(BuildEdits.remove(Plans.byId(b.id()), "RING1"), 6002);
        Plans.Build edited = Plans.byId(b.id());
        Saver.flush();
        Plans.forget();
        assertEquals(edited, Plans.byId(b.id()), "every change kept in the file");
        assertEquals(2, Plans.saved().size());
    }
}
