package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuResumeTest {

    @Test
    void theStoredTabOpensWhileItsPageExists() {
        assertEquals(7, MenuResume.tab(7, t -> true));
    }

    @Test
    void aTabThatIsGoneFallsBackToHome() {
        assertEquals(0, MenuResume.tab(8, t -> t != 8), "8, the old discoveries page, has no page any more");
        assertEquals(0, MenuResume.tab(-1, t -> true), "nothing stored");
    }

    @Test
    void aQuestTrackedSinceTheMenuClosedWinsOverTheStoredPlace() {
        MenuResume.State s = new MenuResume.State(5, null, null, null, 0, 3, null, "Underice");
        assertTrue(MenuResume.resumeOverTracked(s, null));
        assertTrue(MenuResume.resumeOverTracked(s, "Underice"), "the same quest as before: the stored place");
        assertFalse(MenuResume.resumeOverTracked(s, "Jungle Fever"), "a newly tracked quest: the quests tab");
    }

    @Test
    void theStateReadsBackAsItWasWritten() {
        MenuResume.State s = new MenuResume.State(2, "SECRET_DISCOVERY", "Nilrem's Sealed Letter", "SECRET_DISCOVERY", 3, 6, "relik", null);
        assertEquals(s, MenuResume.parse(MenuResume.json(s)));
    }

    @Test
    void aBrokenOrPartialFileKeepsTheDefaults() {
        MenuResume.State s = MenuResume.parse("{\"tab\":9}");
        assertEquals(9, s.tab());
        assertNull(s.questSelected());
        assertEquals(0, s.settingsCat());
    }

    @Test
    void aStoredDamageOrDeathsTabOpensTheTreePage() {
        assertEquals(9, MenuResume.page(7));
        assertEquals(9, MenuResume.page(3));
        assertEquals(2, MenuResume.page(2));
    }
}
