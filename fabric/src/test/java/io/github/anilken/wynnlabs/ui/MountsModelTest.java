package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.FeedGoal;
import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.FeedPlanner;
import io.github.anilken.wynnlabs.mounts.FeedStep;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.MountStat;
import io.github.anilken.wynnlabs.mounts.MountStats;
import io.github.anilken.wynnlabs.mounts.MountType;
import io.github.anilken.wynnlabs.mounts.Range;
import io.github.anilken.wynnlabs.mounts.RoadMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MountsModelTest {

    private static final String[] IDS = {"SPEED", "ACCELERATION", "ALTITUDE", "ENERGY", "HANDLING", "TOUGHNESS", "BOOST", "TRAINING"};

    private static MountRead read(String type, String altitudeId) {
        List<MountRead.Stat> stats = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            String id = i == 2 ? altitudeId : IDS[i];
            stats.add(new MountRead.Stat(id, id, i + 1, 10 + i, 30 + i));
        }
        return new MountRead(type, "Test " + type, 0, 10, 30, stats, false, "", "");
    }

    @Test
    void aWyvernsStatsLandInThePlannersOrder() {
        Mount m = MountsModel.convert(read("WYVERN", "ALTITUDE"));
        assertEquals(MountType.WYVERN, m.type());
        assertEquals(3, m.stats().get(MountStat.ALTITUDE).level());
        assertEquals(17, m.stats().get(MountStat.TRAINING).limit());
        assertEquals(30 * 8 + 28, m.potential());
    }

    @Test
    void aHorsesJumpHeightIsItsAltitude() {
        Mount m = MountsModel.convert(read("HORSE", "JUMP_HEIGHT"));
        assertEquals(12, m.stats().get(MountStat.ALTITUDE).limit());
    }

    @Test
    void aMountMissingAStatOrOfAnUnknownTypeIsLeftOut() {
        MountRead r = read("WYVERN", "ALTITUDE");
        MountRead seven = new MountRead(r.type(), r.name(), 0, 0, 0, r.stats().subList(0, 7), false, "", "");
        assertNull(MountsModel.convert(seven));
        assertNull(MountsModel.convert(read("DRAGON", "ALTITUDE")));
    }

    @Test
    void theKeyStaysWhileItIsFedButNotAcrossMounts() {
        MountRead a = read("WYVERN", "ALTITUDE");
        List<MountRead.Stat> fed = new ArrayList<>();
        for (MountRead.Stat s : a.stats()) {
            fed.add(new MountRead.Stat(s.id(), s.name(), s.level() + 1, s.limit() + 2, s.max()));
        }
        MountRead later = new MountRead(a.type(), a.name(), a.potential(), 5, 30, fed, false, "", "");
        assertEquals(a.key(), later.key());
        assertNotEquals(a.key(), read("HORSE", "JUMP_HEIGHT").key());
    }

    /** A fresh Wyvern and its estimated plan, as the shopping list counts it. */
    private static MountsShop.Feeding fresh(String name) {
        FeedPlan p = FeedPlanner.estimate(MountStats.fresh(), FeedGoal.breedReady(),
                RoadMap.Options.defaults(MountType.WYVERN).feedOptions());
        return new MountsShop.Feeding(name, p);
    }

    @Test
    void theShoppingListCoversTheWholeGeneration() {
        List<MountsShop.Feeding> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(fresh("W" + i));
        }
        Map<Material, Integer> one = five.get(0).plan().materials();
        Map<Material, Integer> all = MountsShop.foods(five);
        one.forEach((mat, n) -> assertEquals(5 * n, all.get(mat), mat.name()));
        // the first food already queued in two troughs: their plans go on from the step after it
        // (as a calculated plan does once its food is queued), so it is bought for the other three
        FeedPlan plan = five.get(0).plan();
        int k = 0;
        while (!plan.steps().get(k).isFeed()) {
            k++;
        }
        Material first = plan.steps().get(k).material();
        List<MountsShop.Feeding> fed = new ArrayList<>(five);
        fed.set(0, new MountsShop.Feeding("W0", MountsModel.advanced(plan, k + 1)));
        fed.set(1, new MountsShop.Feeding("W1", MountsModel.advanced(plan, k + 1)));
        assertEquals(5 * one.get(first) - 2, MountsShop.foods(fed).get(first));
        MountsShop.Shop shop = MountsShop.shop(fed, null, null, 0);
        assertEquals(5 * one.get(first) - 2, shop.need().get(first));
        assertTrue(shop.forWhom().get(first).contains("W2"));
        // one in the bag and two in the bank cover three, the rest is bought
        MountsShop.Shop had = MountsShop.shop(five, mat -> mat == first ? 1 : 0, mat -> mat == first ? 2 : 0, 0);
        assertEquals(3, had.banked().get(first));
        assertEquals(5 * one.get(first) - 3, had.need().get(first));
    }

    @Test
    void anUnfinishedMountOfAnEarlierGenerationStillCounts() {
        MountsModel.Owned bred = new MountsModel.Owned(null, null, null, 0, 1);
        MountsModel.Owned fresh = new MountsModel.Owned(null, null, null, 0, 2);
        assertEquals(List.of(fresh), MountsRoad.unfinished(List.of(bred, fresh), o -> o == bred));
    }

    @Test
    void identicalMountsShareOneChipAndTheOneThatCanActIsShown() {
        FeedPlan plan = fresh("x").plan();
        FeedPlan later = MountsModel.advanced(plan, 1);
        List<MountsGroups.Member> five = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            five.add(new MountsGroups.Member("k" + i, "Azure-Horn", i, 0, plan, null));
        }
        List<MountsGroups.Group> one = MountsGroups.group(five);
        assertEquals(1, one.size());
        assertEquals(5, one.get(0).size());
        // row 2 eats (a timer), row 4 is further along: three chips, the ready ones with the full plan shown first
        List<MountsGroups.Member> mixed = new ArrayList<>(five);
        mixed.set(1, new MountsGroups.Member("k2", "Azure-Horn", 2, 300, plan, null));
        mixed.set(3, new MountsGroups.Member("k4", "Azure-Horn", 4, 0, later, null));
        List<MountsGroups.Group> three = MountsGroups.group(mixed);
        assertEquals(3, three.size());
        assertEquals(3, three.get(0).size(), "rows 1, 3 and 5");
        assertEquals(0, MountsGroups.pick(three));
        // nothing can act now: the soonest timer
        List<MountsGroups.Member> busy = List.of(new MountsGroups.Member("a", "A", 1, 900, plan, null),
                new MountsGroups.Member("b", "B", 2, 120, later, null));
        assertEquals(1, MountsGroups.pick(MountsGroups.group(busy)));
        // a picked mount stays picked while it is there
        assertEquals(1, MountsGroups.find(three, "k2"));
        assertEquals(0, MountsGroups.find(three, "gone"));
    }

    @Test
    void aRideBeforeTheFoodIsTheStepShownFirst() {
        Material paper = io.github.anilken.wynnlabs.mounts.MountMaterials.byName("Birch Paper");
        FeedPlan p = new FeedPlan(true, true, List.of(FeedStep.train(MountStat.ALTITUDE, 10, 600, 0), FeedStep.feed(paper, 900, false)),
                1500, 900, 600, 0, MountStats.fresh(), 0, "test");
        FeedStep first = FeederOverlay.firstStep(p);
        assertEquals(FeedStep.Kind.TRAIN, first.kind());
        assertEquals(10, first.toLevel());
        assertNull(FeederOverlay.firstStep(null));
    }

    @Test
    void aRideAlreadyDoneIsSkippedAndTheNextFoodIsShownWithItsStock() {
        Material paper = io.github.anilken.wynnlabs.mounts.MountMaterials.byName("Birch Paper");
        FeedPlan p = new FeedPlan(true, true, List.of(FeedStep.train(MountStat.ALTITUDE, 10, 2700, 0), FeedStep.feed(paper, 900, false)),
                3600, 900, 2700, 0, MountStats.fresh(), 0, "test");
        MountStats at10 = MountStats.uniform(10, 10, 30);            // Altitude already ridden to 10
        FeedStep first = FeederOverlay.firstStep(io.github.anilken.wynnlabs.mounts.FeedProgress.pending(p, at10, 60));
        assertEquals(FeedStep.Kind.FEED, first.kind());
        assertEquals(paper, first.material());
        assertEquals(List.of(MountsShop.Source.BAG), MountsShop.allot(List.of(first.material()), m -> m == paper ? 1 : 0, m -> 0));
    }

    @Test
    void theStockIsAllottedToTheRowsInOrder() {
        Material oil = io.github.anilken.wynnlabs.mounts.MountMaterials.byName("Gudgeon Oil");
        Material plank = io.github.anilken.wynnlabs.mounts.MountMaterials.byName("Acacia Plank");
        List<MountsShop.Source> four = MountsShop.allot(List.of(oil, oil, oil, oil), m -> 1, m -> 2);
        assertEquals(List.of(MountsShop.Source.BAG, MountsShop.Source.BANK, MountsShop.Source.BANK, MountsShop.Source.BUY), four);
        // each food has its own stock; a row with nothing next gets no mark
        List<MountsShop.Source> mixed = MountsShop.allot(java.util.Arrays.asList(oil, null, plank, oil),
                m -> m == oil ? 1 : 0, m -> 0);
        assertEquals(java.util.Arrays.asList(MountsShop.Source.BAG, null, MountsShop.Source.BUY, MountsShop.Source.BUY), mixed);
    }

    @Test
    void estimatesAreAlwaysRanges() {
        assertEquals("~620–700", MountsText.range(new Range(620, 700)));
        assertEquals("~700", MountsText.range(Range.of(700)));
    }
}
