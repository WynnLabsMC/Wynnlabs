package io.github.anilken.wynnlabs.mounts;

import io.github.anilken.wynnlabs.model.MountRead;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A mount read from its item's name and lore alone, for the mount panel when Wynntils does not
 * read the item as a mount (the "Wyvern Flute" that summons it): the stat lines ("Speed 12/21 (58)",
 * the max only inside an enclosure; "Speed1/10" on the flute), the energy pool ("Energy 196/204",
 * the Energy line before the other stats), potential, skin and its home enclosure ({@link #home}).
 * Null when it has fewer than four stat lines. Pure.
 */
public final class RideLore {

    static final Set<String> STATS = Set.of("Speed", "Acceleration", "Altitude", "Jump Height", "Energy", "Handling",
            "Toughness", "Boost", "Training");
    /** "Speed 12/21 (58)", or "Speed1/10 " on a summon item (glyphs stood around the numbers); read from the line's start. */
    private static final Pattern LINE = Pattern.compile(
            "^(Speed|Acceleration|Altitude|Jump Height|Energy|Handling|Toughness|Boost|Training)\\D*?(\\d+)\\s*/\\s*(\\d+)(?:\\s*\\((\\d+)\\))?");
    private static final String[] TYPES = {"Wyvern", "Horse", "Adasaur"};
    static final int MIN_STATS = 4;

    private RideLore() {}

    /** The mount's home enclosure: on a summon item the last line after "Right-Click to Summon" ("Bantisu Quarters"); null when none. */
    public static String home(List<String> lore) {
        if (lore == null) {
            return null;
        }
        boolean summon = false;
        String last = null;
        for (String raw : lore) {
            String t = FeederRead.text(raw);
            if (t.contains("to Summon")) {
                summon = true;
            } else if (summon && !t.isEmpty() && !t.contains("to Destroy") && !t.contains("cannot be")
                    && !Character.isLowerCase(t.charAt(0))) {
                // the free mount's "This mount cannot be traded, / bred, or upgraded" is no place
                last = t;
            }
        }
        return last;
    }

    /** How many lines read as stat lines (the pool's Energy line included). */
    static int statLines(List<String> lore) {
        int n = 0;
        for (String raw : lore) {
            Matcher m = LINE.matcher(FeederRead.text(raw));
            if (m.lookingAt() && STATS.contains(m.group(1))) {
                n++;
            }
        }
        return n;
    }

    public static MountRead parse(String name, List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        String cleanName = name == null ? "" : FeederRead.text(name);
        String type = "";
        for (String t : TYPES) {
            if (cleanName.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))
                    || FeederRead.text(lore.get(0)).toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))) {
                type = t.toUpperCase(Locale.ROOT);
                break;
            }
        }
        int energy = -1;
        int energyMax = -1;
        boolean estimated = false;
        List<MountRead.Stat> stats = new ArrayList<>();
        for (String raw : lore) {
            Matcher m = LINE.matcher(FeederRead.text(raw));
            if (!m.lookingAt() || !STATS.contains(m.group(1))) {
                continue;
            }
            String stat = m.group(1);
            int a = Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3));
            if (stat.equals("Energy") && stats.isEmpty() && energyMax < 0 && m.group(4) == null) {
                energy = a;                         // the pool: its Energy line comes before the stats
                energyMax = b;
                continue;
            }
            int max = m.group(4) == null ? b : Integer.parseInt(m.group(4));
            estimated |= m.group(4) == null;
            stats.add(new MountRead.Stat(stat.toUpperCase(Locale.ROOT).replace(' ', '_'), stat, a, b, max));
        }
        if (stats.size() < MIN_STATS) {
            return null;
        }
        int potential = FeederRead.potential(lore);
        return new MountRead(type, cleanName, Math.max(0, potential), Math.max(0, energy), Math.max(0, energyMax),
                List.copyOf(stats), estimated, "", "", FeederRead.skin(lore), null);
    }
}
