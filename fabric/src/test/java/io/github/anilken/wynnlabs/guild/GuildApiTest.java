package io.github.anilken.wynnlabs.guild;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuildApiTest {

    @Test
    void readsLevelMembersAndWhoIsOnline() {
        String json = """
                {"name":"Test Guild","prefix":"TST","level":97,"xpPercent":68,"territories":32,"wars":5855,
                 "created":"2025-05-10T16:50:45.860000Z","online":2,
                 "members":{"total":4,
                   "owner":{"Alpha":{"online":false,"server":null}},
                   "chief":{"Bravo":{"online":true,"server":"NA2"}},
                   "captain":{"Charlie":{"online":true,"server":null}},
                   "recruit":{"Delta":{"online":false,"server":null}}}}
                """;
        GuildApi.Info i = GuildApi.parse(JsonParser.parseString(json).getAsJsonObject(), 1L);
        assertEquals("TST", i.prefix());
        assertEquals(97, i.level());
        assertEquals(4, i.members());
        assertEquals(2, i.online());
        assertEquals(2, i.onlineMembers().size());
        assertEquals(new GuildApi.Member("Bravo", "Chief", "NA2"), i.onlineMembers().get(0));
        assertEquals(new GuildApi.Member("Charlie", "Captain", null), i.onlineMembers().get(1));
    }
}
