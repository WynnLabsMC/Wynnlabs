package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.LevelRoad;
import io.github.anilken.wynnlabs.crafting.ProfessionPlan;
import io.github.anilken.wynnlabs.crafting.RoadChoices;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.ProfBomb;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Compass;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the Meslek page draws from, worked out only when its inputs change: the gathering
 * resources of the search index (which material each gives), the profession bombs Wynntils knows
 * of (asked every few seconds), each profession's item types by fewest crafts, and the level road
 * itself ({@link LevelRoad}).
 */
final class CraftRoadData {

    /** For the preview: {xp into the level, xp the level needs}. */
    static volatile int[] previewXp;
    /** For the preview: bombs to show instead of Wynntils'. */
    static volatile List<ProfBomb> previewBombs;

    private final CraftingTab tab;
    private ItemIndex resourcesOf;
    private List<ProfessionPlan.Resource> resources = List.of();
    private final Map<String, LevelRoad.Source> sources = new HashMap<>();
    private List<ProfBomb> bombs = List.of();
    private long bombsAt;
    private String optionsKey = "";
    private List<ProfessionPlan.CraftOption> options = List.of();
    private String roadKey = "";
    private LevelRoad.Road road;

    CraftRoadData(CraftingTab tab) {
        this.tab = tab;
    }

    /** {xp into the level, xp it needs} of a profession; {0, 0} when not known. */
    static int[] xp(String profession) {
        int[] p = previewXp;
        return p != null ? p : Crafting.professionXp(profession);
    }

    /** The index's gathering resources, read again only when the index is new. */
    List<ProfessionPlan.Resource> resources() {
        ItemIndex index = ItemIndex.get();
        if (index != resourcesOf) {
            List<ProfessionPlan.Resource> out = new ArrayList<>();
            sources.clear();
            for (Entry e : index.all()) {
                if (e.kind() != Kind.RESOURCE || e.type() == null) {
                    continue;
                }
                List<String> mats = new ArrayList<>();
                for (Entry.Origin o : e.links(Entry.ITEM)) {
                    mats.add(o.name());
                    sources.putIfAbsent(o.name(), new LevelRoad.Source(e.type().toLowerCase(Locale.ROOT), e.level(), e.name()));
                }
                out.add(new ProfessionPlan.Resource(e.name(), e.type().toLowerCase(Locale.ROOT), e.level(), e.places(), List.copyOf(mats)));
            }
            resources = List.copyOf(out);
            resourcesOf = index;
        }
        return resources;
    }

    /** The resource a material is gathered from ("Gold Ingot" → Mining Lv 20 Gold), or null. */
    LevelRoad.Source source(String material) {
        resources();
        return sources.get(material);
    }

    /** The nearest node of a material's resource and its distance, or null. */
    ProfessionPlan.Spot nearest(String material) {
        LevelRoad.Source s = source(material);
        if (s == null) {
            return null;
        }
        double[] pos = position();
        // a few steps of walking do not change which node is nearest enough to matter
        String key = material + (pos == null ? "|-" : "|" + Math.round(pos[0] / 16) + "," + Math.round(pos[1] / 16));
        if (spots.containsKey(key)) {
            return spots.get(key);
        }
        if (spots.size() > 64) {
            spots.clear();
        }
        ProfessionPlan.Spot found = null;
        for (ProfessionPlan.Spot sp : ProfessionPlan.spots(resources(), s.profession(), 999, pos, 999)) {
            if (sp.resource().name().equals(s.resource())) {
                found = sp;
                break;
            }
        }
        spots.put(key, found);
        return found;
    }

    private final Map<String, ProfessionPlan.Spot> spots = new HashMap<>();
    private String nearestKey = "";
    private List<ProfessionPlan.Spot> nearestList = List.of();

    /**
     * A gathering profession's spots its level allows, nearest first (ties by level, higher first),
     * at most six; worked out again when the player has moved a few blocks.
     */
    List<ProfessionPlan.Spot> nearestSpots(String profession, int level) {
        double[] pos = position();
        String key = profession + "|" + level + "|" + System.identityHashCode(ItemIndex.get())
                + (pos == null ? "|-" : "|" + Math.round(pos[0] / 8) + "," + Math.round(pos[1] / 8));
        if (!key.equals(nearestKey)) {
            nearestKey = key;
            List<ProfessionPlan.Spot> all = new ArrayList<>(ProfessionPlan.spots(resources(), profession, level, pos, 999));
            all.sort(java.util.Comparator.<ProfessionPlan.Spot>comparingDouble(s -> Double.isNaN(s.distance()) ? Double.MAX_VALUE : s.distance())
                    .thenComparing(s -> -s.resource().level()));
            nearestList = List.copyOf(all.subList(0, Math.min(6, all.size())));
        }
        return nearestList;
    }

    /** The nearest place of a service ("trade-market"), or null. */
    Area service(String type) {
        double[] pos = position();
        Area best = null;
        double bd = Double.MAX_VALUE;
        for (Entry e : ItemIndex.get().entries()) {
            if (e.kind() != Kind.SERVICE || !type.equals(e.type())) {
                continue;
            }
            for (Area a : e.places()) {
                double d = Compass.distance(a, pos);
                if (best == null || d < bd) {
                    best = a;
                    bd = d;
                }
            }
        }
        return best;
    }

    static double[] position() {
        try {
            return GameState.current().position();
        } catch (Throwable t) {
            return null;
        }
    }

    /** The profession bombs that still run, asked of Wynntils every two seconds. */
    List<ProfBomb> bombs() {
        List<ProfBomb> p = previewBombs;
        if (p != null) {
            return p;
        }
        long now = System.currentTimeMillis();
        if (now - bombsAt > 2000) {
            bombsAt = now;
            try {
                bombs = Wyn.professionBombs();
            } catch (Throwable t) {
                bombs = List.of();
            }
        }
        return bombs;
    }

    /** The Profession XP bomb on this world (×2 XP for crafting and gathering), or null. */
    ProfBomb xpBombHere() {
        for (ProfBomb b : bombs()) {
            if (b.here() && !b.speed()) {
                return b;
            }
        }
        return null;
    }

    /** A crafting profession's item types, fewest crafts to the target first. */
    List<ProfessionPlan.CraftOption> options(String profession, int level, int target) {
        int[] xp = xp(profession);
        String key = profession + "|" + level + "|" + target + "|" + xp[0] + "|" + xp[1] + "|" + Prices.version;
        if (!key.equals(optionsKey)) {
            optionsKey = key;
            options = ProfessionPlan.crafting(CraftData.all(), profession, level, target, xp[0], xp[1], Crafting::materialPrice);
        }
        return options;
    }

    /** The road for a crafting profession to its target, built again only when what it rests on changes. */
    LevelRoad.Road road(String profession, String type, int level, int target) {
        RoadChoices ch = RoadChoices.get();
        int[] xp = xp(profession);
        ProfBomb bomb = xpBombHere();
        double mult = bomb == null ? 1 : 2;
        String key = profession + "|" + type + "|" + level + "|" + target + "|" + xp[0] + "|" + xp[1] + "|" + ch.pref + "|" + ch.routes
                + "|" + ch.ticked(profession) + "|" + System.identityHashCode(tab.stock) + "|" + Prices.version + "|" + mult + "|"
                + System.identityHashCode(ItemIndex.get());
        if (!key.equals(roadKey) || road == null) {
            roadKey = key;
            road = LevelRoad.build(new LevelRoad.Inputs(CraftData.all(), profession, type, level, target, xp[0], xp[1],
                    this::owned, this::source, p -> tab.knownLevel(p), Crafting::materialPrice, ch.pref, Map.copyOf(ch.routes),
                    java.util.Set.copyOf(ch.ticked(profession)), mult));
        }
        return road;
    }

    /** A material in the bag and the bank, every tier. */
    private int owned(String material) {
        int n = 0;
        for (int t = 1; t <= 3; t++) {
            n += tab.stock.material(material, t);
        }
        return n;
    }
}
