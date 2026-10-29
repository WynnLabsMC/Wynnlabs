package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the Mount Feeder menu is read, in one place, as plain values (so a test can feed it any
 * layout). As the game shows it (MountFeederContainer, read from a dump): an empty header row,
 * then five rows of nine: the mount slot (a "Drag a mount…" placeholder when empty), a spacer, then
 * the queue: five slots (an empty one holds a nameless placeholder) and two locked ones
 * ("Silverbull Membership is required…"). A queue slot holds one item: a food, or a second mount,
 * which means the two breed. The time left is on the mount's own state line ("Feeding in 2h 14m").
 * Rows are found from the menu's own slot positions, never fixed numbers. Anything that does not
 * parse gives no value rather than a wrong one.
 */
public final class FeederRead {

    /** One slot of the open menu: its index and position, its item's name and lore, what it is. */
    public record SlotInfo(int index, int x, int y, String name, List<String> lore, boolean mount, boolean empty) {}

    /** One feeder row: its number (1-5), its mount slot, (unused: -1), its queue's slots in order, how many are open. */
    public record Row(int number, int mountSlot, int partnerSlot, List<Integer> trough, int open) {

        public Row(int number, int mountSlot, List<Integer> trough, int open) {
            this(number, mountSlot, -1, trough, open);
        }
    }

    public static final int ROWS = 5;
    public static final int TROUGH = 7;
    /** Trough slots before Silverbull. */
    public static final int BASE_OPEN = 5;

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK = Pattern.compile("\\b(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\b");
    private static final Pattern WORDS = Pattern.compile("time|left|remain|ready|eat|digest|done|finish|feeding|kalan",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMAT = Pattern.compile("§.");
    /** The private-use glyphs Wynncraft draws its menus with (spacers, icons): never text. */
    private static final Pattern GLYPHS = Pattern.compile("\\p{Co}");

    private FeederRead() {}

    /** Whether a slot is one of the locked ones ("Silverbull Membership is required to use this slot!"). */
    public static boolean locked(SlotInfo s) {
        String all = (s.name() == null ? "" : s.name()) + " " + String.join(" ", s.lore() == null ? List.of() : s.lore());
        return all.toLowerCase(Locale.ROOT).contains("silverbull membership is");
    }

    /** Whether a slot holds nothing to eat: no item, or the nameless placeholder of an empty trough slot. */
    public static boolean free(SlotInfo s) {
        return s.empty() || s.name() == null || clean(s.name()).isEmpty();
    }

    /** Whether a slot is the empty mount slot's placeholder ("Drag a mount into this slot…"). */
    static boolean dragHere(SlotInfo s) {
        return s.name() != null && s.name().toLowerCase(Locale.ROOT).contains("drag a mount");
    }

    /**
     * The feeder's rows from the menu's slots: slots grouped by height; a row counts when it holds a
     * mount, the mount slot's placeholder or a locked slot (the empty header row does not), the first
     * five top to bottom. In each the mount slot is the mounts' column (where the rows with a mount
     * have it, else the placeholder's), the next slot the second mount's, then up to seven trough slots.
     */
    public static List<Row> rows(List<SlotInfo> slots) {
        TreeMap<Integer, List<SlotInfo>> byY = new TreeMap<>();
        for (SlotInfo s : slots) {
            byY.computeIfAbsent(s.y(), k -> new ArrayList<>()).add(s);
        }
        Map<Integer, Integer> mountX = new TreeMap<>();
        for (List<SlotInfo> line : byY.values()) {
            for (SlotInfo s : line) {
                if (s.mount() || dragHere(s)) {
                    mountX.merge(s.x(), 1, Integer::sum);
                }
            }
        }
        int column = mountX.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Integer.MIN_VALUE);
        List<Row> out = new ArrayList<>();
        for (List<SlotInfo> line : byY.values()) {
            if (line.size() < BASE_OPEN + 2 || out.size() == ROWS) {
                continue;
            }
            boolean feeder = line.stream().anyMatch(x -> x.mount() || dragHere(x) || locked(x));
            if (!feeder) {
                continue;                                   // the header row over the feeder
            }
            line.sort((a, b) -> Integer.compare(a.x(), b.x()));
            int at = 0;
            for (int i = 0; i < line.size(); i++) {
                if (line.get(i).x() == column || column == Integer.MIN_VALUE && line.get(i).mount()) {
                    at = i;
                    break;
                }
            }
            List<Integer> trough = new ArrayList<>();       // past the spacer right of the mount
            int open = 0;
            for (int i = at + 2; i < line.size() && trough.size() < TROUGH; i++) {
                trough.add(line.get(i).index());
                if (!locked(line.get(i))) {
                    open++;
                }
            }
            out.add(new Row(out.size() + 1, line.get(at).index(), -1, List.copyOf(trough), open));
        }
        return out;
    }

    /** The row's queued foods, in order, as materials' names (empty and locked slots and a breeding partner skipped). */
    public static List<String> pending(Row row, Map<Integer, SlotInfo> byIndex) {
        List<String> out = new ArrayList<>();
        for (int i : row.trough()) {
            SlotInfo s = byIndex.get(i);
            if (s != null && !free(s) && !locked(s) && !s.mount()) {
                Material m = material(s.name(), s.lore());
                out.add(m != null ? m.name() : clean(s.name()));
            }
        }
        return out;
    }

    /** The queue slot holding a second mount (the two breed), or -1. */
    public static int partner(Row row, Map<Integer, SlotInfo> byIndex) {
        for (int i : row.trough()) {
            SlotInfo s = byIndex.get(i);
            if (s != null && s.mount()) {
                return i;
            }
        }
        return -1;
    }

    private static final Pattern LEVEL = Pattern.compile("Level\\D*(\\d+)");          // "Woodcutting Level1": the gap may be a glyph

    /**
     * A queued food as a material: by its name, else its kind from the name's last word ("… Grains",
     * "… Plank") and its level from the lore's "<Profession> Level N"; null when neither works.
     */
    public static Material material(String name, List<String> lore) {
        String n = clean(name == null ? "" : name);
        Material byName = MountMaterials.byName(n);
        if (byName != null || n.isEmpty()) {
            return byName;
        }
        String last = n.substring(n.lastIndexOf(' ') + 1);
        MaterialKind kind = MaterialKind.parse(last.equalsIgnoreCase("Grains") ? "Grain"
                : last.equalsIgnoreCase("Plank") || last.equalsIgnoreCase("Planks") ? "Wood" : last);
        if (kind == null || lore == null) {
            return null;
        }
        for (String raw : lore) {
            Matcher m = LEVEL.matcher(text(raw));
            if (m.find()) {
                return MountMaterials.of(kind, Integer.parseInt(m.group(1)));
            }
        }
        return null;
    }

    private static final Pattern POTENTIAL = Pattern.compile("([\\d.]+)\\s*([kK]?)\\s*Potential");

    /** A mount's potential from its lore ("438 Potential", "1.2k Potential"), or -1. */
    public static int potential(List<String> lore) {
        if (lore == null) {
            return -1;
        }
        for (String raw : lore) {
            Matcher m = POTENTIAL.matcher(text(raw));
            if (m.find()) {
                try {
                    double v = Double.parseDouble(m.group(1));
                    return (int) Math.round(m.group(2).isEmpty() ? v : v * 1000);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    private static final Pattern PAGE = Pattern.compile("Page (\\d+)");

    /**
     * The mount bank's page from its page buttons' names (slots 51 "<", 52 ">"; "Page N"): the
     * previous button's N + 1, else the next button's N - 1; -1 when neither says.
     */
    public static int bankPage(String prev, String next) {
        Matcher p = prev == null ? null : PAGE.matcher(ascii(prev));
        if (p != null && p.find()) {
            return Integer.parseInt(p.group(1)) + 1;
        }
        Matcher n = next == null ? null : PAGE.matcher(ascii(next));
        if (n != null && n.find()) {
            return Integer.parseInt(n.group(1)) - 1;
        }
        return -1;
    }

    /** A name with only plain ASCII left (no colour codes or glyphs), for matching. */
    public static String ascii(String s) {
        return FORMAT.matcher(s).replaceAll("").replaceAll("[^\\x20-\\x7E]", "").trim();
    }

    /**
     * The seconds left from a mount's state line: "Feeding in 2h 14m" / "Breeding in 45m" give
     * their time; anything else ("Ready to Feed/Breed") gives -1.
     */
    public static long stateSeconds(String state) {
        if (state == null) {
            return -1;
        }
        Matcher m = Pattern.compile("(?i)(feeding|breeding) in (.*)").matcher(text(state));
        return m.find() ? duration(m.group(2)) : -1;
    }

    /** The first empty, open trough slot of a row (where the next food goes), or -1. */
    public static int firstFree(Row row, Map<Integer, SlotInfo> byIndex) {
        for (int i : row.trough()) {
            SlotInfo s = byIndex.get(i);
            if (s != null && free(s) && !locked(s)) {
                return i;
            }
        }
        return -1;
    }

    /** A lore line as text: no colour codes, no private-use glyphs, trimmed. */
    public static String text(String line) {
        return GLYPHS.matcher(FORMAT.matcher(line).replaceAll("")).replaceAll("").trim();
    }

    /** A mount's skin ("Azure-Horn": its colours, shared by many): the line right after its "240 Potential" line, or null. */
    public static String skin(List<String> lore) {
        if (lore == null) {
            return null;
        }
        for (int i = 0; i + 1 < lore.size(); i++) {
            if (text(lore.get(i)).matches("\\d+ Potential")) {
                String n = text(lore.get(i + 1));
                return n.isEmpty() || n.contains(" ") && !n.contains("-") ? null : n;
            }
        }
        return null;
    }

    /**
     * A mount's state line ("Ready to Feed/Breed"): the last text line before the click hints
     * ("Left-Click to Pickup"), past the stats; null when there is none.
     */
    public static String state(List<String> lore) {
        if (lore == null) {
            return null;
        }
        String last = null;
        boolean stats = false;
        for (String raw : lore) {
            String t = text(raw);
            if (t.startsWith("Level/Limit")) {
                stats = true;
                continue;
            }
            if (t.contains("Click to")) {
                break;
            }
            if (stats && !t.isEmpty() && !t.matches(".*\\d+/\\d+ \\(\\d+\\).*")) {
                last = t;
            }
        }
        return last;
    }

    /** A material's name without colour codes, stars or tier marks ("Copper Ingot ✫✫" → "Copper Ingot"). */
    public static String clean(String name) {
        String s = FORMAT.matcher(name).replaceAll("");
        s = s.replaceAll("[^\\p{L}' ]", " ").replaceAll("\\s+", " ").trim();
        return s;
    }

    /**
     * The time left on the item being eaten, in seconds, from its lore: a line that speaks of time
     * ("Time left: 2h 14m", "Ready in 14:05") with a duration in it. -1 when no line parses.
     */
    public static long remaining(List<String> lore) {
        if (lore == null) {
            return -1;
        }
        for (String raw : lore) {
            String line = FORMAT.matcher(raw).replaceAll("");
            if (!WORDS.matcher(line).find()) {
                continue;
            }
            long sec = duration(line);
            if (sec >= 0) {
                return sec;
            }
        }
        return -1;
    }

    /** A duration in a line ("2h 14m", "1 day 3 hours", "14:05", "1:02:03"), in seconds; -1 for none. */
    static long duration(String line) {
        Matcher c = CLOCK.matcher(line);
        if (c.find()) {
            long a = Long.parseLong(c.group(1));
            long b = Long.parseLong(c.group(2));
            return c.group(3) == null ? a * 60 + b : a * 3600 + b * 60 + Long.parseLong(c.group(3));
        }
        Matcher m = PART.matcher(line);
        long total = 0;
        boolean any = false;
        while (m.find()) {
            long v = Long.parseLong(m.group(1));
            char u = Character.toLowerCase(m.group(2).charAt(0));
            total += switch (u) {
                case 'd' -> v * 86400;
                case 'h' -> v * 3600;
                case 'm' -> v * 60;
                default -> v;
            };
            any = true;
        }
        return any ? total : -1;
    }

    /**
     * Each queued item's own timer: the feed timer by the limits before it (the first one's by the
     * stats now, then each after the ones before it).
     */
    public static long[] timers(MountStats stats, List<Material> queue) {
        long[] out = new long[queue.size()];
        MountStats s = stats;
        for (int i = 0; i < queue.size(); i++) {
            out[i] = s.feedTimerSec();
            s = s.fed(queue.get(i));
        }
        return out;
    }

    /**
     * When each queued item is done, from now: the first when its time left runs out, each next
     * one its own timer later (slot k frees at slot 1's time left plus the timers of slots 2..k).
     */
    public static long[] finishTimes(long remainingFirst, long[] timers) {
        long[] out = new long[timers.length];
        long t = 0;
        for (int i = 0; i < timers.length; i++) {
            t += i == 0 ? Math.max(0, remainingFirst) : timers[i];
            out[i] = t;
        }
        return out;
    }
}
