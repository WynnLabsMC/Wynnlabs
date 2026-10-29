package io.github.anilken.wynnlabs.mounts;

import io.github.anilken.wynnlabs.model.MountRead;

import java.util.HashMap;
import java.util.Map;

/**
 * The mount panel's "+N / −N" for {@link #MS} ms after a stat's level changes. A second change
 * inside the window adds to the first and starts the window again. Another mount (its key) starts
 * a new baseline without a flash. Pure over the reads and a clock in ms.
 */
public final class RideFlash {

    public static final long MS = 2000;

    private String mount;
    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, Integer> delta = new HashMap<>();
    private final Map<String, Long> until = new HashMap<>();

    /** A read of the ridden mount at {@code now}: its changed levels flash. */
    public void update(MountRead m, long now) {
        if (m == null) {
            return;
        }
        String key = m.type() + "|" + m.potential() + "|" + m.primary() + "|" + m.secondary();
        if (!key.equals(mount)) {
            mount = key;
            levels.clear();
            delta.clear();
            until.clear();
        }
        for (MountRead.Stat s : m.stats()) {
            Integer before = levels.put(s.id(), s.level());
            if (before == null || before == s.level()) {
                continue;
            }
            int d = s.level() - before;
            Long end = until.get(s.id());
            if (end != null && now < end) {
                d += delta.getOrDefault(s.id(), 0);
            }
            if (d == 0) {
                delta.remove(s.id());
                until.remove(s.id());
            } else {
                delta.put(s.id(), d);
                until.put(s.id(), now + MS);
            }
        }
    }

    /** The stat's change still showing at {@code now}; 0 when none. */
    public int delta(String statId, long now) {
        Long end = until.get(statId);
        return end != null && now < end ? delta.getOrDefault(statId, 0) : 0;
    }

    /** Tests and previews: a change shown as if it just happened. */
    public void force(String statId, int d, long now) {
        delta.put(statId, d);
        until.put(statId, now + MS);
    }

    public void clear() {
        mount = null;
        levels.clear();
        delta.clear();
        until.clear();
    }
}
