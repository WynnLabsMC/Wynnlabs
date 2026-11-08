package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.crafting.ProfessionPlan;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.GatherTool;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What a gathering profession's page draws from, worked out only when its inputs change: the
 * spots (the best and the nearest), the tools (in the bag and by level) and the gathering kit.
 */
final class CraftGatherData {

    /** Crafts whose items are worn (the bonus stays) and those whose items are used up. */
    static final List<String> KIT_GEAR = List.of("armouring", "tailoring", "jeweling");
    static final List<String> KIT_USE = List.of("alchemism", "cooking", "scribing");
    /** The ingredients' gathering stats: more XP, faster. */
    static final String KIT_XP = "gatherXpBonus";
    static final String KIT_SPEED = "gatherSpeed";
    private static final String CHARM_XP = "gatheringExperience";
    /** Rows per spot list. */
    private static final int SPOTS = 3;

    private final CraftRoadData road;

    private String spotsKey = "";
    private List<ProfessionPlan.Spot> bestList = List.of();
    private List<ProfessionPlan.Spot> nearList = List.of();

    private List<GatherTool> allTools = List.of();
    private List<GatherTool> heldTools = List.of();
    private long heldAt;

    private String kitKey = "";
    private List<List<Entry>> kitLists = List.of();
    private ItemIndex charmOf;
    private Entry charm;

    CraftGatherData(CraftRoadData road) {
        this.road = road;
    }

    /**
     * A gathering profession's spots its level allows, each at its nearest dense spot: the best
     * (highest level first, they give the most XP) or the nearest ones the best list does not
     * already show, at most three each; worked out again when the player has moved a few blocks.
     */
    List<ProfessionPlan.Spot> spots(String profession, int level, boolean nearest) {
        double[] pos = CraftRoadData.position();
        String key = profession + "|" + level + "|" + System.identityHashCode(ItemIndex.get())
                + (pos == null ? "|-" : "|" + Math.round(pos[0] / 8) + "," + Math.round(pos[1] / 8));
        if (!key.equals(spotsKey)) {
            spotsKey = key;
            List<ProfessionPlan.Spot> all = ProfessionPlan.spots(road.resources(), profession, level, pos, 999);
            bestList = List.copyOf(all.subList(0, Math.min(SPOTS, all.size())));
            Set<String> shown = new HashSet<>();
            bestList.forEach(s -> shown.add(s.resource().name()));
            List<ProfessionPlan.Spot> near = new ArrayList<>();
            for (ProfessionPlan.Spot s : all) {
                if (!shown.contains(s.resource().name())) {
                    near.add(s);
                }
            }
            near.sort(Comparator.comparingDouble(s -> Double.isNaN(s.distance()) ? Double.MAX_VALUE : s.distance()));
            nearList = List.copyOf(near.subList(0, Math.min(SPOTS, near.size())));
        }
        return nearest ? nearList : bestList;
    }

    /** The best tool of a gathering profession in the bag (highest tier), or null; read once a second. */
    GatherTool heldTool(String profession) {
        long now = System.currentTimeMillis();
        if (now - heldAt > 1000) {
            heldAt = now;
            try {
                heldTools = Wyn.heldTools();
            } catch (Throwable t) {
                heldTools = List.of();
            }
        }
        GatherTool best = null;
        for (GatherTool t : heldTools) {
            if (t.profession().equalsIgnoreCase(profession) && (best == null || t.tier() > best.tier())) {
                best = t;
            }
        }
        return best;
    }

    /**
     * A gathering profession's tool by level: the best the level allows ({@code open}), else the
     * first one past it; null when there is none.
     */
    GatherTool tool(String profession, int level, boolean open) {
        if (allTools.isEmpty()) {
            try {
                allTools = Wyn.gatherTools();
            } catch (Throwable t) {
                allTools = List.of();
            }
        }
        GatherTool pick = null;
        for (GatherTool t : allTools) {
            if (!t.profession().equalsIgnoreCase(profession) || (t.level() <= level) != open) {
                continue;
            }
            if (pick == null || (open ? t.tier() > pick.tier() : t.tier() < pick.tier())) {
                pick = t;
            }
        }
        return pick;
    }

    /**
     * The gathering kit for a combat level: the ingredients it can use that give gathering XP or
     * speed, in four lists (worn XP, worn speed, used-up XP, used-up speed), the best two of
     * each by the stat's top roll, the cheaper first on a tie.
     */
    List<List<Entry>> kit(int level) {
        ItemIndex index = ItemIndex.get();
        String key = level + "|" + System.identityHashCode(index);
        if (key.equals(kitKey)) {
            return kitLists;
        }
        List<List<Entry>> out = new ArrayList<>();
        for (List<String> crafts : List.of(KIT_GEAR, KIT_USE)) {
            for (String stat : List.of(KIT_XP, KIT_SPEED)) {
                List<Entry> pick = new ArrayList<>();
                for (Entry e : index.all()) {
                    if (e.kind() == Kind.INGREDIENT && e.level() <= level && statMax(e, stat) > 0
                            && e.professions().stream().anyMatch(crafts::contains)) {
                        pick.add(e);
                    }
                }
                pick.sort(Comparator.comparingInt((Entry e) -> -statMax(e, stat))
                        .thenComparingDouble(e -> {
                            Prices.Price pr = Prices.of(e.name());
                            Double v = pr == null ? null : pr.typical();
                            return v == null ? Double.MAX_VALUE : v;
                        }));
                out.add(List.copyOf(pick.subList(0, Math.min(2, pick.size()))));
            }
        }
        kitKey = key;
        kitLists = List.copyOf(out);
        return kitLists;
    }

    /** The charm that gives the most gathering XP, or null; looked up again only when the index is new. */
    Entry kitCharm() {
        ItemIndex index = ItemIndex.get();
        if (index != charmOf) {
            charmOf = index;
            charm = null;
            for (Entry e : index.all()) {
                if (e.kind() == Kind.CHARM && statMax(e, CHARM_XP) > 0
                        && (charm == null || statMax(e, CHARM_XP) > statMax(charm, CHARM_XP))) {
                    charm = e;
                }
            }
        }
        return charm;
    }

    /** The charm's gathering XP as {min, max}, or null. */
    static int[] charmXp(Entry charm) {
        return stat(charm, CHARM_XP);
    }

    /** An entry's stat as {min, max}, or null when it has none. */
    static int[] stat(Entry e, String key) {
        for (Entry.Stat st : e.stats()) {
            if (st.key().equals(key)) {
                return new int[] {Math.min(st.min(), st.max()), Math.max(st.min(), st.max())};
            }
        }
        return null;
    }

    private static int statMax(Entry e, String key) {
        int[] r = stat(e, key);
        return r == null ? 0 : r[1];
    }
}
