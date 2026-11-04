package io.github.anilken.wynnlabs.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A saved JSON file: written as UTF-8 bytes exactly as given, read back the same, failures skipped. */
class JsonFileTest {

    @TempDir
    Path dir;

    @Test
    void roundTripsTheExactBytesInUtf8() throws Exception {
        Path f = dir.resolve("characters").resolve("abc").resolve("file.json");
        JsonFile file = new JsonFile(() -> f);
        String text = "{\n  \"name\": \"Günlük ödül · Ragni\"\n}";
        file.writeNow(() -> text);
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(f), "UTF-8, no line ending added");
        List<String> read = new ArrayList<>();
        assertTrue(file.read(read::add));
        assertEquals(List.of(text), read);
    }

    @Test
    void aMissingOrBrokenFileReadsAsFalse() throws Exception {
        Path f = dir.resolve("broken.json");
        JsonFile file = new JsonFile(() -> f);
        assertFalse(file.read(t -> {
            throw new AssertionError("a missing file is not parsed");
        }));
        Files.writeString(f, "[1,", StandardCharsets.UTF_8);
        List<String> taken = new ArrayList<>();
        assertFalse(file.read(t -> {
            taken.add("before");
            throw new IllegalStateException("broken");
        }));
        assertEquals(List.of("before"), taken, "what was taken in before the failure stays");
    }

    @Test
    void aFailedTextBuildWritesNothing() {
        Path f = dir.resolve("none.json");
        new JsonFile(() -> f).writeNow(() -> {
            throw new IllegalStateException("no text");
        });
        assertFalse(Files.exists(f));
    }
}
