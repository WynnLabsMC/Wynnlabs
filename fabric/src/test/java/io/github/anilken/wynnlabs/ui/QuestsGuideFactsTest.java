package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.quests.QuestWiki;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The guide's enemies: each kind once with its count, health short, and tooltips that fit the window. */
class QuestsGuideFactsTest {

    @Test
    void repeatedMobsBecomeOneKindWithACount() {
        List<QuestWiki.Mob> mobs = List.of(
                new QuestWiki.Mob("Ricochet Sprout", 98, 52_000, List.of("FIRE"), ""),
                new QuestWiki.Mob("Core", 100, 500_000, List.of(), ""),
                new QuestWiki.Mob("Ricochet Sprout", 98, 52_000, List.of("FIRE"), ""),
                new QuestWiki.Mob("Seedling", 96, 9_500, List.of(), ""),
                new QuestWiki.Mob("Ricochet Sprout", 99, 54_000, List.of(), ""));
        List<QuestsGuideFacts.Enemy> kinds = QuestsGuideFacts.enemyKinds(mobs);
        assertEquals(3, kinds.size());
        assertEquals("Ricochet Sprout", kinds.get(0).name());          // the most common first
        assertEquals(3, kinds.get(0).count());
        assertEquals(99, kinds.get(0).level());
        assertEquals(List.of("FIRE"), kinds.get(0).weak());
        assertEquals("Core", kinds.get(1).name());                     // then the strongest
    }

    @Test
    void healthIsShortAndTooltipsStayInsideTheWindow() {
        assertEquals("500k", QuestsGuideFacts.shortNumber(500_000));
        assertEquals("1.2M", QuestsGuideFacts.shortNumber(1_234_000));
        assertEquals("950", QuestsGuideFacts.shortNumber(950));
        assertEquals("9.5k", QuestsGuideFacts.shortNumber(9_500));
        int most = Glossary.maxLines(320, 9);
        assertTrue(most * 10 + 16 <= 320 && most >= 4);
    }
}
