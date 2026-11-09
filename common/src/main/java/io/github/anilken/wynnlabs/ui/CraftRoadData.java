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

    private final Map<String, ProfessionPlan.Spot> spots = new HashMap<>();

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

    /** A row of a gather step's spot list: the spot and the town it lies near (null when none is). */
    record SpotRow(ProfessionPlan.Spot spot, String town) {}

    // made when first asked (no initialisers: safe to hot swap)
    private ItemIndex fieldsOf;
    private Map<String, List<ProfessionPlan.Field>> fields;
    private List<Entry> towns;
    private Map<String, List<SpotRow>> spotRows;

    /** The fields of a material's resource, biggest first (worked out once per index); empty when it has none. */
    List<ProfessionPlan.Field> fields(String material) {
        LevelRoad.Source s = source(material);
        if (s == null) {
            return List.of();
        }
        ItemIndex index = ItemIndex.get();
        if (fields == null || fieldsOf != index) {
            fieldsOf = index;
            fields = new HashMap<>();
            spotRows = new HashMap<>();
            towns = index.all().stream().filter(e -> e.kind() == Kind.PLACE).toList();
        }
        List<ProfessionPlan.Field> f = fields.get(s.resource());
        if (f == null) {
            ProfessionPlan.Resource r = resource(s);
            f = r == null ? List.of() : ProfessionPlan.fields(r.nodes());
            fields.put(s.resource(), f);
        }
        return f;
    }

    /** The index's resource a source names, or null. */
    private ProfessionPlan.Resource resource(LevelRoad.Source s) {
        for (ProfessionPlan.Resource r : resources()) {
            if (r.name().equals(s.resource()) && r.profession().equals(s.profession())) {
                return r;
            }
        }
        return null;
    }

    /**
     * A material's spots to gather at, near and small to far and big ({@link ProfessionPlan#spotsFor}),
     * at most {@code limit}, each with the town it lies near; worked out again only when the player
     * has moved a little or the index is new.
     */
    List<SpotRow> spotRows(String material, int limit) {
        List<ProfessionPlan.Field> f = fields(material);
        if (f.isEmpty()) {
            return List.of();
        }
        double[] pos = position();
        String key = material + "|" + limit + (pos == null ? "|-" : "|" + Math.round(pos[0] / 16) + "," + Math.round(pos[1] / 16));
        List<SpotRow> rows = spotRows.get(key);
        if (rows == null) {
            if (spotRows.size() > 64) {
                spotRows.clear();
            }
            ProfessionPlan.Resource r = resource(source(material));
            List<SpotRow> out = new ArrayList<>();
            for (ProfessionPlan.Spot sp : ProfessionPlan.spotsFor(r, f, pos, limit)) {
                out.add(new SpotRow(sp, ItemIndex.nearestTown(sp.node().x(), sp.node().z(), towns)));
            }
            rows = List.copyOf(out);
            spotRows.put(key, rows);
        }
        return rows;
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
