package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pictures a guide shows: a stage's puzzles, maps and screenshots, not its NPCs or mob icons. */
class GuideImagesTest {

    @Test
    void picturesOfTheWayNotOfSomeone() {
        assertTrue(GuideImages.useful("Desperate Metal", "DesperateMetalPuzzle1.png", "|200px"));
        assertTrue(GuideImages.useful("Purple and Blue", "GyliaTreasureMap.png", "|250px"));
        assertTrue(GuideImages.useful("The Feathers Fly Part II", "Ava Power Outfit.png", "|left|thumb"));
        assertTrue(GuideImages.useful("WynnExcavation Site D", "WynnExcavationSiteDPuzzle.png", ""));
        assertTrue(GuideImages.useful("WynnExcavation Site D", "WynnExcavationSiteDPuzzle.png", ""));
        assertTrue(GuideImages.useful("Dearly Departed", "Dearly Departed open grave.png", "|x300px"));
        assertFalse(GuideImages.useful("A Headless History", "Hans.png", "|200px|left"), "a portrait beside dialogue");
        assertFalse(GuideImages.useful("A Hunter's Calling", "Maxie(AHunter'sCalling).png", "|250px"), "an NPC");
        assertFalse(GuideImages.useful("Shattered Minds", "MushroomMite.png", "|100px"), "a mob icon");
        assertFalse(GuideImages.useful("The Envoy Part II", "GravelGolem.png", "|250px"), "a mob, not a grave");
        assertFalse(GuideImages.useful("Enter the Dojo", "KangarooRoomToken.png", "|48px"), "a token's icon");
        assertFalse(GuideImages.useful("A Grave Mistake", "Alem.png", "|256px"), "an NPC named only");
    }

    @Test
    void picturesGoUnderTheirStage() {
        String page = "{{Infobox/Quest}}\n[[File:Banner.png|thumb|Banner]]\n== Stage 1 ==\n» Go\n[[File:Hans.png|200px|left]]\n"
                + "== Stage 2 ==\nSolve it.\n[[File:QuestPuzzle1.png|200px]] [[File:QuestPuzzle2.png|thumb|right|250px|The second room]]\n"
                + "<gallery>\nFile:QuestMap.png|Where to dig\n</gallery>\n[[File:QuestPuzzle1.png|200px]]\n== Rewards ==\n[[File:RewardMap.png|thumb]]\n";
        Map<String, List<GuideImages.Image>> got = GuideImages.byStage("Quest", page);
        assertEquals(List.of("Stage 2"), List.copyOf(got.keySet()));
        assertEquals(List.of(new GuideImages.Image("QuestPuzzle1.png", ""), new GuideImages.Image("QuestPuzzle2.png", "The second room"),
                new GuideImages.Image("QuestMap.png", "Where to dig")), got.get("Stage 2"));
    }
}
