package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.Klass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ability tree and aspects at the end of WynnBuilder links whose trees are known (a Light
 * Bender healer, builds from the forum's build guide), and links pasted with more text around
 * them: WynnBuilder's copied text, escaped new lines, a forum's link wrapper.
 */
class WynnBuilderTreeTest {

    private static final String SITE = "https://wynnbuilder.github.io/builder/#";
    /** A Lv 106 Light Bender healer (data 2.2.5.0). */
    private static final String HEALER = HealerModelTest.LINK_A;
    /** "Convergence - Fallen Upperbash" by Pandamonium, the guide's first build (2.2.4.0). */
    private static final String FALLEN = "CY0G7WM3me0bEHbHgCI15WX2Fk6Sq70yF+b-TtnZ";
    /** A Battle Monk from the guide's replies (2.2.5.0). */
    private static final String MONK = "CZ0x2BwmEieMVaRLKSmrKKV2emKEMEYzHoIM4Nwd-HL6RcOX";

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void healerTree() throws Exception {
        WynnBuilderTree.Tree t = WynnBuilderTree.of(WynnBuilderLink.decode(SITE + HEALER), Klass.MAGE);
        assertNotNull(t);
        assertEquals(36, t.nodes().size(), t.nodes().toString());
        assertEquals("Meteor", t.nodes().get(0), "the first node is always in");
        assertEquals(18, t.archetypes().get("Light Bender"), t.archetypes().toString());
        assertTrue(t.nodes().containsAll(List.of("Heal", "Orphion's Pulse", "Sunshower", "Fortitude", "Lightweaver", "Sunflare",
                "Interweave", "Dawn")), t.nodes().toString());
        assertEquals(5, t.aspects().size(), t.aspects().toString());
        assertEquals(List.of(new WynnBuilderTree.Aspect("Light Bender's Embodiment of Celestial Brilliance", 2),
                new WynnBuilderTree.Aspect("Aspect of the Comet", 3), new WynnBuilderTree.Aspect("Aspect of Mystic Transfer", 2)),
                t.aspects().subList(0, 3));
        // every name as the game writes it
        Set<String> game = TreeModelTest.tree("mage").stream().map(AbilityNode::name).collect(Collectors.toSet());
        assertTrue(game.containsAll(t.nodes()), "not in the game's tree: " + t.nodes().stream().filter(n -> !game.contains(n)).toList());
    }

    @Test
    void guideTrees() {
        WynnBuilderTree.Tree fallen = WynnBuilderTree.of(WynnBuilderLink.decode(SITE + FALLEN), Klass.WARRIOR);
        assertNotNull(fallen);
        assertEquals(Map.of("Fallen", 15, "Battle Monk", 1, "Paladin", 1), fallen.archetypes());
        assertEquals(38, fallen.nodes().size());
        WynnBuilderTree.Tree monk = WynnBuilderTree.of(WynnBuilderLink.decode(SITE + MONK), Klass.WARRIOR);
        assertNotNull(monk);
        assertEquals(Map.of("Fallen", 5, "Battle Monk", 16), monk.archetypes());
        // WynnBuilder's own names are written as the game writes them
        assertTrue(fallen.nodes().contains("Spear Proficiency I") || monk.nodes().contains("Spear Proficiency I"));
        assertTrue(fallen.nodes().stream().noneMatch(n -> n.matches(".* \\d$")), fallen.nodes().toString());
    }

    @Test
    void noTreeWithoutAClassOrInTheOlderForm() {
        assertNull(WynnBuilderTree.of(WynnBuilderLink.decode(SITE + HEALER), null));
        assertNull(WynnBuilderTree.of(WynnBuilderLink.decode("https://wynnbuilder.github.io/builder?v=18#11_09V10L0jn0Qt0K00OW0KI0V10Qi1I140J002C1g0000103tlU0z0z0+0+0+0+0-1T2Y2Y2Z2Z2a2a401401401401401z+xlZxe50"),
                Klass.ARCHER));
    }

    @Test
    void pastedText() {
        WynnBuilderLink.Build plain = WynnBuilderLink.decode(SITE + HEALER);
        assertNotNull(plain);
        // WynnBuilder's copied text: the link, then the items one per line
        String copied = SITE + HEALER + "\n> Wynnbuilder build:\n> Ornate Shadow Cowl\n> Soul Unmade\n> Atomizer";
        assertEquals(plain, WynnBuilderLink.decode(copied));
        assertEquals(plain, WynnBuilderLink.decode(SITE + HEALER + "%0A%3E%20Wynnbuilder%20build:%0A%3E%20Cumulonimbus"));
        assertEquals(plain, WynnBuilderLink.decode("Here is my healer: " + SITE + HEALER + " have fun"));
        assertEquals(plain, WynnBuilderLink.decode(HEALER + "\r\n> Wynnbuilder build:"));
        // a forum's link wrapper, '#' written as %23
        assertEquals(plain, WynnBuilderLink.decode("https://forums.wynncraft.com/proxy.php?link=https%3A%2F%2Fwynnbuilder.github.io%2Fbuilder%2F%23"
                + HEALER.replace("+", "%2B") + "&hash=4837b6470b9dd8f42faac0964715922c"));
        // '+' is one of the link's letters, not a space
        assertTrue(HEALER.contains("+"));
        assertNull(WynnBuilderLink.decode("check out wynnbuilder #1"));
    }

    @Test
    void importCarriesTheTree() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).as(Klass.MAGE).atLevel(106)
                .withTree(TreeModelTest.tree("mage"), List.of("Meteor")).install("en");
        LinkImport.Result r = LinkImport.of(GameState.current(), WynnBuilderLink.decode(SITE + HEALER));
        assertNotNull(r.tree());
        assertEquals(Klass.MAGE, r.tree().klass());
        assertEquals(18, r.tree().archetypes().get("Light Bender"));
        TreeModel.Overlay o = LinkImport.overlay(r);
        assertNotNull(o, "the model reads the link's tree");
        List<String> heals = o.heals.stream().map(SourceText.Heal::name).toList();
        assertTrue(heals.containsAll(List.of("Orphion's Pulse", "Sunflare")), heals.toString());
        // your own tree (only Meteor) has none of them
        TreeModel.Overlay mine = TreeModel.run("").at(106);
        assertTrue(mine.heals.stream().noneMatch(h -> h.name().equals("Sunflare")));
    }
}
