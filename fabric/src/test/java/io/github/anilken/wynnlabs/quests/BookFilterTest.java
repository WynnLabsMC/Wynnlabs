package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BookFilterTest {

    @Test
    void thePlayersOwnFilterIsKept() {
        assertEquals("Quest", BookFilter.keeps(null, false, "Quest"));
        assertEquals("Cave", BookFilter.keeps("Quest", false, "Cave"));
        assertEquals("World Event", BookFilter.keeps("Quest", false, "World Event"), "chosen by the player");
    }

    @Test
    void aReadsLeftoverIsNotThePlayersFilter() {
        assertEquals("Quest", BookFilter.keeps("Quest", true, "World Event"));
        assertEquals("Cave", BookFilter.keeps("Quest", true, "Cave"), "changed by the player after the read");
        assertEquals("Quest", BookFilter.keeps("Quest", true, null), "an unreadable button changes nothing");
    }
}
