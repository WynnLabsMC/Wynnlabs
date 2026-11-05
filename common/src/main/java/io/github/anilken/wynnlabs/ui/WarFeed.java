package io.github.anilken.wynnlabs.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who held a territory before its holder took it, as far as this game has seen: each look at the
 * map (once a second at most) remembers every territory's guild, and a change records the old
 * holder's tag. Kept for this game only; the map shows the tag beside a recent capture.
 */
final class WarFeed {

    private WarFeed() {}

    /** Territory → its holder's name and tag as last seen. */
    private static final Map<String, String[]> SEEN = new HashMap<>();
    /** Territory → {new holder's name, old holder's tag} of its last seen capture. */
    private static final Map<String, String[]> FROM = new HashMap<>();
    private static long lookedAt;

    static void observe(List<GuildMapLook.Territory> all, long now) {
        if (now - lookedAt < 1000) {
            return;
        }
        lookedAt = now;
        for (GuildMapLook.Territory t : all) {
            if (t.guild() == null || t.guild().isBlank()) {
                continue;
            }
            String[] old = SEEN.put(t.name(), new String[] {t.guild(), t.prefix()});
            if (old != null && !old[0].equals(t.guild())) {
                FROM.put(t.name(), new String[] {t.guild(), old[1]});
            }
        }
    }

    /** The tag of the guild {@code holder} took the territory from, or null when not seen. */
    static String from(String territory, String holder) {
        String[] f = FROM.get(territory);
        return f != null && f[0].equals(holder) ? f[1] : null;
    }
}
