package io.github.anilken.wynnlabs.mounts;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kibble: special mount food (2.2.6) that works differently from the regular materials. Each kind
 * has a fixed eating time and its own effect, by tier: Sugarcrunch I/II give energy, Hearty I/II/III
 * raise every limit by 2/4/6, Golden Blend raises every maximum by 2, Nosweat Nuggets raise every
 * value by 3.
 */
public enum Kibble {
    SUGARCRUNCH("Sugarcrunch", 3 * 60),
    HEARTY("Hearty", 8 * 3600),
    GOLDEN_BLEND("Golden Blend", 6 * 3600),
    NOSWEAT("Nosweat", 6 * 3600);

    private static final Pattern TIER = Pattern.compile("(?<![A-Za-z])(III|II|I)(?![A-Za-z])");

    private final String word;
    private final int eatSec;

    Kibble(String word, int eatSec) {
        this.word = word;
        this.eatSec = eatSec;
    }

    /** Seconds the mount takes to eat it. */
    public int eatSec() {
        return eatSec;
    }

    /** A queued item's name as kibble ("Hearty Kibble II"), its tier (1 when none is named) as the level; null when it is not kibble. */
    public static Material material(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        String low = n.toLowerCase(Locale.ROOT);
        for (Kibble k : values()) {
            if (low.contains(k.word.toLowerCase(Locale.ROOT))) {
                Matcher m = TIER.matcher(n);
                int tier = m.find() ? m.group(1).length() : 1;
                return new Material(n, tier, null, k);
            }
        }
        return null;
    }

    /** These stats after eating one of this kibble of this tier (limits stay within maximums, values within limits). */
    MountStats apply(MountStats s, int tier) {
        int[] lv = new int[MountStat.COUNT], li = new int[MountStat.COUNT], mx = new int[MountStat.COUNT];
        for (int i = 0; i < MountStat.COUNT; i++) {
            lv[i] = s.level(i);
            li[i] = s.limit(i);
            mx[i] = s.max(i);
            switch (this) {
                case HEARTY -> li[i] = Math.min(mx[i], li[i] + 2 * Math.max(1, Math.min(3, tier)));
                case GOLDEN_BLEND -> mx[i] += 2;
                case NOSWEAT -> lv[i] = Math.min(li[i], lv[i] + 3);
                default -> { }
            }
        }
        return MountStats.of(lv, li, mx);
    }
}
