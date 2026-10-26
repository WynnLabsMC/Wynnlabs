package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Mini-quests from the wiki's "Mini-Quest" tables (a recorded excerpt): each post, and the one-stage guide built from it. */
class MiniQuestsTest {

    private static String page() throws Exception {
        try (var in = MiniQuestsTest.class.getResourceAsStream("/wiki/mini-quest.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterEach
    void forget() {
        MiniQuests.preview(null);
    }

    @Test
    void theSlayingAndGatheringTablesGiveEachPost() throws Exception {
        Map<String, MiniQuests.Mini> posts = MiniQuests.parse(page());
        assertEquals(6, posts.size());
        MiniQuests.Mini pernix = posts.get("Slay Pernix Monkeys");
        assertEquals("COMBAT", pernix.kind());
        assertEquals(90, pernix.level());
        assertArrayEquals(new int[] {-1721, 110, -2255}, pernix.at());
        assertEquals("Corkus", pernix.province());
        assertEquals("24 Perkish Potatoes", pernix.items());
        assertEquals(1_234_200, pernix.xp());
        MiniQuests.Mini copper = posts.get("Gather Copper");
        assertEquals("MINING", copper.kind());
        assertEquals(8, copper.level());
        assertEquals(120, copper.xp());
        assertEquals(100, copper.combatXp());
        assertEquals("Emerald Trail", copper.place());
    }

    @Test
    void aLockedMiniGetsItsPostFromTheWikiAndAnOpenOneKeepsTheBooksLine() throws Exception {
        MiniQuests.preview(page());
        QuestWiki.Info locked = MiniQuests.info("Slay Pernix Monkeys", null);
        assertNotNull(locked);
        assertEquals(1, locked.stages().size());
        assertArrayEquals(new int[] {-1721, 110, -2255}, locked.stages().get(0).at());
        assertEquals(1, locked.stages().get(0).points().size());
        assertEquals(1_234_200, locked.xp());
        QuestBook.Entry open = new QuestBook.Entry("Slay Spiders", "AVAILABLE", 4, "SHORT", 400, true,
                "Bring [5 Forest Webs] to the Slaying Post [Combat Lv. 4] at [-272, 73, -1588]", -272, 73, -1588, null, 0, "MINI_QUEST", "EASY");
        QuestWiki.Info book = MiniQuests.info("Slay Spiders", open);
        assertEquals(open.task(), book.stages().get(0).task());
        assertArrayEquals(new int[] {-272, 73, -1588}, book.stages().get(0).at());
        assertNull(MiniQuests.info("Collect White Lilies", null));       // not on the page and not open: nothing known
    }

    @Test
    void itemsComeFromTheBooksLineOrTheWikiWithAlternatives() throws Exception {
        Map<String, MiniQuests.Mini> posts = MiniQuests.parse(page());
        assertEquals(java.util.List.of(new MiniQuests.Need(12, "Copper Ingot"), new MiniQuests.Need(12, "Copper Gem")),
                MiniQuests.needs(null, posts.get("Gather Copper")));
        assertEquals(java.util.List.of(new MiniQuests.Need(5, "Forest Webs")),
                MiniQuests.needs("Bring [5 Forest Webs] to the Slaying Post [Combat Lv. 4] at [-272, 73, -1588]", posts.get("Slay Spiders")));
        assertEquals(java.util.List.of(new MiniQuests.Need(24, "Perkish Potatoes")), MiniQuests.needs(null, posts.get("Slay Pernix Monkeys")));
    }

    @Test
    void theRequirementSaysWhatIsMissing() throws Exception {
        Map<String, MiniQuests.Mini> posts = MiniQuests.parse(page());
        MiniQuests.Req locked = MiniQuests.requirement(posts.get("Slay Pernix Monkeys"), 44, p -> -1, null);
        assertEquals(new MiniQuests.Req(false, "COMBAT", 90, 44), locked);
        MiniQuests.Req mining = MiniQuests.requirement(posts.get("Gather Copper"), 44, p -> p.equals("MINING") ? 5 : -1, null);
        assertEquals(new MiniQuests.Req(false, "MINING", 8, 5), mining);
        org.junit.jupiter.api.Assertions.assertTrue(MiniQuests.requirement(posts.get("Slay Spiders"), 44, p -> -1, null).met());
    }

    @Test
    void aSlayingMiniNamesItsMobs() {
        assertEquals(java.util.List.of("Pernix Monkey"), MiniQuests.mobNames("Slay Pernix Monkeys"));
        assertEquals(java.util.List.of("Frosted Guard", "Cryostone Golem"), MiniQuests.mobNames("Slay Frosted Guards & Cryostone Golems"));
        assertEquals(java.util.List.of(), MiniQuests.mobNames("Slay Creatures of Nesaak Forest"));
        var mobs = java.util.List.of(new io.github.anilken.wynnlabs.search.MobWiki.Mob("Pernix Monkey", 88, "Corkus", "Corkus Forest", java.util.List.of()),
                new io.github.anilken.wynnlabs.search.MobWiki.Mob("Pernix Monkey", 91, "Corkus", "", java.util.List.of()));
        assertEquals(new MiniQuests.MobInfo("Pernix Monkey", 88, 91, "Corkus Forest"), MiniQuests.mob("Slay Pernix Monkeys", 90, mobs));
    }
}
