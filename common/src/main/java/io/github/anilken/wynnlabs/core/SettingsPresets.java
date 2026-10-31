package io.github.anilken.wynnlabs.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which settings preset each character plays with: its name in the character's folder
 * ({@code preset.txt}), read when the character is picked. A character without one uses the
 * shared settings. The presets themselves are {@link Settings#savePreset}.
 */
public final class SettingsPresets {

    private SettingsPresets() {}

    private static Path file() {
        return Profile.dir().resolve("preset.txt");
    }

    /** The character was picked: its preset (or the shared settings) is in use from here on. */
    public static void onCharacter() {
        String name = null;
        try {
            if (Files.exists(file())) {
                name = Files.readString(file(), StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read preset.txt: " + e);
        }
        Settings.usePreset(name);
    }

    /** This character plays with {@code name} from now on; null for the shared settings. */
    public static void choose(String name) {
        Settings.usePreset(name);
        try {
            if (Settings.activePreset() == null) {
                Files.deleteIfExists(file());
            } else {
                Files.createDirectories(file().getParent());
                Files.writeString(file(), Settings.activePreset(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not save preset.txt: " + e);
        }
    }

    /** The settings as they are now, kept as a new preset this character plays with; the name it got, null if none. */
    public static String saveNew(String wanted) {
        String base = Settings.presetName(wanted);
        if (base.isEmpty()) {
            return null;
        }
        String name = base;
        java.util.List<String> have = Settings.presetNames();
        for (int i = 2; have.stream().anyMatch(name::equalsIgnoreCase); i++) {
            name = base + " " + i;
        }
        if (!Settings.savePreset(name)) {
            return null;
        }
        choose(name);
        return name;
    }

    /** A preset renamed, for every character that plays with it; the name it got, null if it could not. */
    public static String rename(String old, String wanted) {
        String name = Settings.renamePreset(old, wanted);
        if (name == null) {
            return null;
        }
        try (var all = Files.list(Profile.dir().getParent())) {
            for (Path d : (Iterable<Path>) all::iterator) {
                Path f = d.resolve("preset.txt");
                if (Files.exists(f) && Files.readString(f, StandardCharsets.UTF_8).strip().equals(old)) {
                    Files.writeString(f, name, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not follow the rename: " + e);
        }
        return name;
    }

    /** A preset deleted; characters that used it fall back to the shared settings when they are picked. */
    public static void delete(String name) {
        boolean mine = name.equals(Settings.activePreset());
        Settings.deletePreset(name);
        if (mine) {
            choose(null);
        }
    }
}
