package io.github.anilken.wynnlabs.coach;

import io.github.anilken.wynnlabs.core.TempConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * deaths.json and xprates.json: a file written the old way (Files.writeString of the same text) is
 * read in and saved again byte for byte.
 */
class CoachFilesTest {

    private static final String ID = "test-json-coach";

    @TempDir
    Path dir;

    /** The old writer: the folder, then the text as UTF-8. */
    private static byte[] oldWrite(Path f, String text) throws Exception {
        Files.createDirectories(f.getParent());
        Files.writeString(f, text);
        return Files.readAllBytes(f);
    }

    @Test
    void deathsAreSavedWithTheSameBytes() throws Exception {
        try (var c = new TempConfig(dir, ID)) {
            Path f = dir.resolve("wynnlabs").resolve("characters").resolve(ID).resolve("deaths.json");
            var d = new DeathCoach.Death(1_700_000_000_000L, -812, 64, -1590, 23, "Nivla Woods · ü", false, 20,
                    3456, 1200, 4.5, 2, List.of("Corrupted Spider", "Ägir"), List.of());
            byte[] old = oldWrite(f, DeathCoach.write(List.of(d)));
            assertEquals(1, DeathCoach.all().size());
            Files.delete(f);
            TempConfig.call(DeathCoach.class, "save");
            assertArrayEquals(old, Files.readAllBytes(f), new String(old, StandardCharsets.UTF_8));
        }
    }

    @Test
    void xpRatesAreSavedWithTheSameBytes() throws Exception {
        try (var c = new TempConfig(dir, ID)) {
            Path f = dir.resolve("wynnlabs").resolve("characters").resolve(ID).resolve("xprates.json");
            byte[] old = oldWrite(f, "{\"Nivla Woods\":[1200,600]}");
            assertEquals(7200, XpMeter.rate("Nivla Woods"), 1e-9);
            Files.delete(f);
            TempConfig.call(XpMeter.class, "save");
            assertArrayEquals(old, Files.readAllBytes(f), new String(old, StandardCharsets.UTF_8));
        }
    }
}
