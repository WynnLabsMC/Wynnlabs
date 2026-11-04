package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.RepoData;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published community build list ({@code data/community-builds.json}) is clean: it parses,
 * ids are unique kebab-case, every link decodes in the current form, its weapon is the entry's
 * class (none on an "any" set), no piece is missing from the item data, the level range, tags,
 * description, source, checked date and outdated flag are valid, a chat or list entry has no
 * credit, and the hash file matches.
 */
class CommunityBuildsDataTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    static Path data(String name) {
        return Path.of(System.getProperty("wynnlabs.projectDir", ".")).resolve("data").resolve(name);
    }

    @Test
    void publishedListIsValid() throws Exception {
        byte[] bytes = Files.readAllBytes(data("community-builds.json"));
        String sha = Files.readString(data("community-builds.sha256"), StandardCharsets.UTF_8).trim();
        assertEquals(RepoData.sha256(bytes), sha, "community-builds.sha256 does not match the file");
        JsonObject doc = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(CommunityBuilds.TAGS, doc.getAsJsonArray("tags").asList().stream().map(JsonElement::getAsString).toList(),
                "the file's tag list");
        CommunityBuilds.Doc parsed = CommunityBuilds.parse(new String(bytes, StandardCharsets.UTF_8));
        assertEquals(0, parsed.skipped(), "entries the mod would skip");
        ReferenceBuilds.installPlain();
        List<String> bad = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonElement el : doc.getAsJsonArray("builds")) {
            JsonObject o = el.getAsJsonObject();
            String id = o.has("id") ? o.get("id").getAsString() : "?";
            for (String f : List.of("id", "name", "link", "class", "levelMin", "levelMax", "tags", "description", "source", "checked", "outdated")) {
                if (!o.has(f) || o.get(f).isJsonNull()) {
                    bad.add(id + ": no " + f);
                }
            }
            if (!bad.isEmpty() && bad.get(bad.size() - 1).startsWith(id + ": no")) {
                continue;
            }
            if (!id.matches("[a-z0-9]+(-[a-z0-9]+)*") || !ids.add(id)) {
                bad.add(id + ": id not unique kebab-case");
            }
            String klass = o.get("class").getAsString();
            int min = o.get("levelMin").getAsInt();
            int max = o.get("levelMax").getAsInt();
            if (!CommunityBuilds.CLASSES.contains(klass)) {
                bad.add(id + ": class " + klass);
            }
            if (min < 1 || max > 106 || min > max) {
                bad.add(id + ": level range " + min + "-" + max);
            }
            List<String> tags = o.getAsJsonArray("tags").asList().stream().map(JsonElement::getAsString).toList();
            if (tags.isEmpty() || !CommunityBuilds.TAGS.containsAll(tags)) {
                bad.add(id + ": tags " + tags);
            }
            if (o.get("description").getAsString().length() > CommunityBuilds.MAX_DESCRIPTION) {
                bad.add(id + ": description over " + CommunityBuilds.MAX_DESCRIPTION);
            }
            String source = o.get("source").getAsString();
            if (!CommunityBuilds.SOURCES.contains(source)) {
                bad.add(id + ": source " + source);
            }
            if (o.has("credit") && (source.equals("discord") || source.equals("community"))) {
                bad.add(id + ": credit on a " + source + " entry");
            }
            if (!o.get("checked").getAsString().matches("\\d{4}-\\d{2}-\\d{2}")) {
                bad.add(id + ": checked date");
            }
            String link = o.get("link").getAsString();
            WynnBuilderLink.Build b = WynnBuilderLink.decode(link);
            if (b == null || !link.startsWith(WynnBuilderExport.SITE)) {
                bad.add(id + ": link does not decode in the current form");
                continue;
            }
            LinkImport.Result r = LinkImport.of(GameState.current(), b, CommunityBuildsImport.INGREDIENTS::get);
            WynnBuilderLink.Piece weapon = null;
            for (LinkImport.Row row : r.rows()) {
                WynnBuilderLink.Piece p = row.piece();
                boolean empty = p.kind() == WynnBuilderLink.Kind.ITEM && (p.name() == null && p.id() < 0 || p.name() != null && p.name().startsWith("No "));
                if (empty) {
                    continue;
                }
                if (p.slot() == WynnBuilderLink.WEAPON) {
                    weapon = p;
                }
                if (p.kind() == WynnBuilderLink.Kind.ITEM && row.info() == null) {
                    bad.add(id + ": " + p.name() + " is not in the item data");
                }
            }
            Klass k = weapon == null ? Klass.NONE : weapon.kind() == WynnBuilderLink.Kind.CRAFTED
                    ? io.github.anilken.wynnlabs.model.Slot.of(weapon.recipe().type().toUpperCase(Locale.ROOT)).klass()
                    : ReferenceBuilds.classOf(b);
            String want = k == null ? "?" : k == Klass.NONE ? "any" : k.name().toLowerCase(Locale.ROOT);
            if (!want.equals(klass)) {
                bad.add(id + ": marked " + klass + " but the weapon is " + (want.equals("any") ? "missing" : "an " + want + "'s"));
            }
        }
        assertTrue(bad.isEmpty(), bad.size() + " problems:\n" + String.join("\n", bad.subList(0, Math.min(40, bad.size()))));
        System.out.println("[WynnLabs] community builds: " + ids.size() + " entries checked");
        assertTrue(ids.size() > 0, "an empty list");
    }
}
