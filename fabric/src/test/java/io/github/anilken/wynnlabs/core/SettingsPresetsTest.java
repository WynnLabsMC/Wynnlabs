package io.github.anilken.wynnlabs.core;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Settings presets: saved by name, one in use per character, the language and menu fill shared. */
class SettingsPresetsTest {

    private Path dir;

    @BeforeEach
    void temp() throws Exception {
        dir = Files.createTempDirectory("wynnlabs-presets");
        Settings.dirOverride = dir;
        JsonObject o = new JsonObject();
        o.addProperty("mobTrack", true);
        o.addProperty("langOverride", 1);
        Settings.use(o);
    }

    @AfterEach
    void reset() {
        Settings.dirOverride = null;
        Settings.use(new JsonObject());
    }

    @Test
    void aPresetKeepsItsOwnSwitchesAndTheSharedOnesComeBack() {
        assertTrue(Settings.savePreset("Tank"));
        Settings.set("mobTrack", false);
        assertEquals("Tank", Settings.activePreset());
        assertFalse(Settings.on("mobTrack"));

        Settings.usePreset(null);
        assertNull(Settings.activePreset());
        assertTrue(Settings.on("mobTrack"), "the shared settings were not touched by the preset's change");

        Settings.usePreset("Tank");
        assertFalse(Settings.on("mobTrack"));
    }

    @Test
    void languageStaysShared() {
        Settings.savePreset("Tank");
        Settings.setNumber("langOverride", 2);
        Settings.usePreset(null);
        assertEquals(2, Settings.number("langOverride", 0), "the language follows the player, not the preset");
    }

    @Test
    void namesAreCleanedAndListed() {
        assertEquals("Myset 1", Settings.presetName("  My/set 1?  "));
        assertEquals("", Settings.presetName("///"));
        assertFalse(Settings.savePreset("///"));
        Settings.savePreset("b");
        Settings.savePreset("A");
        assertEquals(List.of("A", "b"), Settings.presetNames());
    }

    @Test
    void deletingTheOneInUseBringsTheSharedBack() {
        Settings.savePreset("Tank");
        Settings.set("mobTrack", false);
        Settings.deletePreset("Tank");
        assertNull(Settings.activePreset());
        assertTrue(Settings.on("mobTrack"));
        assertTrue(Settings.presetNames().isEmpty());
    }

    @Test
    void aMissingPresetFallsBackToTheShared() {
        Settings.usePreset("Gone");
        assertNull(Settings.activePreset());
        assertTrue(Settings.on("mobTrack"));
    }

    @Test
    void renamingKeepsTheSettingsAndRefusesATakenName() {
        Settings.savePreset("Tank");
        Settings.set("mobTrack", false);
        Settings.savePreset("Healer");
        assertNull(Settings.renamePreset("Tank", "healer"), "another preset has that name");
        assertEquals("Mage", Settings.renamePreset("Healer", "Mage"));
        assertEquals("Mage", Settings.activePreset(), "the one in use follows the rename");
        assertEquals(List.of("Mage", "Tank"), Settings.presetNames());
        Settings.usePreset("Tank");
        assertFalse(Settings.on("mobTrack"));
    }
}
