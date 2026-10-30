package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The quest completion banner in chat marks the quest completed at once, however the game splits the message. */
class QuestCompleteChatTest {

    private static final long T = 1_000_000;

    @BeforeEach
    void book() {
        QuestBook.reset();
        open("The Olmic Rune", "STARTED", 87);
        open("Recipe For Disaster", "AVAILABLE", 60);
        open("Murder Mystery", "AVAILABLE", 70);
        open("Lost Royalty", "AVAILABLE", 70);
        open("Lazarus Pit", "AVAILABLE", 70);
    }

    @AfterEach
    void forget() {
        QuestBook.reset();
    }

    private static void open(String name, String status, int level) {
        QuestBook.put(new QuestBook.Entry(name, status, level, "MEDIUM", 1000, true, null, null, null, null, null, 0, "QUEST", "EASY"));
    }

    private static String status(String name) {
        return QuestBook.get(name).status();
    }

    @Test
    void oneMessageWithTheBannerTheNameAndTheRewards() {
        QuestBook.onText("""
                          [Quest Complete]
                           The Olmic Rune
                Rewards:
                +5800000 Experience Points
                +Access to the Tol rune exchange""", T);
        assertEquals("COMPLETED", status("The Olmic Rune"));
        assertEquals("AVAILABLE", status("Recipe For Disaster"));
        assertTrue(QuestBook.changedAt() > 0);
    }

    @Test
    void theNameAfterTheBannerInItsOwnMessage() {
        QuestBook.onText("§6[Quest Completed]", T);
        QuestBook.onText("§aThe Olmic Rune", T + 50);
        assertEquals("COMPLETED", status("The Olmic Rune"));
    }

    @Test
    void theNameBeforeTheBanner() {
        QuestBook.onText("Recipe For Disaster", T);
        QuestBook.onText("[Quest Completed]", T + 20);
        assertEquals("COMPLETED", status("Recipe For Disaster"));
        assertEquals("STARTED", status("The Olmic Rune"));
    }

    @Test
    void namesWithApostrophesAndColonsMatchHoweverTheyAreWritten() {
        open("Pirate's Trove", "AVAILABLE", 30);
        open("Royal Trials: The Lost Heir", "AVAILABLE", 80);
        QuestBook.onText("[Quest Completed]\nPirate’s Trove", T);
        QuestBook.onText("[Quest Completed]\nRoyal Trials:  The Lost Heir", T + 5000);
        assertEquals("COMPLETED", status("Pirate's Trove"));
        assertEquals("COMPLETED", status("Royal Trials: The Lost Heir"));
    }

    @Test
    void miniQuestsAndTheTurkishBannerCountToo() {
        QuestBook.put(new QuestBook.Entry("Gather Copper", "AVAILABLE", 8, "SHORT", 120, true, null, null, null, null, null, 0, "MINI_QUEST", "EASY"));
        QuestBook.onText("[Mini-Quest Completed]\nGather Copper", T);
        QuestBook.onText("[Görev Tamamlandı]\nMurder Mystery", T + 5000);
        assertEquals("COMPLETED", status("Gather Copper"));
        assertEquals("COMPLETED", status("Murder Mystery"));
    }

    @Test
    void aNameTooLongAfterTheBannerOrWithoutOneChangesNothing() {
        QuestBook.onText("[Quest Completed]", T);
        QuestBook.onText("Lost Royalty", T + 10_000);
        QuestBook.onText("Lazarus Pit", T + 20_000);
        assertEquals("AVAILABLE", status("Lost Royalty"));
        assertEquals("AVAILABLE", status("Lazarus Pit"));
        assertEquals(0, QuestBook.changedAt());
    }

    @Test
    void theFinishedQuestLeavesTheRecommendedList() {
        assertTrue(QuestBook.all().stream().anyMatch(e -> e.name().equals("The Olmic Rune") && !"COMPLETED".equals(e.status())));
        QuestBook.onText("[Quest Complete]\nThe Olmic Rune\nRewards:", T);
        // the list's own test for "Recommended": started, or available and met at your level
        assertTrue(QuestBook.all().stream()
                .filter(e -> "STARTED".equals(e.status()) || "AVAILABLE".equals(e.status()))
                .noneMatch(e -> e.name().equals("The Olmic Rune")));
    }
}
