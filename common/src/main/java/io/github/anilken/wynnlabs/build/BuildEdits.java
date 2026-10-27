package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.L;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The build editor's changes to a saved build, each a new {@link Plans.Build} (the caller keeps it
 * with {@link Plans#update}): keep a slot's piece (it stays when the build is worked out again),
 * swap or take out a slot's piece (the score worked out again), rename, and a copy.
 */
public final class BuildEdits {

    private BuildEdits() {}

    /** Kept or let go: the piece in {@code slot} among the build's kept items (its "chosen items"). */
    public static Plans.Build toggleKeep(Plans.Build b, String slot) {
        String name = b.items().get(slot);
        if (name == null) {
            return b;
        }
        List<String> locks = new ArrayList<>(b.locks());
        if (!locks.remove(name)) {
            locks.add(name);
        }
        return with(b, locks, b.items(), b.score());
    }

    /** {@code name} in {@code slot}; kept when the piece it replaces was kept. The score as it stands with it. */
    public static Plans.Build swap(Plans.Build b, String slot, String name) {
        Map<String, String> items = new LinkedHashMap<>(b.items());
        String old = items.put(slot, name);
        List<String> locks = new ArrayList<>(b.locks());
        if (old != null && !old.equals(name) && locks.remove(old) && !locks.contains(name)) {
            locks.add(name);
        }
        return rescored(with(b, locks, items, b.score()));
    }

    /** {@code slot} left empty, its piece no longer kept. The score as it stands without it. */
    public static Plans.Build remove(Plans.Build b, String slot) {
        Map<String, String> items = new LinkedHashMap<>(b.items());
        String old = items.remove(slot);
        if (old == null) {
            return b;
        }
        List<String> locks = new ArrayList<>(b.locks());
        if (!items.containsValue(old)) {
            locks.remove(old);
        }
        return rescored(with(b, locks, items, b.score()));
    }

    public static Plans.Build renamed(Plans.Build b, String name) {
        return b.named(name);
    }

    /** A copy to save as a new build: "Name (copy)", a fresh id when saved. */
    public static Plans.Build copyOf(Plans.Build b) {
        return new Plans.Build("", L.t("wynnlabs.edit.copy_name", b.name()), b.locks(), b.tuning(), b.items(), b.score(), b.level(),
                b.created(), b.updated(), b.points());
    }

    /** The build's score worked out again (0 when it cannot be worn). */
    static Plans.Build rescored(Plans.Build b) {
        double s = Around.rescore(b);
        return with(b, b.locks(), b.items(), Double.isNaN(s) ? 0 : s);
    }

    private static Plans.Build with(Plans.Build b, List<String> locks, Map<String, String> items, double score) {
        return new Plans.Build(b.id(), b.name(), locks, b.tuning(), items, score, b.level(), b.created(), b.updated(), b.points());
    }
}
