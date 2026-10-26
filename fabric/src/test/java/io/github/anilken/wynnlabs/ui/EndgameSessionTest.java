package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.Settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Endgame title's one session chip, said in words: failures and the streak only when there are any. */
class EndgameSessionTest {

    private static void turkish() {
        JsonObject s = new JsonObject();
        s.addProperty("langOverride", 2);
        Settings.use(s);
    }

    @AfterEach
    void reset() {
        Settings.use(new JsonObject());
    }

    @Test
    void theChipSaysTheRaidsAndOnlyWhatThereIs() {
        turkish();
        assertEquals("Bu oturum: 3 raid · seri 5", EndgameTitleBar.sessionText(3, 0, 5, false));
        assertEquals("Bu oturum: 3 raid · 1 başarısız", EndgameTitleBar.sessionText(3, 1, 0, false));
        assertEquals("3 raid · 1 başarısız · seri 2", EndgameTitleBar.sessionText(3, 1, 2, true));
    }
}
