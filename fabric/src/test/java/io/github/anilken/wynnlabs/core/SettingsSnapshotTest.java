package io.github.anilken.wynnlabs.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settings are read from a lock-free copy that follows every change; an old file is migrated once. */
class SettingsSnapshotTest {

    @AfterEach
    void reset() {
        Settings.use(new JsonObject());
    }

    /** A settings file in the form from before the regrouping (no version), with these switches. */
    private static JsonObject old(Object... pairs) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            o.addProperty((String) pairs[i], (Boolean) pairs[i + 1]);
        }
        return o;
    }

    @Test
    void readsFollowChanges() {
        JsonObject a = new JsonObject();
        a.addProperty("mobTrack", false);
        a.addProperty("langOverride", 2);
        Settings.use(a);
        assertFalse(Settings.on("mobTrack"));
        assertEquals(2, Settings.number("langOverride", 0));

        JsonObject b = new JsonObject();
        b.addProperty("mobTrack", true);
        Settings.use(b);
        assertTrue(Settings.on("mobTrack"));
        assertEquals(0, Settings.number("langOverride", 0), "an unset number falls back");
    }

    @Test
    void defaultsApplyToUnsetSwitches() {
        Settings.use(new JsonObject());
        assertFalse(Settings.on("debug"), "debug starts off");
        assertFalse(Settings.on("rideRefresh"), "asking the server for the mount starts off");
        assertTrue(Settings.on("mobTrack"), "other switches start on");
        assertEquals(1, Settings.choice("dropGlow"), "drops glow from Unique up");
        assertEquals(1, Settings.choice("slotBadges"));
        assertEquals(2, Settings.choice("treeHelp"));
        assertEquals(1, Settings.choice("updates"));
    }

    @Test
    void steppedNumbersStartAtTheirDefault() {
        Settings.use(new JsonObject());
        assertEquals(85, Settings.stepValue("menuTooltipScale"), "menu tooltips start at 85%");
        int[] options = Settings.STEPS.get("menuTooltipScale");
        assertEquals(100, options[options.length - 1], "100% (as the game draws them) is a choice");
        assertTrue(java.util.Arrays.stream(options).anyMatch(o -> o == 85), "the default is a choice");

        JsonObject o = new JsonObject();
        o.addProperty("menuTooltipScale", 70);
        Settings.use(o);
        assertEquals(70, Settings.stepValue("menuTooltipScale"));
    }

    @Test
    void oldFilesWithRemovedFeaturesStillLoad() {
        JsonObject old = new JsonObject();
        old.addProperty("chestGlow", true);
        old.addProperty("partyGlow", false);
        old.addProperty("namelessGlow", true);
        old.addProperty("chestMinStars", 2);
        old.addProperty("chestFarDistance", 400);
        old.addProperty("menuScale", 3);
        old.addProperty("treeBonuses", false);
        old.addProperty("mobTrack", false);
        old.addProperty("langOverride", 2);
        Settings.use(old);
        assertFalse(Settings.on("mobTrack"), "kept switches still load");
        assertEquals(2, Settings.number("langOverride", 0), "kept numbers still load");
        assertEquals(-1, Settings.number("chestMinStars", -1), "removed numbers are dropped");
        assertEquals(-1, Settings.number("menuScale", -1), "the manual menu scale is gone: Auto");
        for (String k : Settings.REMOVED) {
            assertFalse(Settings.LABELS.containsKey(k), k + " is no longer a setting");
            assertFalse(Settings.json().has(k), k + " is no longer reported");
        }
    }

    @Test
    void anUntouchedOldFileKeepsEveryChoiceAtItsDefault() {
        Settings.use(old("itemAdvice", true));
        for (String k : Settings.CHOICES.keySet()) {
            assertEquals(Settings.CHOICES.get(k), Settings.choice(k), k);
            assertEquals(-1, Settings.number(k, -1), k + " is not written: it did not change");
        }
    }

    @Test
    void dropGlowTakesTheWidestSwitchThatWasOn() {
        Settings.use(old("groundGlow", false, "groundGlowNormal", true));
        assertEquals(0, Settings.choice("dropGlow"), "drops did not glow at all: Off");
        Settings.use(old("groundGlowOther", true));
        assertEquals(2, Settings.choice("dropGlow"), "ingredients glowed too: All drops");
        Settings.use(old("groundGlowNormal", true));
        assertEquals(2, Settings.choice("dropGlow"), "Normal drops glowed too: All drops");
        Settings.use(old("groundGlow", true));
        assertEquals(1, Settings.choice("dropGlow"));
    }

    @Test
    void badgesTreeHelpAndUpdatesFollowTheOldSwitches() {
        Settings.use(old("verdictBadges", false));
        assertEquals(0, Settings.choice("slotBadges"));
        Settings.use(old("verdictBadges", false, "hotbarBadges", true));
        assertEquals(2, Settings.choice("slotBadges"), "on if any was on: the hotbar's badges stay");
        Settings.use(old("glowTree", false));
        assertEquals(1, Settings.choice("treeHelp"), "tips without the glow");
        Settings.use(old("glowTree", false, "menuAdvice", false));
        assertEquals(0, Settings.choice("treeHelp"));
        Settings.use(old("menuAdvice", false));
        assertEquals(2, Settings.choice("treeHelp"), "the glow came with tips now");
        Settings.use(old("autoUpdate", true));
        assertEquals(2, Settings.choice("updates"));
        Settings.use(old("updateCheck", false));
        assertEquals(0, Settings.choice("updates"));
    }

    @Test
    void aMigratedFileIsNotMigratedAgain() {
        JsonObject now = old("hotbarBadges", true);
        now.addProperty(SettingsMigration.VERSION_KEY, SettingsMigration.VERSION);
        Settings.use(now);
        assertEquals(1, Settings.choice("slotBadges"), "a key from before in a new file means nothing");
        JsonObject saved = new JsonObject();
        saved.addProperty("dropGlow", 0);
        saved.addProperty("groundGlow", true);
        Settings.use(saved);
        assertEquals(0, Settings.choice("dropGlow"), "a choice already saved wins over the old switches");
    }

    @Test
    void savedRideRefreshStaysOn() {
        Settings.use(old("rideRefresh", true));
        assertTrue(Settings.on("rideRefresh"), "only the default changed");
    }

    @Test
    void theApiReportsChoicesAsNumbers() {
        Settings.use(new JsonObject());
        JsonObject j = Settings.json();
        assertEquals(1, j.get("dropGlow").getAsInt());
        assertTrue(j.get("itemAdvice").getAsBoolean());
        assertFalse(j.has("settingsVersion"));
    }

    @Test
    void readersSeeAConsistentCopyWhileWritersChangeIt() throws InterruptedException {
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 2000; i++) {
                JsonObject o = new JsonObject();
                o.addProperty("mobTrack", i % 2 == 0);
                Settings.use(o);
            }
        });
        writer.start();
        for (int i = 0; i < 20000; i++) {
            Settings.on("mobTrack");                // must never throw while the maps change
            Settings.choice("dropGlow");
        }
        writer.join();
    }
}
