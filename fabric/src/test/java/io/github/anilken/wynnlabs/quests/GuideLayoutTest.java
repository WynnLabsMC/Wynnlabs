package io.github.anilken.wynnlabs.quests;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A guide stage as the guide screen lays it out: parts, options, choices, items, places and the wiki's leftovers gone. */
class GuideLayoutTest {

    private static QuestWiki.Stage stage(String... hints) {
        return new QuestWiki.Stage("Stage 5", "Ask around", List.of(hints), null, List.of(), List.of());
    }

    private static List<GuideLayout.Row> common(String... hints) {
        return GuideLayout.of(stage(hints), null).common();
    }

    @Test
    void optionsWithTheSameStepsShowOnce() {
        GuideLayout.View v = GuideLayout.of(stage("Read the letter.", "Mechanic Outfit =", "If you pay the vendor:",
                "[+1 Audience Invitation Letter]", "Azure Outfit =", "If you pay the vendor:", "[+1 Audience Invitation Letter]",
                "Power Outfit =", "If you pay the vendor:", "[+1 Audience Invitation Letter]", "Ava: Hm."), null);
        assertEquals(1, v.common().size());
        assertEquals(1, v.options().size());
        assertEquals(List.of("Mechanic Outfit", "Azure Outfit", "Power Outfit"), v.options().get(0).labels());
        List<GuideLayout.Row> rows = v.options().get(0).rows();
        assertEquals(GuideLayout.Kind.CHOICE, rows.get(0).kind());
        assertEquals("If you pay the vendor", rows.get(0).text());
        assertEquals(new GuideLayout.Row(GuideLayout.Kind.ITEM, "+1 Audience Invitation Letter", 0, true, null, null, null), rows.get(1));
    }

    @Test
    void optionsThatNearlyReadTheSameAreOne() {
        String key = "In the room right after the door key you can find a key, then climb the ladder and open the trapdoor with it.";
        GuideLayout.View v = GuideLayout.of(stage("Mechanic Outfit =", "Ava walks to the centre of Corkus City.", "If you look around by yourself:", key,
                "Azure Outfit =", "Ava walks towards the centre of Corkus City.", "If you keep looking around by yourself:", key), null);
        assertEquals(1, v.options().size());
        GuideLayout.View apart = GuideLayout.of(stage("Mechanic Outfit =", "Go to the receptionist.",
                "Emerald Outfit =", "Go to the receptionist.", "Bring the receptionist money."), null);
        assertEquals(2, apart.options().size());
    }

    @Test
    void aLoneOptionIsAPlainChoice() {
        GuideLayout.View v = GuideLayout.of(stage("Join the race.", "Victory =", "You won the race.", "+1 Snail Slime"), null);
        assertTrue(v.options().isEmpty());
        assertEquals(GuideLayout.Kind.CHOICE, v.common().get(1).kind());
        assertEquals("Victory", v.common().get(1).text());
        assertEquals(GuideLayout.Kind.ITEM, v.common().get(3).kind());
        assertTrue(v.common().get(3).under());
    }

    @Test
    void partsHoldTheirOwnOptionsAndWhatTheyAreFor() {
        GuideLayout.View v = GuideLayout.of(stage("▸ Voidroot", "Used for: Brie", "Take the crane to the Void.",
                "▸ Snail Slime", "Used for: Brie, Kale", "Join the race.", "Loss =", "You lost.", "Victory =", "You won.",
                "▸ Talking Mushroom", "Hit it to pick it up."), null);
        assertEquals(3, v.parts().size());
        assertEquals("Brie", v.parts().get(0).usedFor());
        assertEquals(2, v.parts().get(1).options().size());
        assertEquals("Talking Mushroom", v.parts().get(2).name());
        assertEquals("Hit it to pick it up.", v.parts().get(2).common().get(0).text());
    }

    @Test
    void anOptionKeepsItsEnglishNameWhenTheTranslationDoes() {
        assertEquals("Emerald-Encrusted Outfit", GuideLayout.label("Zümrüt İşlemeli Kıyafet", "Emerald-Encrusted Outfit",
                "emerald-encrusted outfit'i almak için"));
        assertEquals("Toprak", GuideLayout.label("Toprak", "Earth", "toprağın gücü"));
        assertEquals("Büyülerinle onu korkutup kaçırırsan", GuideLayout.label("Büyülerinle onu korkutup kaçırırsan",
                "If you scare it off with your spells", "if you scare it off with your spells"));
    }

    @Test
    void aLongParagraphIsCutIntoNumberedSentences() {
        String para = "Go to the room right after the door key at -1657, 75, -2940 and pick up the key. "
                + "Then go back up to the room with the ladder and climb it. Use the key on the trapdoor and it opens. "
                + "There are two chests up there.";
        List<GuideLayout.Row> rows = common(para);
        assertEquals(4, rows.size());
        assertEquals(GuideLayout.Kind.STEP, rows.get(0).kind());
        assertEquals(3, rows.get(2).number());
        assertEquals(-1657, rows.get(0).at()[0]);
    }

    @Test
    void twoParagraphsUnderOneChoiceNumberOn() {
        String cut = "In the room right after the door key you find a key on the floor near the wall. Right-click it to get an "
                + "Oddly-Shaped Key, then head back upstairs. Climb the ladder and use the key on the trapdoor.";
        List<GuideLayout.Row> rows = common("If you look around by yourself:", cut, "After finding the contract, go up the ladder.");
        assertEquals(List.of(1, 2, 3, 4), rows.subList(1, 5).stream().map(GuideLayout.Row::number).toList());
        assertTrue(rows.subList(1, 5).stream().allMatch(GuideLayout.Row::under));
    }

    @Test
    void itemsWithOrWithoutBrackets() {
        assertEquals(GuideLayout.Kind.ITEM, common("+1 Voidroot").get(0).kind());
        assertEquals("+750 Cooking XP", common("[+750 Cooking XP]").get(0).text());
        assertEquals(GuideLayout.Kind.BULLET, common("+5 to every stat when you wear the whole set of armour").get(0).kind());
    }

    @Test
    void placesBecomeANumberedList() {
        List<GuideLayout.Row> rows = common(
                "You land on an island in the Void and stay blind there. Bits of the Sky Islands fell on it. The notable things are:",
                "Cargo: 2027, 74, -4631", "Some kind of cargo... it smells of fresh cookies.", "Furry Eggs: 2032, 74, -4643",
                "1995, 69, -4650", "2051, 69, -4676");
        assertEquals(GuideLayout.Kind.BULLET, rows.get(0).kind());
        GuideLayout.Row cargo = rows.get(1);
        assertEquals(GuideLayout.Kind.PLACE, cargo.kind());
        assertEquals("Cargo", cargo.text());
        assertEquals("Some kind of cargo... it smells of fresh cookies.", cargo.note());
        assertArrayEquals(new int[] {2027, 74, -4631}, cargo.at());
        assertTrue(cargo.under());
        assertEquals(List.of(1, 2, 3, 4), rows.subList(1, 5).stream().map(GuideLayout.Row::number).toList());
        assertEquals("", rows.get(3).text());
    }

    @Test
    void aCaveWithXAndZOnly() {
        GuideLayout.Row r = common("Granite cave (-1797 -2120) - for getting the L-I-G item").get(0);
        assertEquals(GuideLayout.Kind.PLACE, r.kind());
        assertEquals("Granite cave", r.text());
        assertEquals("-1797, -2120", r.place());
        assertEquals("for getting the L-I-G item", r.note());
        assertNull(r.at());
    }

    @Test
    void aConditionNamingAPlaceStaysAChoiceWithItsCoordinates() {
        QuestWiki.Stage tr = stage("Kıyafetleri (-1470, 95, -2936) noktasındaki odada bir lavaboda yıkadıktan sonra:",
                "Kıyafetleri yıkamaya başlıyorsun...");
        QuestWiki.Stage en = stage("After washing the clothes in a sink in the room at (-1470, 95, -2936):", "You start washing the clothes...");
        GuideLayout.Row r = GuideLayout.of(tr, en).common().get(0);
        assertEquals(GuideLayout.Kind.CHOICE, r.kind());
        assertTrue(r.text().contains("(-1470, 95, -2936)"), r.text());
        assertEquals(1, GuideCoords.find(r.text(), List.of()).size(), "the coordinates are one clickable group");
        assertEquals(GuideLayout.Kind.CHOICE, common("[-2016, 29, -869] noktasında Vagabond ile konuşunca:", "Birini arıyorum.").get(0).kind());
        assertEquals(GuideLayout.Kind.PLACE, common("[1244, 76, -956] noktasındaki delik arenaya çıkar:", "Arenada bir patlama oldu.").get(0).kind());
    }

    @Test
    void aShortHeadingIsAChoiceALongLineLeadsIn() {
        assertEquals(GuideLayout.Kind.CHOICE, common("If you talk to Ava first:", "Go.").get(0).kind());
        List<GuideLayout.Row> rows = common("You must answer a series of questions. The answers are:", "1. Intelligence.", "2. Mermaids.");
        assertEquals(GuideLayout.Kind.BULLET, rows.get(0).kind());
        assertEquals(GuideLayout.Kind.STEP, rows.get(2).kind());
        assertEquals(2, rows.get(2).number());
        assertEquals("Mermaids.", rows.get(2).text());
    }

    @Test
    void theFeathersFlyStageFiveIsOneListForEveryOutfit() {
        QuestGuides.Guide g = QuestGuides.allTurkish().stream().filter(x -> x.id().equals("The Feathers Fly Part II")).findFirst().orElseThrow();
        StringBuilder all = new StringBuilder();
        g.translated().forEach(s -> all.append(s.task()).append(' ').append(String.join(" ", s.hints())).append(' '));
        GuideLayout.View v = GuideLayout.of(g.translated().get(4), g.english().get(4), all.toString().toLowerCase(java.util.Locale.ROOT));
        assertEquals(1, v.options().size(), "the outfits differ only in wording");
        assertEquals(List.of("Mechanic Outfit", "Emerald-Encrusted Outfit", "Azure Outfit", "Power Outfit"), v.options().get(0).labels());
        List<GuideLayout.Row> rows = v.options().get(0).rows();
        assertTrue(rows.stream().noneMatch(r -> r.text().startsWith("\"If")), "the quoted English heading is gone");
        GuideLayout.Row look = rows.stream().filter(r -> r.kind() == GuideLayout.Kind.CHOICE).reduce((a, b) -> b).orElseThrow();
        assertEquals("Etrafa tek başına bakarsan", look.text());
        assertEquals(8, rows.get(rows.size() - 1).number());
    }

    @Test
    void wikiLeftoversAreCleaned() {
        assertEquals("Katze follows the guard.", QuestWiki.tidy("Katze follows the guard. 200px|thumb|Arnol talking to the player"));
        assertEquals("", QuestWiki.tidy("200x150px|thumb|Auburn group."));
        assertEquals("", QuestWiki.tidy("32px|link= » Military Watchtower"));
        assertEquals("Kill Dead Miners", QuestWiki.tidy("Kill Dead Miners200px|left"));
        assertEquals("Walk to each sample and click it.", QuestWiki.tidy("Walk to each sample and click it.|Magical Effects|60"));
        assertEquals("[+1 Elemental Training Wand/Bow/Spear/Dagger/Relik]", QuestWiki.tidy("c|var(--color-wc-purple)|[+1 Elemental Training {Wand/Bow/Spear/Dagger/Relik}]"));
        assertEquals("[1] Pay for raspberries → [-10 Emeralds]", QuestWiki.tidy("[1] Pay for raspberries | [-10 Emeralds]"));
        assertEquals("", QuestWiki.tidy("left|thumb"));
        assertEquals("", QuestWiki.tidy("150px 150px|Luring Leni}}"));
    }

    @Test
    void noShippedGuideKeepsALeftover() {
        for (boolean tr : new boolean[] {false, true}) {
            for (QuestGuides.Guide g : tr ? QuestGuides.allTurkish() : QuestGuides.allEnglish()) {
                List<QuestWiki.Stage> st = g.translated() != null ? g.translated() : g.english();
                for (int i = 0; i < st.size(); i++) {
                    GuideLayout.View v = GuideLayout.of(st.get(i), i < g.english().size() ? g.english().get(i) : null);
                    List<GuideLayout.Row> rows = new ArrayList<>(v.common());
                    v.options().forEach(o -> rows.addAll(o.rows()));
                    v.parts().forEach(p -> {
                        rows.addAll(p.common());
                        p.options().forEach(o -> rows.addAll(o.rows()));
                    });
                    for (GuideLayout.Row r : rows) {
                        String text = r.text() + " " + (r.note() == null ? "" : r.note());
                        assertTrue(!text.contains("px|") && !text.contains("}}") && !text.contains("{{") && !text.contains("[X, Y, Z]"),
                                g.id() + " " + st.get(i).title() + ": " + text);
                    }
                }
            }
        }
    }
}
