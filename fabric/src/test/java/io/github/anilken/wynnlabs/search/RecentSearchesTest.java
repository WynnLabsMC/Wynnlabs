package io.github.anilken.wynnlabs.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class RecentSearchesTest {

    @Test
    void newestFirstWithoutRepeatsAndAtMostTen() {
        RecentSearches r = new RecentSearches(null);
        r.add("Atoll");
        r.add("mob:Reggie");
        r.add("Atoll");
        assertEquals(List.of("Atoll", "mob:Reggie"), r.names(), "a repeat moves to the front, once");
        for (int i = 0; i < 12; i++) {
            r.add("Item " + i);
        }
        assertEquals(RecentSearches.MAX, r.names().size());
        assertEquals("Item 11", r.names().get(0));
        assertEquals(List.of("Item 11", "Item 10", "Item 9", "Item 8", "Item 7"), r.newest(5));
    }

    @Test
    void oneIsForgottenOrAllAre() {
        RecentSearches r = new RecentSearches(null);
        r.add("A");
        r.add("B");
        r.add("  ");
        r.remove("A");
        assertEquals(List.of("B"), r.names(), "blank names are not kept");
        r.clear();
        assertEquals(List.of(), r.names());
    }
}
