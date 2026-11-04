package io.github.anilken.wynnlabs.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The holder keeps its value while the character stays and loads once more when it changes. */
class PerCharacterTest {

    /** Plays as {@code id}: the field the character switch sets in game. */
    private static void playAs(String id) throws Exception {
        Field f = Profile.class.getDeclaredField("current");
        f.setAccessible(true);
        f.set(null, id);
    }

    @AfterEach
    void noCharacter() throws Exception {
        playAs("");
    }

    @Test
    void sameIdKeepsTheValueAndANewIdLoadsOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        PerCharacter<String> h = new PerCharacter<>(() -> Profile.id() + "#" + loads.incrementAndGet());
        assertNull(h.loaded());
        playAs("a");
        assertEquals("a#1", h.get());
        assertEquals("a#1", h.get());
        playAs("b");
        assertEquals("b#2", h.get());
        assertEquals("b#2", h.get());
        assertEquals(2, loads.get());
    }

    @Test
    void clearLoadsAgainForTheSameId() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        PerCharacter<Integer> h = new PerCharacter<>(loads::incrementAndGet);
        assertEquals(1, h.get());
        h.clear();
        assertNull(h.loaded());
        assertEquals(2, h.get());
        assertEquals(2, h.get());
    }
}
