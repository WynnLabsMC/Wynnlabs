package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fewest skill points a set needs over every equip order, with many pieces that give points:
 * two Lv 121 Mage raid builds from WynnBuilder links, where WynnBuilder shows 186 and 192.
 */
class SkillPointOrderTest {

    private static final String[] SLOTS = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "RING1", "RING2", "BRACELET", "NECKLACE", "WEAPON"};

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static Damage.Piece piece(String name, Slot type, Map<SkillKind, Integer> reqs, Map<SkillKind, Integer> gives) {
        Map<String, Double> stats = new HashMap<>();
        gives.forEach((k, v) -> stats.put("raw" + k.name().charAt(0) + k.name().substring(1).toLowerCase(java.util.Locale.ROOT), (double) v));
        return new Damage.Piece(ItemStack.EMPTY, name, name, type, 1, stats, reqs, 0, Map.of(), 0);
    }

    private static Map<String, Damage.Piece> decode(String link) {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(link);
        assertNotNull(b, link);
        Map<String, ItemData> byName = new HashMap<>();
        GameState.current().allGear().forEach(i -> byName.putIfAbsent(i.name(), i));
        Map<String, Damage.Piece> out = new LinkedHashMap<>();
        for (WynnBuilderLink.Piece p : b.pieces()) {
            ItemData info = byName.get(p.name());
            assertNotNull(info, p.name());
            out.put(SLOTS[p.slot()], Damage.piece(info));
        }
        assertEquals(9, out.size(), out.keySet().toString());
        return out;
    }

    /** Every order of the pieces that give points, the others after them: the brute-force answer. */
    private static int exhaustive(List<Damage.Piece> pieces) {
        List<Damage.Piece> movers = new ArrayList<>();
        List<Damage.Piece> rest = new ArrayList<>();
        for (Damage.Piece p : pieces) {
            boolean gives = p.helpsRequirements() && p.stats().entrySet().stream()
                    .anyMatch(e -> e.getKey().startsWith("raw") && e.getValue() > 0 && isSkill(e.getKey()));
            (gives ? movers : rest).add(p);
        }
        int[] best = {Integer.MAX_VALUE};
        permute(movers, 0, rest, best);
        return best[0];
    }

    private static boolean isSkill(String key) {
        return Arrays.stream(SkillKind.values()).anyMatch(s -> key.equalsIgnoreCase("raw" + s.name()));
    }

    private static void permute(List<Damage.Piece> movers, int k, List<Damage.Piece> rest, int[] best) {
        if (k == movers.size()) {
            List<Damage.Piece> order = new ArrayList<>(movers);
            order.addAll(rest);
            best[0] = Math.min(best[0], Arrays.stream(Build.orderNeed(order)).sum());
            return;
        }
        for (int i = k; i < movers.size(); i++) {
            java.util.Collections.swap(movers, k, i);
            permute(movers, k + 1, rest, best);
            java.util.Collections.swap(movers, k, i);
        }
    }

    private static void check(String link, int expected) {
        Map<String, Damage.Piece> b = decode(link);
        long t0 = System.nanoTime();
        int[] min = Build.minPoints(b.values());
        long t1 = System.nanoTime();
        int sum = Arrays.stream(min).sum();
        Fixtures.note("needs " + Arrays.toString(min) + " = " + sum + " in " + (t1 - t0) / 1000 + " us");
        assertEquals(expected, sum, Arrays.toString(min));
        assertEquals(expected, exhaustive(new ArrayList<>(b.values())), "every equip order");
        // no skill above the largest requirement any piece has in it
        for (SkillKind s : SkillKind.values()) {
            int top = b.values().stream().mapToInt(p -> p.reqs().getOrDefault(s, 0)).max().orElse(0);
            assertTrue(min[s.ordinal()] <= top, s + " " + min[s.ordinal()] + " > " + top);
        }
        assertNotNull(Build.allocate(b, 121, 200), "fits 200 points");
    }

    @Test
    void championsValianceRaidBuild() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        check("https://wynnbuilder.github.io/builder/#CZ0g2nDJGI4jWGbHgC2hJq3oupKSeMGeWB1+ra3bqczp9krwkU4D", 186);
    }

    @Test
    void rebelEthosRaidBuild() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        check("https://wynnbuilder.github.io/builder/#CY0g2nDJSI4jWWfJCTIhJMbnupKSS90qFluMpT-G3", 192);
    }

    @Test
    void onlyTheBlockingSkillGoesUp() {
        // A needs Str 20 and Dex 10, gives Int 10; B needs Int 30, gives Dex 10. Either order
        // costs 50; raising both stuck pieces' needs at once (Dex and Int) cost 60. Five rings
        // that give a point of Agility make it more pieces than the old order search tried
        List<Damage.Piece> pieces = new ArrayList<>();
        pieces.add(piece("A", Slot.HELMET, Map.of(SkillKind.STRENGTH, 20, SkillKind.DEXTERITY, 10), Map.of(SkillKind.INTELLIGENCE, 10)));
        pieces.add(piece("B", Slot.BOOTS, Map.of(SkillKind.INTELLIGENCE, 30), Map.of(SkillKind.DEXTERITY, 10)));
        for (int i = 0; i < 5; i++) {
            pieces.add(piece("ring" + i, Slot.RING, Map.of(), Map.of(SkillKind.AGILITY, 1)));
        }
        int[] min = Build.minPoints(pieces);
        assertEquals(50, Arrays.stream(min).sum(), Arrays.toString(min));
        assertEquals(20, min[SkillKind.STRENGTH.ordinal()]);
    }

    @Test
    void noSkillPastItsLargestRequirement() {
        // C needs Int 65 and Str 30, gives Dex 15; D needs Dex 40 and Int 20, gives Str 20;
        // E needs Str 25, gives Int 5. With seven pieces giving points the answer stays in
        // reach of each skill's own largest requirement
        List<Damage.Piece> pieces = new ArrayList<>();
        pieces.add(piece("C", Slot.HELMET, Map.of(SkillKind.INTELLIGENCE, 65, SkillKind.STRENGTH, 30), Map.of(SkillKind.DEXTERITY, 15)));
        pieces.add(piece("D", Slot.CHESTPLATE, Map.of(SkillKind.DEXTERITY, 40, SkillKind.INTELLIGENCE, 20), Map.of(SkillKind.STRENGTH, 20)));
        pieces.add(piece("E", Slot.LEGGINGS, Map.of(SkillKind.STRENGTH, 25), Map.of(SkillKind.INTELLIGENCE, 5)));
        for (int i = 0; i < 4; i++) {
            pieces.add(piece("ring" + i, Slot.RING, Map.of(), Map.of(SkillKind.AGILITY, 1)));
        }
        int[] min = Build.minPoints(pieces);
        assertEquals(exhaustive(pieces), Arrays.stream(min).sum(), Arrays.toString(min));
        assertTrue(min[SkillKind.INTELLIGENCE.ordinal()] <= 65, Arrays.toString(min));
        assertTrue(min[SkillKind.STRENGTH.ordinal()] <= 30, Arrays.toString(min));
        assertTrue(min[SkillKind.DEXTERITY.ordinal()] <= 40, Arrays.toString(min));
    }
}
