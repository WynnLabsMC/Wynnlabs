package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.WornGear;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.SkillKind;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * WynnBuilder build links written by the mod's own code: the inverse of {@link WynnBuilderLink}
 * (the current form, in the newest data version the mod ships) and {@link WynnBuilderTree}.
 * Items and tomes go by WynnBuilder's ids from {@code wynnbuilder-items.json}; a crafted or
 * custom piece, or a name the table does not have, goes in as an empty slot.
 *
 * <p>Tomes have fixed slots on the site: two weapon, four armour, a guild, a lootrun, two
 * marathon, two mysticism and two expertise tomes, filled in that order by what each name is.
 * The game does not show a powder's tier, so a worn piece's powders take the tier the damage
 * model assumes for the piece's level.
 */
public final class WynnBuilderExport {

    public static final String SITE = "https://wynnbuilder.github.io/builder/#";
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+-";
    private static final String ELEMENTS = "ETWFA";
    /** The tome slots per kind, in the site's order: weapon, armour, guild, lootrun, marathon, mysticism, expertise. */
    private static final int[] TOME_SLOTS = {2, 4, 1, 1, 2, 2, 2};

    private static volatile Ids ids;

    /** Name → WynnBuilder id, for items and tomes (the game's names and WynnBuilder's own). */
    private record Ids(Map<String, Integer> items, Map<String, Integer> tomes) {}

    private WynnBuilderExport() {}

    /** The full link for {@code b}; see {@link #encode}. */
    public static String link(WynnBuilderLink.Build b, WynnBuilderTree.Tree tree) {
        String hash = encode(b, tree);
        return hash == null ? null : SITE + hash;
    }

    /**
     * The link's hash for {@code b} in its data version (the newest the mod knows when it names
     * none, an unknown one or a newer one): its pieces by slot (an item's id, else its name), each
     * piece's powders ("T6T6E6"), the tomes, the skill points (null: WynnBuilder works them out;
     * a null skill is left to it), the level (0 or the top: the version's top level) and, after
     * them, {@code tree}'s aspects and nodes (null: none). {@code b.rest} is not read.
     */
    public static String encode(WynnBuilderLink.Build b, WynnBuilderTree.Tree tree) {
        if (b == null) {
            return null;
        }
        WynnBuilderLink.Table t = WynnBuilderLink.table();
        int v = t.versions().size() - 1;
        for (int i = 0; i < t.versions().size() && b.version() != null && !b.newer(); i++) {
            if (t.versions().get(i).name().equals(b.version())) {
                v = i;
            }
        }
        WynnBuilderLink.Widths w = t.versions().get(v);
        StringBuilder bits = new StringBuilder();
        put(bits, 12, 6);
        put(bits, v, 10);
        for (int slot = 0; slot < w.slots(); slot++) {
            WynnBuilderLink.Piece p = null;
            for (WynnBuilderLink.Piece q : b.pieces()) {
                if (q.slot() == slot) {
                    p = q;
                }
            }
            int id = p == null ? -1 : itemId(p);
            put(bits, 0, 2);
            put(bits, id < 0 || id + 1 >= 1 << w.items() ? 0 : id + 1, w.items());
            if (slot < 4 || slot == WynnBuilderLink.WEAPON) {
                powders(bits, id < 0 ? "" : p.powders(), w);
            }
        }
        tomes(bits, b.tomes(), w);
        if (b.skills() == null) {
            put(bits, 1, 1);
        } else {
            put(bits, 0, 1);
            for (int i = 0; i < w.spTypes(); i++) {
                Integer sp = i < b.skills().size() ? b.skills().get(i) : null;
                put(bits, sp == null ? 0 : 1, 1);
                if (sp != null) {
                    put(bits, sp & (1 << w.spBits()) - 1, w.spBits());
                }
            }
        }
        if (b.level() <= 0 || b.level() >= w.maxLevel()) {
            put(bits, 0, 1);
        } else {
            put(bits, 1, 1);
            put(bits, b.level(), w.levelBits());
        }
        String rest = tree == null ? null : WynnBuilderTree.bits(w.name(), tree.klass(), tree.nodes(), tree.aspects());
        bits.append(rest == null ? "0" : rest);        // no aspects and an empty tree
        return letters(bits);
    }

    // ---- what you wear now ----

    /**
     * The link for what you wear now: the four armour pieces, the accessories and the weapon you
     * fight with ({@link WornGear#bestWeapon}) with their powders, the skill points the Character
     * Info menu showed you assigned (else left to WynnBuilder), your level, the tomes and aspects
     * Wynntils read, and the nodes you have unlocked. Null when you are not in a world.
     */
    public static String worn() {
        GameState state = GameState.current();
        if (!state.inGame()) {
            return null;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            stacks.add(state.armour(s));
        }
        List<ItemStack> acc = state.accessories();
        for (int i = 0; i < 4; i++) {
            stacks.add(i < acc.size() ? acc.get(i) : ItemStack.EMPTY);
        }
        stacks.add(WornGear.bestWeapon());
        List<WynnBuilderLink.Piece> pieces = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            WynnBuilderLink.Piece p = worn(i, stacks.get(i), state);
            if (p != null) {
                pieces.add(p);
            }
        }
        List<Integer> skills = null;
        if (state.skillsKnown()) {
            skills = new ArrayList<>();
            int[] own = new int[SkillKind.values().length];
            for (SkillKind s : SkillKind.values()) {
                own[s.ordinal()] = state.assignedSeen(s) + state.tomePoints(s);
            }
            List<Damage.Piece> gear = new ArrayList<>();
            for (int i = 0; i < stacks.size() - 1; i++) {
                gear.add(stacks.get(i).isEmpty() ? null : Damage.piece(stacks.get(i)));
            }
            ItemStack weapon = stacks.get(stacks.size() - 1);
            skills = totalSkills(own, gear, weapon.isEmpty() ? null : Damage.piece(weapon));
        }
        List<WynnBuilderTree.Aspect> aspects = new ArrayList<>();
        state.aspects().forEach((name, tier) -> aspects.add(new WynnBuilderTree.Aspect(name, tier)));
        WynnBuilderTree.Tree tree = new WynnBuilderTree.Tree(state.classType(), state.unlockedAbilities(), aspects, Map.of());
        return link(newest(pieces, state.tomes(), skills, state.level()), tree);
    }

    /** A worn stack as a link piece: gear by name with its powders, a crafted piece as one; null for none. */
    private static WynnBuilderLink.Piece worn(int slot, ItemStack stack, GameState state) {
        Optional<ItemView> v = stack == null || stack.isEmpty() ? Optional.empty() : state.view(stack);
        if (v.isEmpty()) {
            return null;
        }
        if (v.get().isCrafted()) {
            return new WynnBuilderLink.Piece(slot, WynnBuilderLink.Kind.CRAFTED, -1, v.get().crafted().name(), null, "");
        }
        if (!v.get().isGear()) {
            return null;
        }
        ItemData info = v.get().data();
        List<String> real = v.get().rolled() == null ? List.of() : v.get().rolled().powders();
        return new WynnBuilderLink.Piece(slot, WynnBuilderLink.Kind.ITEM, -1, info.name(), null,
                powders(real, Pieces.powderTier(info.level())));
    }

    // ---- a road map milestone ----

    /**
     * The link for a milestone's whole set ({@link Build#SLOTS} → piece) at {@code level}: each
     * piece with the powders the road map judged it with (yours, and the empty slots filled as
     * it fills them), {@code eval}'s skill points (null: left to WynnBuilder), your tomes and
     * the ability plan's nodes up to that level (your unlocked nodes when there is no plan).
     */
    public static String milestone(Map<String, Damage.Piece> set, Build.Eval eval, int level) {
        if (set == null) {
            return null;
        }
        GameState state = GameState.current();
        List<WynnBuilderLink.Piece> pieces = new ArrayList<>();
        for (int i = 0; i < Build.SLOTS.length; i++) {
            Damage.Piece p = set.get(Build.SLOTS[i]);
            if (p != null) {
                pieces.add(p.crafted() ? new WynnBuilderLink.Piece(i, WynnBuilderLink.Kind.CRAFTED, -1, p.name(), null, "")
                        : new WynnBuilderLink.Piece(i, WynnBuilderLink.Kind.ITEM, -1, p.name(), null, judgedPowders(p, state)));
            }
        }
        List<Integer> skills = null;
        if (eval != null && eval.allocation() != null) {
            int[] own = eval.withTome();
            for (SkillKind s : SkillKind.values()) {
                own[s.ordinal()] += state.tomePoints(s);
            }
            List<Damage.Piece> gear = new ArrayList<>();
            for (int i = 0; i < Build.SLOTS.length - 1; i++) {
                gear.add(set.get(Build.SLOTS[i]));
            }
            skills = totalSkills(own, gear, set.get("WEAPON"));
        }
        List<String> nodes = new ArrayList<>();
        state.abilityPlan().forEach((name, at) -> {
            if (at <= level) {
                nodes.add(name);
            }
        });
        if (state.abilityPlan().isEmpty()) {
            nodes.addAll(state.unlockedAbilities());
        }
        WynnBuilderTree.Tree tree = new WynnBuilderTree.Tree(state.classType(), nodes, List.of(), Map.of());
        return link(newest(pieces, state.tomes(), skills, level), tree);
    }

    /**
     * The link for a saved build: its pieces looked up by name (one the item data does not know is
     * left out), the skill points it was saved with (null: left to WynnBuilder), at its level
     * (yours when it has none), as {@link #milestone} writes a set. Null for no build.
     */
    public static String saved(Plans.Build b) {
        if (b == null) {
            return null;
        }
        GameState state = GameState.current();
        java.util.Set<String> want = new java.util.HashSet<>(b.items().values());
        Map<String, ItemData> infos = new HashMap<>();
        state.allGear().filter(i -> want.contains(i.name())).forEach(i -> infos.putIfAbsent(i.name(), i));
        Map<String, Damage.Piece> set = new HashMap<>();
        b.items().forEach((slot, name) -> {
            ItemData info = infos.get(name);
            if (info != null) {
                set.put(slot, Damage.piece(info));
            }
        });
        Build.Eval eval = null;
        if (b.points() != null && b.points().size() == SkillKind.values().length) {
            eval = new Build.Eval(b.points().stream().mapToInt(Integer::intValue).toArray(), null);
        }
        return milestone(set, eval, b.level() > 0 ? b.level() : state.level());
    }

    /** A piece's own powders and those the road map adds ({@link Pieces#addedPowders}, {@link Pieces#assumedCount}). */
    private static String judgedPowders(Damage.Piece p, GameState state) {
        if (p.stack() != null && !p.stack().isEmpty()) {
            Optional<ItemView> v = state.view(p.stack());
            if (v.isEmpty() || !v.get().isGear() || v.get().rolled() == null) {
                return "";
            }
            int tier = Pieces.powderTier(v.get().data().level());
            String own = powders(v.get().rolled().powders(), tier);
            Object[] add = Pieces.addedPowders(p);
            return add == null ? own : own + powders(add[0] == null ? "earth" : (String) add[0], (int) add[1], (int) add[2]);
        }
        ItemData info = state.allGear().filter(d -> d.name().equals(p.name())).findFirst().orElse(null);
        if (info == null) {
            return "";
        }
        Object[] hint = Pieces.powderHint(info);
        if (hint != null) {
            return powders((String) hint[0], (int) hint[1], (int) hint[2]);
        }
        int[] n = Pieces.assumedCount(p, info);
        return info.slot() != null && info.slot().isWeapon() ? "" : powders("earth", n[0], n[1]);
    }

    // ---- the pieces ----

    /**
     * The skill fields as WynnBuilder keeps them: totals, from which it takes every item's, set's
     * and tome's points off again to show what you assigned. {@code own}: assigned plus tomes;
     * the gear's raw skill points (sets included, the weapon too) go on top. With the assigned
     * points alone the site showed 142 of 200 assigned, short by the gear's bonuses.
     */
    private static List<Integer> totalSkills(int[] own, List<Damage.Piece> gear, Damage.Piece weapon) {
        List<Damage.Piece> worn = new ArrayList<>();
        for (Damage.Piece p : gear) {
            if (p != null) {
                worn.add(p);
            }
        }
        Map<String, Double> stats = Damage.totals(worn, weapon);
        int[] none = new int[own.length];
        List<Integer> out = new ArrayList<>();
        for (SkillKind s : SkillKind.values()) {
            out.add(own[s.ordinal()] + (int) Math.round(Damage.skillPoints(stats, none, s)));
        }
        return out;
    }

    private static WynnBuilderLink.Build newest(List<WynnBuilderLink.Piece> pieces, List<String> tomes, List<Integer> skills, int level) {
        return new WynnBuilderLink.Build(null, false, List.copyOf(pieces), tomes == null ? List.of() : List.copyOf(tomes),
                skills, level, "");
    }

    /** Powder elements as the game names them ("FIRE", "earth") at one tier: "F6F6". */
    private static String powders(List<String> elements, int tier) {
        StringBuilder out = new StringBuilder();
        for (String e : elements) {
            out.append(powders(e, 1, tier));
        }
        return out.toString();
    }

    private static String powders(String element, int n, int tier) {
        if (element == null || element.isEmpty() || n <= 0) {
            return "";
        }
        char c = element.toUpperCase(Locale.ROOT).charAt(0);
        return ELEMENTS.indexOf(c) < 0 ? "" : (String.valueOf(c) + Math.max(1, tier)).repeat(n);
    }

    private static int itemId(WynnBuilderLink.Piece p) {
        if (p.kind() != WynnBuilderLink.Kind.ITEM) {
            return -1;
        }
        if (p.id() >= 0) {
            return p.id();
        }
        Integer id = p.name() == null ? null : ids().items().get(p.name());
        return id == null ? -1 : id;
    }

    /**
     * One piece's powders, the inverse of the reader: the first id, then per powder "the same
     * again", "the same tier, another element" or "a new id", then "done". None: a 0 flag.
     */
    private static void powders(StringBuilder bits, String powders, WynnBuilderLink.Widths w) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; powders != null && i + 1 < powders.length(); i += 2) {
            int e = ELEMENTS.indexOf(powders.charAt(i));
            int tier = Math.min(powders.charAt(i + 1) - '0', w.powderTiers());
            if (e >= 0 && tier >= 1) {
                ids.add(e * w.powderTiers() + tier - 1);
            }
        }
        if (ids.isEmpty()) {
            put(bits, 0, 1);
            return;
        }
        put(bits, 1, 1);
        put(bits, ids.get(0), w.powderBits());
        for (int i = 1; i < ids.size(); i++) {
            int a = ids.get(i - 1);
            int b = ids.get(i);
            if (a == b) {
                put(bits, 0, 1);
            } else if (a % w.powderTiers() == b % w.powderTiers()) {
                put(bits, 0b01, 2);
                put(bits, Math.floorMod(b / w.powderTiers() - a / w.powderTiers(), ELEMENTS.length()) - 1, w.wrapBits());
            } else {
                put(bits, 0b011, 3);
                put(bits, b, w.powderBits());
            }
        }
        put(bits, 0b111, 3);
    }

    /** The tomes in their slots: none is a 0 flag; a name the table lacks, or a full kind, is left out. */
    private static void tomes(StringBuilder bits, List<String> tomes, WynnBuilderLink.Widths w) {
        int[] slots = new int[w.tomeSlots()];
        Arrays.fill(slots, -1);
        boolean any = false;
        for (String name : tomes) {
            Integer id = ids().tomes().get(name);
            int kind = tomeKind(name);
            int start = 0;
            for (int k = 0; k < kind; k++) {
                start += TOME_SLOTS[k];
            }
            for (int s = start; id != null && s < start + TOME_SLOTS[kind] && s < slots.length; s++) {
                if (slots[s] < 0) {
                    slots[s] = id;
                    any = true;
                    break;
                }
            }
        }
        put(bits, any ? 1 : 0, 1);
        if (any) {
            for (int s : slots) {
                put(bits, s < 0 ? 0 : 1, 1);
                if (s >= 0) {
                    put(bits, s, w.tomes());
                }
            }
        }
    }

    /** A tome's kind from its name, as an index into {@link #TOME_SLOTS}; the lootrun tomes have names of their own. */
    static int tomeKind(String name) {
        if (name.contains("Combat Mastery") || name.contains("Weapon Mastery")) {
            return 0;
        }
        if (name.contains("Defensive Mastery") || name.contains("Armour Mastery")) {
            return 1;
        }
        if (name.contains("Allegiance")) {
            return 2;
        }
        if (name.contains("Marathon")) {
            return 4;
        }
        if (name.contains("Mysticism")) {
            return 5;
        }
        return name.contains("Expertise") ? 6 : 3;
    }

    // ---- bits ----

    private static void put(StringBuilder bits, int value, int n) {
        for (int i = 0; i < n; i++) {
            bits.append((value >> i & 1) == 1 ? '1' : '0');
        }
    }

    /** '0'/'1' to letters, six bits each, lowest first, the last letter filled with 0. */
    private static String letters(StringBuilder bits) {
        StringBuilder out = new StringBuilder(bits.length() / 6 + 1);
        for (int i = 0; i < bits.length(); i += 6) {
            int v = 0;
            for (int j = 0; j < 6 && i + j < bits.length(); j++) {
                if (bits.charAt(i + j) == '1') {
                    v |= 1 << j;
                }
            }
            out.append(DIGITS.charAt(v));
        }
        return out.toString();
    }

    // ---- the ids ----

    private static Ids ids() {
        Ids t = ids;
        if (t == null) {
            t = load();
            ids = t;
        }
        return t;
    }

    /**
     * The table's names to ids. An id an older one moved to wins, and of two items with one name
     * the one whose WynnBuilder name is the same (no alias) wins.
     */
    private static Ids load() {
        WynnBuilderLink.Table t = WynnBuilderLink.table();
        Map<String, Integer> items = new HashMap<>();
        String[] names = t.items();
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                continue;
            }
            Integer had = items.get(names[i]);
            if (had == null || t.itemAlias().containsKey(had) && !t.itemAlias().containsKey(i)) {
                items.put(names[i], i);
            }
        }
        t.itemAlias().forEach((id, alias) -> items.putIfAbsent(alias, id));
        Map<String, Integer> tomes = new HashMap<>();
        String[] tn = t.tomes();
        for (int i = 0; i < tn.length; i++) {
            if (tn[i] != null && !t.tomeMoved().containsKey(i)) {
                tomes.putIfAbsent(tn[i], i);
            }
        }
        return new Ids(Map.copyOf(items), Map.copyOf(tomes));
    }
}
