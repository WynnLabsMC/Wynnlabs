package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * A WynnBuilder build link turned into the items to build around: each piece of the link found
 * in the game's item database by name, a crafted one made from the link's recipe, a custom one
 * from the numbers the link holds, or why it is left out. The kept items go to {@link Locks#set}
 * once the player confirms (a crafted or custom piece has no name to build around, so it is only
 * scored, {@link #set}); powders, tomes and skill points are only shown. The link's ability tree
 * and aspects are read with its weapon's class (yours when the weapon is not in the database).
 */
public final class LinkImport {

    /** Why a piece of the link is not built around; KEPT when it is (a crafted or custom one: made from the link). */
    public enum Why { KEPT, CRAFTED, CUSTOM, UNKNOWN, WRONG_SLOT, CLASS }

    /**
     * One piece of the link: what it is, the item when the database has it, the piece made from
     * the link when it is crafted (its recipe) or custom (its numbers), else null, and whether it
     * is kept.
     */
    public record Row(WynnBuilderLink.Piece piece, ItemData info, Damage.Piece made, Why why) {}

    /**
     * The link's rows in its order, the items kept (one per slot, two rings) and the link's
     * ability tree and aspects (null when the link has none or its class is not known).
     */
    public record Result(WynnBuilderLink.Build build, List<Row> rows, List<ItemData> kept, WynnBuilderTree.Tree tree) {}

    private LinkImport() {}

    public static Result of(GameState state, WynnBuilderLink.Build build) {
        Map<String, Ingredient> ings = new HashMap<>();
        Wyn.craftIngredients().forEach(i -> ings.put(i.name(), i));
        return of(state, build, ings::get);
    }

    /** {@link #of(GameState, WynnBuilderLink.Build)} with the ingredients by name (null for one not known). */
    public static Result of(GameState state, WynnBuilderLink.Build build, Function<String, Ingredient> ingredients) {
        Map<String, ItemData> byName = new HashMap<>();
        state.allGear().forEach(i -> byName.putIfAbsent(i.name(), i));
        List<Row> rows = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (WynnBuilderLink.Piece p : build.pieces()) {
            Why why;
            ItemData info = null;
            Damage.Piece made = null;
            if (p.kind() != WynnBuilderLink.Kind.ITEM) {
                made = p.kind() == WynnBuilderLink.Kind.CRAFTED ? crafted(p, ingredients) : custom(p);
                Klass need = made == null ? null : made.type().klass();
                why = made == null ? (p.kind() == WynnBuilderLink.Kind.CRAFTED ? Why.CRAFTED : Why.CUSTOM)
                        : !fits(made.type(), p.slot()) ? Why.WRONG_SLOT
                        : need != null && need != state.classType() ? Why.CLASS : Why.KEPT;
            } else {
                info = p.name() == null ? null : byName.get(p.name());
                if (info == null && p.alias() != null) {
                    info = byName.get(p.alias());
                }
                why = info == null ? Why.UNKNOWN : !fits(info, p.slot()) ? Why.WRONG_SLOT
                        : !Locks.usable(info, state.classType()) ? Why.CLASS : Why.KEPT;
            }
            if (why == Why.KEPT && info != null) {
                names.add(info.name());
            }
            rows.add(new Row(p, info, made, why));
        }
        return new Result(build, List.copyOf(rows), Locks.resolve(state, names), WynnBuilderTree.of(build, klass(rows, state)));
    }

    /**
     * A crafted piece of the link made from its recipe, every range at its middle, named the way
     * WynnBuilder names it ("Crafted Spear"); null when the code or an ingredient is not known.
     */
    static Damage.Piece crafted(WynnBuilderLink.Piece p, Function<String, Ingredient> ingredients) {
        RecipeCode.Recipe r = p.recipe();
        CraftData.Recipe recipe = r == null ? null : CraftData.find(r.type(), r.minLevel());
        if (recipe == null) {
            return null;
        }
        List<Ingredient> slots = new ArrayList<>();
        for (String name : r.slots()) {
            Ingredient ing = name == null ? Ingredient.NONE : ingredients.apply(name);
            if (ing == null) {
                return null;
            }
            slots.add(ing);
        }
        Craft.Result made = Craft.craft(recipe, r.tier1(), r.tier2(), r.speed(), slots);
        String type = r.type().toUpperCase(Locale.ROOT);
        String name = "Crafted " + type.charAt(0) + r.type().substring(1);
        return Damage.crafted(made, r.speed(), name, Slot.of(type), "craft:" + p.code() + "|" + p.powders(), p.powders());
    }

    /** A custom piece of the link from the numbers it holds; null when its code is in the site's older form. */
    static Damage.Piece custom(WynnBuilderLink.Piece p) {
        WynnBuilderCustom.Item it = p.custom();
        return it == null ? null : WynnBuilderCustom.piece(it, "custom:" + p.code());
    }

    /**
     * The link's pieces as the model scores them, by build slot ({@link Build#SLOTS}): each kept
     * item at the middle of its rolls with the link's powders, each crafted one made from its
     * recipe and each custom one from its numbers. What {@link Damage#breakdown} takes.
     */
    public static Map<String, Damage.Piece> set(Result r) {
        Map<String, Damage.Piece> out = new java.util.LinkedHashMap<>();
        for (Row row : r.rows()) {
            if (row.why() != Why.KEPT) {
                continue;
            }
            int slot = row.piece().slot();
            String key = slot >= Build.SLOTS.length ? "WEAPON" : Build.SLOTS[slot];
            out.put(key, row.made() != null ? row.made() : Damage.piece(row.info(), row.piece().powders()));
        }
        return out;
    }

    /**
     * The link's own tree as the model's overlay at the link's level, to score its items with the
     * nodes it was made for ({@link TreeModel#with}); null when the link has no tree, is for
     * another class than yours, or the model cannot read your class's tree.
     */
    public static TreeModel.Overlay overlay(Result r) {
        if (r == null || r.tree() == null || r.tree().klass() != GameState.current().classType()) {
            return null;
        }
        TreeModel.Run base = TreeModel.run("");
        if (base == null) {
            return null;
        }
        int level = r.build().level();
        return new TreeModel.Run(base.reads, base.spellNames, r.tree().nodes(), List.of(), level, "", 1, base.passiveCap,
                base.bonuses, base.text).at(level);
    }

    /** The class the link is for: its weapon's, else yours. */
    static Klass klass(List<Row> rows, GameState state) {
        for (Row r : rows) {
            if (r.piece().slot() == WynnBuilderLink.WEAPON && r.made() != null && r.made().type().klass() != null) {
                return r.made().type().klass();
            }
            if (r.piece().slot() == WynnBuilderLink.WEAPON && r.info() != null && r.info().slot().isWeapon()) {
                Klass k = r.info().requirements().klass().orElse(r.info().slot().klass());
                if (k != null && k != Klass.NONE) {
                    return k;
                }
            }
        }
        return state.classType();
    }

    /**
     * The chosen items as a plan to look at, even when you cannot wear them yet: each in its build
     * slot (rings in both hands), the slots they leave open with what you wear, made for
     * {@code level} with {@code points} (a link's, or null for the mod's spread). Null when there
     * is no weapon, the one piece nothing can stand in for.
     */
    public static Plans.Build asPlan(List<ItemData> items, int level, List<Integer> points, long now) {
        Map<String, String> slots = new java.util.LinkedHashMap<>();
        int rings = 0;
        for (ItemData i : items) {
            Slot k = Locks.kind(i);
            String slot = k == null ? null : k.isWeapon() ? "WEAPON" : Build.slotOf(k, k == Slot.RING ? rings++ : 0);
            if (slot != null) {
                slots.putIfAbsent(slot, i.name());
            }
        }
        BuildSources.worn().forEach(slots::putIfAbsent);
        if (!slots.containsKey("WEAPON") || items.isEmpty()) {
            return null;
        }
        Map<String, String> ordered = new java.util.LinkedHashMap<>();
        for (String s : Plans.SLOTS) {
            if (slots.containsKey(s)) {
                ordered.put(s, slots.get(s));
            }
        }
        List<String> locks = items.stream().map(ItemData::name).toList();
        List<Integer> pts = points == null ? null : points.stream().map(v -> v == null ? 0 : v).toList();
        return new Plans.Build("p" + now, "", locks, Tuning.Choice.PLAIN, ordered, 0, level, now, now, pts);
    }

    /** Whether the item is the kind the link's place holds (a check that the ids were read right). */
    static boolean fits(ItemData info, int slot) {
        return fits(Locks.kind(info), slot);
    }

    private static boolean fits(Slot k, int slot) {
        if (k != null && k.isWeapon()) {
            k = Slot.WEAPON;
        }
        return switch (slot) {
            case 0 -> k == Slot.HELMET;
            case 1 -> k == Slot.CHESTPLATE;
            case 2 -> k == Slot.LEGGINGS;
            case 3 -> k == Slot.BOOTS;
            case 4, 5 -> k == Slot.RING;
            case 6 -> k == Slot.BRACELET;
            case 7 -> k == Slot.NECKLACE;
            default -> k == Slot.WEAPON;
        };
    }
}
