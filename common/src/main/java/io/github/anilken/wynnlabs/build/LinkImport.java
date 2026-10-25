package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A WynnBuilder build link turned into the items to build around: each piece of the link found
 * in the game's item database by name, or why it is left out. The kept ones go to
 * {@link Locks#set} once the player confirms; powders, tomes and skill points are only shown.
 */
public final class LinkImport {

    /** Why a piece of the link is not built around; KEPT when it is. */
    public enum Why { KEPT, CRAFTED, CUSTOM, UNKNOWN, WRONG_SLOT, CLASS }

    /** One piece of the link: what it is, the item when the database has it, and whether it is kept. */
    public record Row(WynnBuilderLink.Piece piece, ItemData info, Why why) {}

    /** The link's rows in its order and the items kept (one per slot, two rings). */
    public record Result(WynnBuilderLink.Build build, List<Row> rows, List<ItemData> kept) {}

    private LinkImport() {}

    public static Result of(GameState state, WynnBuilderLink.Build build) {
        Map<String, ItemData> byName = new HashMap<>();
        state.allGear().forEach(i -> byName.putIfAbsent(i.name(), i));
        List<Row> rows = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (WynnBuilderLink.Piece p : build.pieces()) {
            Why why;
            ItemData info = null;
            if (p.kind() == WynnBuilderLink.Kind.CRAFTED) {
                why = Why.CRAFTED;
            } else if (p.kind() == WynnBuilderLink.Kind.CUSTOM) {
                why = Why.CUSTOM;
            } else {
                info = p.name() == null ? null : byName.get(p.name());
                if (info == null && p.alias() != null) {
                    info = byName.get(p.alias());
                }
                why = info == null ? Why.UNKNOWN : !fits(info, p.slot()) ? Why.WRONG_SLOT
                        : !Locks.usable(info, state.classType()) ? Why.CLASS : Why.KEPT;
            }
            if (why == Why.KEPT) {
                names.add(info.name());
            }
            rows.add(new Row(p, info, why));
        }
        return new Result(build, List.copyOf(rows), Locks.resolve(state, names));
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
        Slot k = Locks.kind(info);
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
