package io.github.anilken.wynnlabs.api;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.build.CommunityBuilds;
import io.github.anilken.wynnlabs.build.CommunityList;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** GET /community-builds: passive (state "missing", no entries, no fetch) until the list is on hand; then filtered. */
class CommunityBuildsRouteTest {

    @AfterEach
    void reset() {
        CommunityList.use(null, null);
    }

    private static CommunityBuilds.Entry entry(String id, String klass, int min, int max, List<String> tags) {
        return new CommunityBuilds.Entry(id, id, "https://wynnbuilder.github.io/builder/#CZ0", klass, min, max, tags, "d", "forum",
                "2026-11-04", false, null);
    }

    @Test
    void neverFetched() {
        CommunityList.use(null, null);
        JsonObject o = WynnLabs.communityJson(Map.of(), Klass.MAGE, 85);
        assertEquals("missing", o.get("state").getAsString());
        assertEquals(0, o.getAsJsonArray("builds").size());
        assertEquals(CommunityList.State.MISSING, CommunityList.state(), "the route started no fetch");
        assertNull(CommunityList.doc());
    }

    @Test
    void filtersTheListOnHand() {
        CommunityList.use(new CommunityBuilds.Doc(1, "2026-11-04", CommunityBuilds.TAGS, List.of(
                entry("mage-a", "mage", 80, 106, List.of("raid")),
                entry("mage-b", "mage", 1, 40, List.of("leveling")),
                entry("archer-a", "archer", 80, 106, List.of("raid")),
                entry("set", "any", 1, 106, List.of("xp-bonus"))), 0), Map.of());
        JsonObject o = WynnLabs.communityJson(Map.of("class", "mage", "level", "85"), Klass.SHAMAN, 10);
        assertEquals("ready", o.get("state").getAsString());
        assertEquals(2, o.get("count").getAsInt(), "the mage build for 85 and the set");
        assertEquals(1, WynnLabs.communityJson(Map.of("tag", "raid"), Klass.MAGE, 85).get("count").getAsInt());
        assertEquals(4, WynnLabs.communityJson(Map.of("class", "all", "level", "0"), Klass.MAGE, 85).get("count").getAsInt());
    }
}
