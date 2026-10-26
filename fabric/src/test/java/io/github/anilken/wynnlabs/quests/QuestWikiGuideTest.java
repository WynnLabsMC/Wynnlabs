package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a guide shows of a stage: cleaned lines, the hints kept, its list label and the coordinates it names inline. */
class QuestWikiGuideTest {

    private static QuestWiki.Stage stage(String task, String... hints) {
        return new QuestWiki.Stage("Stage 6", task, List.of(hints), null, List.of(), List.of());
    }

    @Test
    void placeholdersAndFancyLettersAreCleaned() {
        assertEquals("1 Ingredient topla ya da tarif kitabına bak.", QuestWiki.tidy("[X, Y, Z] noktasında [1 {Ingredient}] topla ya da tarif kitabına bak."));
        assertEquals("Collect 1 Ingredient or check the recipe book.", QuestWiki.tidy("Collect [1 {Ingredient}] at [X, Y, Z] or check the recipe book."));
        assertEquals("A label reads Corkian Murder Bot.", QuestWiki.tidy("A label reads ℂ𝕠𝕣𝕜𝕚𝕒𝕟 𝕄𝕦𝕣𝕕𝕖𝕣 𝔹𝕠𝕥."));
        assertEquals("The Pit", QuestWiki.tidy("Th̵̲͚͐͒e Pi̴̧̹̚͝t"));
        assertEquals("Tüm malzemeleri topladın, Your Chef'e dönmelisin.", QuestWiki.tidy("Tüm malzemeleri topladın, [Your Chef'e dönmelisin."));
        assertEquals("[+1 Audience Invitation Letter]", QuestWiki.tidy("[+1 Audience Invitation Letter]"));
    }

    @Test
    void namedPlacesAndUsedForAreNotDialogue() {
        QuestWiki.Stage st = stage("Collect", "Cargo: 2027, 74, -4631", "Used for: Brie", "Ava: Hello there.", "Go on.");
        assertEquals(List.of(0, 1, 3), QuestWiki.shownHints(st, null));
    }

    @Test
    void aQuotedEnglishHeadingWithNothingUnderItGoes() {
        QuestWiki.Stage st = stage("Ask", "Take it to Ava.", "\"If you got two invitation letter somehow and you go to the receptionist alone\"");
        assertEquals(List.of(0), QuestWiki.shownHints(st, null));
    }

    @Test
    void aHeadingThatOnlyOpensADialogueGivesWayToTheOneBefore() {
        QuestWiki.Stage st = stage("Ask", "If you look around by yourself:", "If Ava is with you and you go in the alleyway, the following dialogue will play:",
                "Ava: Wait wait wait.", "In the room after the door key you find a key.");
        assertEquals(List.of(0, 3), QuestWiki.shownHints(st, null));
    }

    @Test
    void aChoiceIsShortOrAConditionALongLineLeadsIn() {
        assertTrue(QuestWiki.choice("Etrafa tek başına bakarsan:", null));
        assertTrue(QuestWiki.choice("Kendi başına etrafa bakmaya devam edersen ve resepsiyon görevlisinin yanına yalnız gidersen:", null));
        assertFalse(QuestWiki.choice("Adada, körlüğünü bir saniyeliğine kaldıran birkaç mor bitki var. Bu bitkilerin yerleri şöyle:", null));
        assertFalse(QuestWiki.choice("Go on.", null));
    }

    @Test
    void aStageListLabel() {
        assertEquals("Corkus City'nin yakınında Ava ile buluş", QuestWiki.listLabel(stage("[-1573, 86, -2762] noktasında, Corkus City'nin yakınında Ava ile buluş"), null));
        assertEquals("Council ile görüşme fırsatı edinmenin bir yolunu Corkus City'de sağa sola sor.",
                QuestWiki.listLabel(stage("Council ile görüşme fırsatı edinmenin bir yolunu Corkus City'de sağa sola sor. Ava [-1615, 68, -2924] noktasında"), null));
        assertEquals("Büyülü Malzemeler", QuestWiki.listLabel(stage("", "Uygulamalı Büyü:", "▸ Büyülü Malzemeler", "Gather the materials."), null));
        assertEquals("Hayalet Rehber ile konuş.", QuestWiki.taskText("Hayalet Rehber ile konuş:"));
        assertEquals("Yemek odasında Hamsey ile buluş.", QuestWiki.listLabel(stage("[927, 53, -4305] noktasındaki yemek odasında Hamsey ile buluş."), null));
    }

    @Test
    void coordinatesInALine() {
        List<int[]> points = List.of(new int[] {-1650, 86, -2940});
        List<GuideCoords.Spot> spots = GuideCoords.find("Yukarıda iki sandık var: biri -1655, -2937, diğeri -1500, -2000 noktasında. "
                + "Merdiven (-1639, 68, -2908) noktasında.", points);
        assertEquals(3, spots.size());
        assertArrayEquals(new int[] {-1655, 86, -2937}, spots.get(0).at(), "x and z, the height of the stage point next to it");
        assertTrue(spots.get(0).knowsY());
        assertFalse(spots.get(1).knowsY(), "no point near: the player's height");
        assertArrayEquals(new int[] {-1639, 68, -2908}, spots.get(2).at());
    }

    @Test
    void coordinatesWithoutCommasAndNumbersThatAreNot() {
        assertArrayEquals(new int[] {-1797, 0, -2120}, GuideCoords.find("Granite cave (-1797 -2120)", List.of()).get(0).at());
        assertArrayEquals(new int[] {-1656, 75, -2939}, GuideCoords.find("a key at (-1656 75 -2939) in the attic", List.of()).get(0).at());
        assertTrue(GuideCoords.find("İlk sandıkta 6 Tender Pork Fillets, 4 Lockpicking Kits ve 64 Zümrüt, ikinci sandıkta 320 Zümrüt var.", List.of()).isEmpty());
        assertTrue(GuideCoords.find("Levels 25-28, and 10 tries at most, 2 stars.", List.of()).isEmpty());
    }
}
