package io.github.anilken.wynnlabs.quests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Once every quest left is open to your level, the recommended list runs in order. */
class QuestOrderTest {

    private static QuestBook.Entry quest(String name, String status, int level, String type) {
        return new QuestBook.Entry(name, status, level, "SHORT", 100, true, null, null, null, null, null, 0, type, "EASY");
    }

    @Test
    void inOrderOnlyWhenNothingLeftIsAboveYou() {
        List<QuestBook.Entry> book = List.of(quest("A", "AVAILABLE", 40, "QUEST"),
                quest("B", "COMPLETED", 110, "QUEST"), quest("C", "AVAILABLE", 115, "MINI_QUEST"));
        assertTrue(QuestAdvisor.inOrder(book, 106));
        assertFalse(QuestAdvisor.inOrder(List.of(quest("A", "AVAILABLE", 40, "QUEST"), quest("D", "AVAILABLE", 101, "QUEST")), 90));
        assertFalse(QuestAdvisor.inOrder(List.of(quest("B", "COMPLETED", 10, "QUEST")), 106));
    }

    @Test
    void startedThenLevelThenNearest() {
        List<QuestAdvisor.Suggestion> list = new ArrayList<>(List.of(
                new QuestAdvisor.Suggestion(quest("Far", "AVAILABLE", 12, "QUEST"), 0.9, 3000, List.of()),
                new QuestAdvisor.Suggestion(quest("High", "AVAILABLE", 60, "QUEST"), 0.99, 10, List.of()),
                new QuestAdvisor.Suggestion(quest("Begun", "STARTED", 40, "QUEST"), 0.1, 900, List.of()),
                new QuestAdvisor.Suggestion(quest("Near", "AVAILABLE", 12, "QUEST"), 0.2, 200, List.of()),
                new QuestAdvisor.Suggestion(quest("Low", "AVAILABLE", 3, "QUEST"), 0.3, -1, List.of())));
        list.sort(QuestAdvisor.IN_ORDER);
        assertEquals(List.of("Begun", "Low", "Near", "Far", "High"), list.stream().map(s -> s.quest().name()).toList());
    }
}
