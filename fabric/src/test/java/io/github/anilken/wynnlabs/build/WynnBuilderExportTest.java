package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.SyntheticTree;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.SkillKind;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * WynnBuilder links written by the mod: what it writes reads back the same, links the site
 * wrote come out letter for letter, and what a recorded character wears turns into a link.
 */
class WynnBuilderExportTest {

    private static final String SITE = WynnBuilderExport.SITE;

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void readsBackWhatItWrites() {
        String version = latest();
        List<WynnBuilderLink.Piece> pieces = List.of(
                piece(0, "Dune Storm", "T6T6E6"),
                piece(1, "Etiolation", "A6A6E6T4"),
                piece(3, "Revenant", "F5"),
                piece(4, "Intensity", ""),
                piece(6, "Vortex Bracer", ""),
                piece(7, "Necklace of a Thousand Storms", ""),
                piece(8, "Hero", "A6A6A6A6"));
        List<String> tomes = List.of("Abyssal Tome of Combat Mastery III", "Pulsing Tome of Defensive Mastery II",
                "Courageous Tome of Defensive Mastery I", "Brute's Tome of Allegiance");
        WynnBuilderLink.Build in = new WynnBuilderLink.Build(null, false, pieces, tomes, Arrays.asList(82, null, -5, 0, 140), 98, "");
        List<String> nodes = WynnBuilderTree.of(WynnBuilderLink.decode(SITE + HealerModelTest.LINK_A), Klass.MAGE).nodes();
        String[] aspects = WynnBuilderTree.table().get(version).aspects().get(Klass.MAGE);
        String aspect = Arrays.stream(aspects).filter(a -> a != null).findFirst().orElseThrow();
        WynnBuilderTree.Tree tree = new WynnBuilderTree.Tree(Klass.MAGE, nodes, List.of(new WynnBuilderTree.Aspect(aspect, 3)), Map.of());

        String link = WynnBuilderExport.link(in, tree);
        assertTrue(link.startsWith("https://wynnbuilder.github.io/builder/#C"), link);
        WynnBuilderLink.Build out = WynnBuilderLink.decode(link);
        assertNotNull(out, link);
        assertEquals(version, out.version(), "the newest data version");
        assertEquals(pieces.stream().map(p -> p.slot() + p.name() + p.powders()).toList(),
                out.pieces().stream().map(p -> p.slot() + p.name() + p.powders()).toList());
        assertEquals(tomes, out.tomes(), "each tome in a slot of its kind, in the site's order");
        assertEquals(in.skills(), out.skills());
        assertEquals(98, out.level());
        WynnBuilderTree.Tree back = WynnBuilderTree.of(out, Klass.MAGE);
        assertEquals(new HashSet<>(nodes), new HashSet<>(back.nodes()));
        assertEquals(List.of(new WynnBuilderTree.Aspect(aspect, 3)), back.aspects());

        // nothing known: empty slots, points left to WynnBuilder, the top level, no tree
        WynnBuilderLink.Build empty = WynnBuilderLink.decode(WynnBuilderExport.link(
                new WynnBuilderLink.Build(null, false, List.of(), List.of(), null, 0, ""), null));
        assertNotNull(empty);
        assertTrue(empty.pieces().isEmpty());
        assertTrue(empty.tomes().isEmpty());
        assertNull(empty.skills());
        assertEquals(WynnBuilderLink.table().versions().get(WynnBuilderLink.table().versions().size() - 1).maxLevel(), empty.level());
    }

    @Test
    void sitesLinksComeOutTheSame() {
        Map<String, Klass> links = new LinkedHashMap<>();
        links.put(HealerModelTest.LINK_A, Klass.MAGE);
        links.put(HealerModelTest.LINK_B, Klass.MAGE);
        links.put("CY0G7WM3me0bEHbHgCI15WX2Fk6Sq70yF+b-TtnZ", Klass.WARRIOR);           // WynnBuilderTreeTest's guide builds
        links.put("CZ0x2BwmEieMVaRLKSmrKKV2emKEMEYzHoIM4Nwd-HL6RcOX", Klass.WARRIOR);
        links.put("CZ0W9W5G000uQG0500m454+GhcXYfy-200k-Jn0", Klass.ARCHER);            // WynnBuilderLinkTest's site link
        links.forEach((hash, klass) -> {
            WynnBuilderLink.Build b = WynnBuilderLink.decode(SITE + hash);
            assertNotNull(b, hash);
            String again = WynnBuilderExport.encode(b, WynnBuilderTree.of(b, klass));
            WynnBuilderLink.Build c = WynnBuilderLink.decode(SITE + again);
            assertEquals(fields(b, klass), fields(c, klass), hash + " -> " + again);
            assertEquals(hash, again, "letter for letter");
        });
    }

    @Test
    void olderLinksMoveToTheCurrentForm() {
        WynnBuilderLink.Build b = WynnBuilderLink.decode("https://wynnbuilder.github.io/builder?v=18#11_09V10L0jn0Qt0K00OW0KI0V10Qi1I140J002C1g0000103tlU0z0z0+0+0+0+0-1T2Y2Y2Z2Z2a2a401401401401401z+xlZxe50");
        WynnBuilderLink.Build c = WynnBuilderLink.decode(WynnBuilderExport.link(b, null));
        assertNotNull(c);
        assertEquals(b.version(), c.version(), "the data version the link named");
        assertEquals(b.pieces().stream().map(p -> p.slot() + p.name() + p.powders()).toList(),
                c.pieces().stream().map(p -> p.slot() + p.name() + p.powders()).toList());
        assertEquals(b.skills(), c.skills());
        assertEquals(106, c.level());
    }

    @Test
    void aVersionTheSiteLacksGoesInAsItsNewestOne() {
        WynnBuilderLink.Build in = new WynnBuilderLink.Build(null, false, List.of(piece(8, "Ascended Immolation", "F6")),
                List.of(), null, 0, "");
        WynnBuilderLink.Build out = WynnBuilderLink.decode(WynnBuilderExport.link(in, null));
        assertEquals("Masterwork Immolation", out.pieces().get(0).name());
        assertEquals("F6", out.pieces().get(0).powders());
    }

    @Test
    void aMissingWeaponTakesOneOfTheClass() throws Exception {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        FixtureState.load(dir).install("en");
        WynnBuilderLink.Build in = new WynnBuilderLink.Build(null, false,
                List.of(new WynnBuilderLink.Piece(8, WynnBuilderLink.Kind.CRAFTED, -1, "My Relik", null, "")), List.of(), null, 0, "");
        WynnBuilderLink.Build out = WynnBuilderLink.decode(WynnBuilderExport.link(in,
                new WynnBuilderTree.Tree(Klass.SHAMAN, List.of(), List.of(), Map.of())));
        assertEquals(1, out.pieces().size());
        assertEquals(WynnBuilderLink.WEAPON, out.pieces().get(0).slot());
        assertEquals(io.github.anilken.wynnlabs.model.Slot.RELIK,
                GameState.current().allGear().filter(i -> i.name().equals(out.pieces().get(0).name())).findFirst().orElseThrow().slot());
    }

    @Test
    void whatYouWearNow() throws Exception {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        // the recording keeps no tree: a path through WynnBuilder's shaman tree stands in
        List<String> unlocked = List.of("Totem", "Relik Proficiency I", "Totemic Smash", "Haul");
        FixtureState.load(dir).withTree(SyntheticTree.nodes(), unlocked).install("en");
        GameState fixture = GameState.current();
        String version = latest();
        String aspect = Arrays.stream(WynnBuilderTree.table().get(version).aspects().get(Klass.SHAMAN))
                .filter(a -> a != null).findFirst().orElseThrow();
        // the recording has no tomes or aspects: give it some, as Wynntils would read them
        GameState.install(with(fixture, List.of("Brute's Tome of Allegiance", "Abyssal Tome of Combat Mastery III"),
                Map.of("Embodiment of the " + aspect, 2)));

        WynnBuilderLink.Build b = WynnBuilderLink.decode(WynnBuilderExport.worn());
        assertNotNull(b);
        List<String> names = b.pieces().stream().map(WynnBuilderLink.Piece::name).toList();
        assertEquals(List.of("Yume", "Nemract's Ruin", "Chained Pixels", "Galloping Spurs", "Warsong", "Precious", "Grisly Gauntlet",
                "Generator Amulet"), names.subList(0, 8));
        assertEquals(9, b.pieces().size(), "a weapon from the hotbar: " + names);
        assertEquals(WynnBuilderLink.WEAPON, b.pieces().get(8).slot());
        List<Integer> skills = Arrays.stream(SkillKind.values()).map(fixture::assignedSeen).toList();
        // WynnBuilder keeps totals and takes the gear's points off again: assigned 25/15/15/8/23
        // plus what the pieces and the relik give (Def -4 from a penalty)
        assertEquals(List.of(36, 21, 39, 4, 45), b.skills(), "assigned plus the gear's skill points");
        assertEquals(List.of(25, 15, 15, 8), skills.subList(0, 4));
        assertEquals(44, b.level());
        assertEquals(List.of("Abyssal Tome of Combat Mastery III", "Brute's Tome of Allegiance"), b.tomes(), "in the site's slot order");
        WynnBuilderTree.Tree tree = WynnBuilderTree.of(b, Klass.SHAMAN);
        assertNotNull(tree);
        assertEquals(unlocked, tree.nodes());
        assertEquals(List.of(new WynnBuilderTree.Aspect(aspect, 2)), tree.aspects(), "a name Wynntils keeps with a prefix");
    }

    @Test
    void aMilestoneSet() throws Exception {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        FixtureState.load(dir).install("en");
        GameState state = GameState.current();
        Map<String, Damage.Piece> set = new LinkedHashMap<>();
        set.put("HELMET", Damage.piece(state.armour(EquipmentSlot.HEAD)));
        set.put("RING1", Damage.piece(state.accessories().get(0)));
        // a weapon you would buy: from the item data, with the powders the road map assumes in it
        var relik = state.allGear().filter(d -> d.slot() != null && d.slot().klass() == Klass.SHAMAN && d.powderSlots() > 0)
                .findFirst().orElseThrow();
        set.put("WEAPON", Damage.piece(relik));
        WynnBuilderLink.Build b = WynnBuilderLink.decode(WynnBuilderExport.milestone(set,
                new Build.Eval(new int[] {30, 10, 0, 5, 0}, null), 50));
        assertNotNull(b);
        assertEquals(List.of("0Yume", "4Warsong", "8" + relik.name()), b.pieces().stream().map(p -> p.slot() + p.name()).toList());
        // totals: 30/10/0/5/0 assigned plus Yume's, Warsong's and the relik's points (Int -30 from a penalty)
        assertEquals(List.of(32, 12, -30, 5, 5), b.skills());
        assertEquals(50, b.level());
        assertNull(WynnBuilderExport.milestone(null, null, 50));
        // a set the road map judged with an assumed guild tome carries that tome, so WynnBuilder
        // takes its +1s off the totals instead of counting them as assigned
        Tomes.Tome rainbow = new Tomes.Tome(new int[] {1, 1, 1, 1, 1}, true);
        WynnBuilderLink.Build t = WynnBuilderLink.decode(WynnBuilderExport.milestone(set,
                new Build.Eval(new int[] {30, 10, 0, 5, 0}, null, rainbow), 50));
        assertTrue(t.tomes().contains(rainbow.name()), t.tomes().toString());
        assertEquals(List.of(33, 13, -29, 6, 6), t.skills());
    }

    // ---- helpers ----

    private static String latest() {
        var v = WynnBuilderLink.table().versions();
        return v.get(v.size() - 1).name();
    }

    private static WynnBuilderLink.Piece piece(int slot, String name, String powders) {
        return new WynnBuilderLink.Piece(slot, WynnBuilderLink.Kind.ITEM, -1, name, null, powders);
    }

    /** What a link means, without its letters: pieces, powders, tomes, points, level, tree and aspects. */
    private static List<Object> fields(WynnBuilderLink.Build b, Klass klass) {
        WynnBuilderTree.Tree t = WynnBuilderTree.of(b, klass);
        return List.of(b.version(), b.pieces().stream().map(p -> p.slot() + ":" + p.id() + ":" + p.powders()).toList(), b.tomes(),
                String.valueOf(b.skills()), b.level(), t == null ? List.of() : new HashSet<>(t.nodes()), t == null ? List.of() : t.aspects());
    }

    /** {@code base} with these tomes and aspects. */
    private static GameState with(GameState base, List<String> tomes, Map<String, Integer> aspects) {
        return (GameState) Proxy.newProxyInstance(GameState.class.getClassLoader(), new Class<?>[] {GameState.class}, (proxy, m, args) -> {
            if (m.getName().equals("tomes") && m.getParameterCount() == 0) {
                return tomes;
            }
            if (m.getName().equals("aspects") && m.getParameterCount() == 0) {
                return aspects;
            }
            try {
                return m.invoke(base, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
