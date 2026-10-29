package io.github.anilken.wynnlabs.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tracked items: keyed by the cleaned name a drop on the ground shows, saved like tracked mobs. */
class TrackedItemsTest {

    @Test
    void dropNamesMatchTheTrackedItem(@TempDir Path dir) {
        TrackedMobs t = new TrackedMobs(dir.resolve("tracked-items.json"), TrackedMobs::itemKey);
        assertTrue(t.toggle("Bob's Tear"));
        assertEquals(Set.of("bob's tear"), t.keys());
        assertTrue(t.keys().contains(TrackedMobs.itemKey("§5Bob’s Tear")), "colour codes and the curly apostrophe");
        assertTrue(t.keys().contains(TrackedMobs.itemKey("Unidentified Bob's Tear")));
        assertTrue(t.toggle("Lucky Rabbit's Foot"));
        assertTrue(t.keys().contains(TrackedMobs.itemKey("Lucky Rabbit's Foot [✫✫✫]")), "an ingredient's stars");
        assertFalse(t.keys().contains(TrackedMobs.itemKey("Bob's Tears")));
        assertEquals("Bob's Tear", t.name("bob's tear"), "the name as the player picked it");
    }

    @Test
    void theListIsSavedAndReadBack(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("tracked-items.json");
        TrackedMobs t = new TrackedMobs(f, TrackedMobs::itemKey);
        t.toggle("Bob's Tear");
        t.toggle("bob's tear");
        t.toggle("Gale's Force");
        io.github.anilken.wynnlabs.core.Saver.flush();
        TrackedMobs again = new TrackedMobs(f, TrackedMobs::itemKey);
        assertEquals(List.of("Gale's Force"), again.names());
    }
}
