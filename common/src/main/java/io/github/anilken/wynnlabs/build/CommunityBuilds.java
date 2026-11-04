package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.advice.AutoAdvice;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The community build list ({@code data/community-builds.json} in our repository, fetched by
 * {@link CommunityList}): its entries, the filters the build window and the local API use, and
 * each entry compared with what the player has: per piece whether it is equipped, in the bag, on
 * a bank page or missing (with its Trade Market price when one is cached), the skill points the
 * pieces need, and how the build's damage, effective health, mana and walk speed differ from what
 * the player wears. No Minecraft screen code, so a recorded game state runs it headless.
 */
public final class CommunityBuilds {

    /** The play style tags an entry may carry. */
    public static final List<String> TAGS = List.of("damage", "spell", "melee", "tank", "healer", "support", "speed", "hybrid", "budget",
            "leveling", "lootrun", "raid", "solo", "xp-bonus", "gathering-xp");
    public static final List<String> CLASSES = List.of("warrior", "mage", "archer", "assassin", "shaman", "any");
    public static final List<String> SOURCES = List.of("discord", "forum", "community", "issue");
    /** The build window's role filters: each matches entries holding any of its tags. */
    public static final Map<String, List<String>> ROLES = roles();
    public static final int MAX_DESCRIPTION = 200;

    private CommunityBuilds() {}

    private static Map<String, List<String>> roles() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("raid", List.of("raid"));
        m.put("lootrun", List.of("lootrun"));
        m.put("leveling", List.of("leveling"));
        m.put("xp", List.of("xp-bonus", "gathering-xp"));
        return java.util.Collections.unmodifiableMap(m);
    }

    /** One build of the list. {@code klass} is lower case ("mage"), "any" for a set without a weapon. */
    public record Entry(String id, String name, String link, String klass, int levelMin, int levelMax, List<String> tags,
            String description, String source, String checked, boolean outdated, String credit) {

        public boolean any() {
            return "any".equals(klass);
        }

        /** The class it is for; NONE for "any". */
        public Klass klassType() {
            return any() ? Klass.NONE : Klass.valueOf(klass.toUpperCase(Locale.ROOT));
        }

        /** Listed for {@code k} (null: every class): its own class, or "any". */
        public boolean forClass(Klass k) {
            return k == null || any() || klassType() == k;
        }

        public boolean hasTag(String t) {
            return tags.contains(t);
        }

        /** Whether one of a role's tags is on it. */
        public boolean hasRole(String role) {
            List<String> ts = ROLES.get(role);
            return ts != null && ts.stream().anyMatch(tags::contains);
        }
    }

    /** The file: its tag list, its entries (malformed ones left out, counted in {@code skipped}). */
    public record Doc(int version, String updated, List<String> tags, List<Entry> builds, int skipped) {}

    /** Reads the file; null when it is not the list at all (not JSON, no builds array). */
    public static Doc parse(String json) {
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        if (!o.has("builds") || !o.get("builds").isJsonArray()) {
            return null;
        }
        List<String> tags = new ArrayList<>();
        if (o.has("tags") && o.get("tags").isJsonArray()) {
            o.getAsJsonArray("tags").forEach(t -> tags.add(t.getAsString()));
        }
        List<Entry> out = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        int skipped = 0;
        for (JsonElement el : o.getAsJsonArray("builds")) {
            Entry e = entry(el);
            if (e == null || !ids.add(e.id())) {
                skipped++;
            } else {
                out.add(e);
            }
        }
        int version = o.has("version") ? o.get("version").getAsInt() : 1;
        String updated = o.has("updated") ? o.get("updated").getAsString() : "";
        return new Doc(version, updated, List.copyOf(tags.isEmpty() ? TAGS : tags), List.copyOf(out), skipped);
    }

    /** One entry, or null when a field it needs is missing or out of range (unknown fields are ignored). */
    static Entry entry(JsonElement el) {
        try {
            JsonObject e = el.getAsJsonObject();
            String klass = e.get("class").getAsString().toLowerCase(Locale.ROOT);
            int min = e.get("levelMin").getAsInt();
            int max = e.get("levelMax").getAsInt();
            List<String> tags = new ArrayList<>();
            e.getAsJsonArray("tags").forEach(t -> tags.add(t.getAsString()));
            String id = e.get("id").getAsString();
            String link = e.get("link").getAsString();
            if (id.isBlank() || link.isBlank() || !CLASSES.contains(klass) || min < 1 || max > 106 || min > max) {
                return null;
            }
            return new Entry(id, str(e, "name", id), link, klass, min, max, List.copyOf(tags), str(e, "description", ""),
                    str(e, "source", "issue"), str(e, "checked", ""), e.has("outdated") && e.get("outdated").getAsBoolean(),
                    e.has("credit") ? e.get("credit").getAsString() : null);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    /**
     * The entries for class {@code k} (null: every class; "any" entries always), whose level range
     * holds {@code level} (0: any level), holding every tag in {@code tags}.
     */
    public static List<Entry> filter(Collection<Entry> entries, Klass k, int level, Collection<String> tags) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.forClass(k) && (level <= 0 || level >= e.levelMin() && level <= e.levelMax())
                    && (tags == null || e.tags().containsAll(tags))) {
                out.add(e);
            }
        }
        return out;
    }

    // ---- what the player has ----

    /** Where an owned piece is; MISSING when the player has none. */
    public enum Place { EQUIPPED, BAG, BANK, MISSING }

    /**
     * What the player has, read once on the game thread: the names equipped and in the bag (with
     * counts), the first reachable bank page of each banked name, and every count as the road map
     * counts owned gear ({@link Roadmap#ownedCounts}).
     */
    public record Owned(Map<String, Integer> equipped, Map<String, Integer> bag, Map<String, Integer> bankPage,
            Map<String, Integer> counts, List<CraftedOwn> crafted) {

        public static final Owned NONE = new Owned(Map.of(), Map.of(), Map.of(), Map.of(), List.of());

        /** A short fingerprint, for knowing when comparisons are stale. */
        public int stamp() {
            return java.util.Objects.hash(equipped, bag, bankPage, crafted);
        }
    }

    /**
     * A crafted piece the player has: its type, level and where (bank page when banked). A crafted
     * item's lore names no ingredients, so a link's crafted piece is matched by type and the
     * recipe's level range ({@link #craftedMatch}).
     */
    public record CraftedOwn(Slot type, int level, Place place, int bankPage) {}

    /** Reads {@link Owned} from {@code state} (the game thread: it reads the inventory and the worn gear). */
    public static Owned owned(GameState state) {
        Map<String, Integer> equipped = new HashMap<>();
        Map<String, Integer> bag = new HashMap<>();
        Map<String, Integer> bank = new HashMap<>();
        List<CraftedOwn> crafted = new ArrayList<>();
        try {
            Set<ItemStack> worn = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (Slot t : Slot.values()) {
                if (t.isArmour() || t.isAccessory() || t.klass() != null) {
                    for (ItemStack w : AutoAdvice.worn(t)) {
                        if (w != null && !w.isEmpty() && worn.add(w)) {
                            equipped.merge(name(w), 1, Integer::sum);
                        }
                    }
                }
            }
            for (ItemStack w : worn) {
                crafted(state, w, Place.EQUIPPED, 0, crafted);
            }
            for (ItemStack s : state.inventory()) {
                if (s != null && !s.isEmpty() && !worn.contains(s)) {
                    bag.merge(name(s), s.getCount(), Integer::sum);
                    crafted(state, s, Place.BAG, 0, crafted);
                }
            }
            for (Vault.Group g : Vault.groups()) {
                for (Vault.Stored s : g.places()) {
                    if (s.gear() && Vault.reachable(s.page()) && !Vault.isBag(s.page())) {
                        bank.putIfAbsent(g.name(), Vault.pageNumber(s.page()));
                        if (s.name().contains("Crafted")) {
                            crafted(state, Vault.stackOf(s), Place.BANK, Vault.pageNumber(s.page()), crafted);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // a menu changed under us: what was read so far
        }
        return new Owned(Map.copyOf(equipped), Map.copyOf(bag), Map.copyOf(bank), Map.copyOf(Roadmap.ownedCounts(state)),
                List.copyOf(crafted));
    }

    /** {@code s} into {@code out} when it is a crafted piece of gear. */
    private static void crafted(GameState state, ItemStack s, Place place, int page, List<CraftedOwn> out) {
        if (s == null || s.isEmpty()) {
            return;
        }
        state.view(s).flatMap(io.github.anilken.wynnlabs.model.ItemView::craftedPart)
                .ifPresent(c -> out.add(new CraftedOwn(c.slot(), c.level(), place, page)));
    }

    private static String name(ItemStack s) {
        return ItemJson.clean(s.getHoverName().getString()).trim();
    }

    // ---- an entry against it ----

    /**
     * One piece of an entry: its link place (0 helmet … 8 weapon), the item (null for a crafted,
     * custom or unknown piece), where the player has it, the bank page when banked, its cached
     * price when missing (null: none), and whether it is made from the link (crafted or custom).
     */
    public record Piece(int slot, String name, ItemData info, Place place, int bankPage, Double price, boolean made,
            RecipeCode.Recipe recipe, Long craftCost) {

        /** A crafted piece of the link (its recipe reads). */
        public boolean crafted() {
            return recipe != null;
        }

        public boolean owned() {
            return place != Place.MISSING;
        }
    }

    /**
     * The changes against what the player wears: damage and effective health in percent, mana
     * back every 5 s in points, walk speed in percent points; a null part is not known.
     */
    public record Deltas(Double dmg, Double ehp, Double mana, Double speed) {}

    /**
     * An entry against what the player has: its nine pieces, how many are owned, the known total
     * of the missing pieces' prices and how many have none, the fewest skill points per skill its
     * pieces need and the link's own, the highest level a piece needs, the skill point problem at
     * the player's level (null: none), the deltas (null: another class or nothing to compare) and
     * the kept items' names (what "Set as goal" builds around).
     */
    public record Compare(String id, List<Piece> pieces, int owned, int total, long knownCost, int unpriced, int[] need,
            List<Integer> linkPoints, int needLevel, Locks.Problem problem, Deltas deltas, List<String> kept) {

        public int missing() {
            return total - owned;
        }

        /** Owned share in percent (0 for an empty build). */
        public int ownedPct() {
            return total == 0 ? 0 : Math.round(100f * owned / total);
        }
    }

    /**
     * {@code e} against what the player has ({@code owned}, read on the game thread) at the
     * player's class and level; prices from {@code price} (the cache only, never a request);
     * {@code deltas} false skips the damage model. Null when the link does not decode.
     */
    public static Compare compare(GameState state, Entry e, Owned owned, Function<String, Prices.Price> price, boolean deltas) {
        Map<String, Ingredient> ings = new HashMap<>();
        io.github.anilken.wynnlabs.game.Wyn.craftIngredients().forEach(i -> ings.put(i.name(), i));
        return compare(state, e, owned, price, ings::get, r -> null, deltas);
    }

    /**
     * {@link #compare} with the ingredients by name and the cost to craft a missing crafted piece
     * ({@code craftCost}, null: not known).
     */
    public static Compare compare(GameState state, Entry e, Owned owned, Function<String, Prices.Price> price,
            Function<String, Ingredient> ingredients, Function<RecipeCode.Recipe, Long> craftCost, boolean deltas) {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(e.link());
        if (b == null) {
            return null;
        }
        LinkImport.Result r = LinkImport.of(state, b, ingredients);
        List<CraftedOwn> craftedLeft = new ArrayList<>(owned.crafted());
        Map<String, Integer> left = new HashMap<>();
        owned.equipped().forEach((k, v) -> left.merge("E|" + k, v, Integer::sum));
        owned.bag().forEach((k, v) -> left.merge("B|" + k, v, Integer::sum));
        List<Piece> pieces = new ArrayList<>();
        int have = 0;
        long cost = 0;
        int unpriced = 0;
        int needLevel = 0;
        List<ItemData> items = new ArrayList<>();
        for (LinkImport.Row row : r.rows()) {
            WynnBuilderLink.Piece p = row.piece();
            if (p.kind() == WynnBuilderLink.Kind.ITEM && (p.name() == null || p.name().startsWith("No ")) && row.info() == null) {
                continue;                               // an empty slot of the link
            }
            ItemData info = row.info();
            boolean made = p.kind() != WynnBuilderLink.Kind.ITEM;
            String name = info != null ? info.name() : row.made() != null ? row.made().name() : p.name() == null ? "?" : p.name();
            Place place = Place.MISSING;
            int page = 0;
            RecipeCode.Recipe recipe = p.kind() == WynnBuilderLink.Kind.CRAFTED ? p.recipe() : null;
            if (recipe != null) {
                CraftedOwn mine = craftedMatch(recipe, craftedLeft);
                if (mine != null) {
                    craftedLeft.remove(mine);
                    place = mine.place();
                    page = mine.bankPage();
                }
            }
            if (info != null) {
                items.add(info);
                needLevel = Math.max(needLevel, info.level());
                if (take(left, "E|" + name)) {
                    place = Place.EQUIPPED;
                } else if (take(left, "B|" + name)) {
                    place = Place.BAG;
                } else if (owned.bankPage().containsKey(name) && take(left, "K|" + name, owned.counts().getOrDefault(name, 1)
                        - owned.equipped().getOrDefault(name, 0) - owned.bag().getOrDefault(name, 0))) {
                    place = Place.BANK;
                    page = owned.bankPage().get(name);
                }
            }
            Double pr = null;
            Long craft = null;
            if (place == Place.MISSING) {
                if (recipe != null) {
                    // a crafted piece has no market price: what crafting it costs
                    craft = craftCost.apply(recipe);
                    if (craft == null) {
                        unpriced++;
                    } else {
                        cost += craft;
                    }
                } else {
                    Prices.Price pp = info == null ? null : price.apply(name);
                    pr = pp == null ? null : pp.typical();
                    if (pr == null) {
                        unpriced++;
                    } else {
                        cost += Math.round(pr);
                    }
                }
            } else {
                have++;
            }
            pieces.add(new Piece(p.slot(), name, info, place, page, pr, made, recipe, craft));
        }
        Map<String, Damage.Piece> set = LinkImport.set(r);
        int[] need = Build.minPoints(set.values());
        int level = state.level();
        Locks.Problem problem = items.isEmpty() ? null : Locks.problem(items, level);
        Deltas d = deltas && !e.any() && e.klassType() == state.classType() ? deltas(set, level) : null;
        List<String> kept = r.kept().stream().map(ItemData::name).toList();
        return new Compare(e.id(), List.copyOf(pieces), have, pieces.size(), cost, unpriced, need, b.skills(), needLevel, problem, d, kept);
    }

    /**
     * The crafted piece the player has that stands for {@code r}: the same type, its level inside
     * the recipe's level range (an equipped one first, then the bag, then the bank); null for none.
     */
    static CraftedOwn craftedMatch(RecipeCode.Recipe r, List<CraftedOwn> mine) {
        Slot type = Slot.of(r.type().toUpperCase(Locale.ROOT));
        CraftData.Recipe rec = CraftData.find(r.type(), r.minLevel());
        int lo = rec == null ? r.minLevel() : rec.minLevel();
        int hi = rec == null ? r.minLevel() + 2 : rec.maxLevel();
        CraftedOwn best = null;
        for (CraftedOwn c : mine) {
            if (c.type() == type && c.level() >= lo && c.level() <= hi && (best == null || c.place().ordinal() < best.place().ordinal())) {
                best = c;
            }
        }
        return best;
    }

    /** The search's words in an entry's link: its items' names and its crafted pieces' ingredients. */
    public static List<String> searchWords(Entry e) {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(e.link());
        if (b == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (WynnBuilderLink.Piece p : b.pieces()) {
            if (p.kind() == WynnBuilderLink.Kind.ITEM && p.name() != null && !p.name().startsWith("No ")) {
                out.add(p.name());
            }
            RecipeCode.Recipe r = p.recipe();
            if (r != null) {
                r.slots().stream().filter(java.util.Objects::nonNull).forEach(out::add);
            }
        }
        return out;
    }

    /** Whether the entry's link holds a crafted piece. */
    public static boolean hasCrafted(Entry e) {
        WynnBuilderLink.Build b = WynnBuilderLink.decode(e.link());
        return b != null && b.pieces().stream().anyMatch(p -> p.kind() == WynnBuilderLink.Kind.CRAFTED);
    }

    /** Takes one of {@code key} from {@code left}; true when there was one. */
    private static boolean take(Map<String, Integer> left, String key) {
        Integer n = left.get(key);
        if (n == null || n <= 0) {
            return false;
        }
        left.put(key, n - 1);
        return true;
    }

    /** {@link #take} with {@code start} copies the first time {@code key} is asked for. */
    private static boolean take(Map<String, Integer> left, String key, int start) {
        left.putIfAbsent(key, Math.max(0, start));
        return take(left, key);
    }

    /**
     * The set (its open slots with what the player wears) against what the player wears, both as
     * the model scores them at {@code level}; null without a plan or a weapon.
     */
    static Deltas deltas(Map<String, Damage.Piece> set, int level) {
        Build.Plan plan = Build.plan();
        if (plan == null || plan.current() == null || !set.containsKey("WEAPON")) {
            return null;
        }
        Map<String, Damage.Piece> full = new LinkedHashMap<>(set);
        if (plan.worn() != null) {
            plan.worn().forEach((slot, p) -> {
                if (p != null) {
                    full.putIfAbsent(slot, p);
                }
            });
        }
        int earned = plan.earned();
        Build.Eval ev = Build.allocate(full, level, earned);
        if (ev == null) {
            int need = 0;
            for (int v : Build.minPoints(full.values())) {
                need += v;
            }
            ev = Build.allocate(full, level, Math.max(earned, need));
        }
        if (ev == null) {
            return null;
        }
        Damage.Result now = plan.current();
        Damage.Result to = ev.result();
        Double dmg = now.factors().length > 0 && to.factors().length > 0 && now.factors()[0] > 0
                ? (to.factors()[0] / now.factors()[0] - 1) * 100 : null;
        Double ehp = now.ehp() > 0 ? (to.ehp() / now.ehp() - 1) * 100 : null;
        return new Deltas(dmg, ehp, to.manaPer5() - now.manaPer5(), to.speed() - now.speed());
    }

    /** The entries as JSON for the local API, each with its comparison when one is known. */
    public static JsonArray json(List<Entry> entries, Map<String, Compare> compared) {
        JsonArray out = new JsonArray();
        for (Entry e : entries) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("name", e.name());
            o.addProperty("link", e.link());
            o.addProperty("class", e.klass());
            o.addProperty("levelMin", e.levelMin());
            o.addProperty("levelMax", e.levelMax());
            JsonArray tags = new JsonArray();
            e.tags().forEach(tags::add);
            o.add("tags", tags);
            o.addProperty("description", e.description());
            o.addProperty("source", e.source());
            o.addProperty("checked", e.checked());
            o.addProperty("outdated", e.outdated());
            if (e.credit() != null) {
                o.addProperty("credit", e.credit());
            }
            Compare c = compared == null ? null : compared.get(e.id());
            if (c != null) {
                JsonArray own = new JsonArray();
                JsonArray miss = new JsonArray();
                for (Piece p : c.pieces()) {
                    JsonObject po = new JsonObject();
                    po.addProperty("name", p.name());
                    if (p.owned()) {
                        po.addProperty("where", p.place() == Place.BANK ? "bank page " + p.bankPage() : p.place().name().toLowerCase(Locale.ROOT));
                        own.add(po);
                    } else {
                        if (p.price() != null) {
                            po.addProperty("price", Math.round(p.price()));
                        }
                        if (p.craftCost() != null) {
                            po.addProperty("craftCost", p.craftCost());
                        }
                        if (p.made()) {
                            po.addProperty("made", true);
                        }
                        miss.add(po);
                    }
                }
                o.add("owned", own);
                o.add("missing", miss);
                o.addProperty("knownCost", c.knownCost());
                o.addProperty("unpriced", c.unpriced());
            }
            out.add(o);
        }
        return out;
    }
}
