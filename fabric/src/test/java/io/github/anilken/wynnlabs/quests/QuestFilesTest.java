package io.github.anilken.wynnlabs.quests;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.core.TempConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * quests.json, discoveries-book.json and discoveries.json: a file written the old way
 * (Files.writeString of the same text) is read in and saved again byte for byte.
 */
class QuestFilesTest {

    private static final String ID = "test-json-quests";

    @TempDir
    Path dir;

    /** The old writer: the folder, then the text as UTF-8. */
    private static byte[] oldWrite(Path f, String text) throws Exception {
        Files.createDirectories(f.getParent());
        Files.writeString(f, text, StandardCharsets.UTF_8);
        return Files.readAllBytes(f);
    }

    private Path character(String file) {
        return dir.resolve("wynnlabs").resolve("characters").resolve(ID).resolve(file);
    }

    @Test
    void questsAreSavedWithTheSameBytes() throws Exception {
        try (var c = new TempConfig(dir, ID)) {
            JsonObject o = new JsonObject();
            o.addProperty("name", "King's Recruit · ü");
            o.addProperty("status", "STARTED");
            o.addProperty("level", 1);
            o.addProperty("length", "SHORT");
            o.addProperty("xp", 120L);
            o.addProperty("met", true);
            o.addProperty("task", "Talk to Tasim");
            o.addProperty("x", -620);
            o.addProperty("y", 45);
            o.addProperty("z", -1550);
            o.addProperty("bookDistance", 30);
            o.addProperty("seen", 1_700_000_000_000L);
            o.addProperty("type", "QUEST");
            o.addProperty("difficulty", "EASY");
            JsonArray arr = new JsonArray();
            arr.add(o);
            Path f = character("quests.json");
            byte[] old = oldWrite(f, arr.toString());
            assertEquals(1, QuestBook.all().size());
            Files.delete(f);
            TempConfig.call(QuestBook.class, "save");
            assertArrayEquals(old, Files.readAllBytes(f), new String(old, StandardCharsets.UTF_8));
        }
    }

    @Test
    void bookDiscoveriesAreSavedWithTheSameBytes() throws Exception {
        try (var c = new TempConfig(dir, ID)) {
            Path f = character("discoveries-book.json");
            byte[] old = oldWrite(f, "[\"Bob's Tomb\",\"Ägir's Rest\"]");
            assertEquals(Set.of("Ägir's Rest", "Bob's Tomb"), DiscoveryBook.found());
            assertTrue(DiscoveryBook.seen());
            Files.delete(f);
            TempConfig.call(DiscoveryBook.class, "save");
            assertArrayEquals(old, Files.readAllBytes(f), new String(old, StandardCharsets.UTF_8));
        }
    }

    @Test
    void tickedDiscoveriesAreSavedWithTheSameBytes() throws Exception {
        try (var c = new TempConfig(dir, ID)) {
            JsonObject o = new JsonObject();
            JsonArray a = new JsonArray();
            a.add("Bob's Tomb");             // sorted as saved: "Ä" after "B"
            a.add("Ägir's Rest");
            o.add("done", a);
            Path f = character("discoveries.json");
            byte[] old = oldWrite(f, new GsonBuilder().setPrettyPrinting().create().toJson(o));
            assertTrue(Discoveries.ticked(ID, "Bob's Tomb"));
            Discoveries.tick(ID, "Bob's Tomb", false);
            Discoveries.tick(ID, "Bob's Tomb", true);
            assertArrayEquals(old, Files.readAllBytes(f), new String(old, StandardCharsets.UTF_8));
        }
    }
}
