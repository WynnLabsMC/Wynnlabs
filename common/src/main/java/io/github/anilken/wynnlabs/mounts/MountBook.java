package io.github.anilken.wynnlabs.mounts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.model.MenuKind;
import io.github.anilken.wynnlabs.model.MountRead;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mounts this character has, as the places last showed them: one snapshot per place (the
 * inventory, each bank page, each enclosure's Mount Feeder), each replaced whole every time the
 * place is read. The mounts owned are all snapshots together, so a mount moved from the bag into
 * the feeder leaves the bag's snapshot on the next read of the bag; nothing tells mounts apart but
 * the place they are in. Mounts, enclosures and the mount bank are the account's, shared by every
 * character: one file, {@code config/wynnlabs/mounts.json}; only each character's bag is its own
 * place ("inventory:<id>"). Only reads what the client already shows; never opens, clicks or sends
 * anything.
 */
public final class MountBook {

    /** Where a mount was seen. {@code MENU} is only read back from older files and dropped. */
    public enum Where { INVENTORY, BANK, ENCLOSURE, MENU }

    /**
     * One mount in a place's snapshot: its read, where and when (epoch ms), what its Mount Feeder
     * row showed ({@code fed}, null outside a feeder), and its place's key ({@code page}: "bank:…",
     * "feeder:Ternaves"; null for the inventory).
     */
    public record Seen(MountRead mount, Where where, long at, Fed fed, String page) {
        public Seen(MountRead mount, Where where, long at) {
            this(mount, where, at, null, null);
        }

        public Seen(MountRead mount, Where where, long at, Fed fed) {
            this(mount, where, at, fed, null);
        }
    }

    /**
     * A mount's feeder row as last read: the row (1-5), its queued foods in order (the first is
     * being eaten), the time left then in seconds (-1: not read), when, and the open slots.
     */
    public record Fed(int row, List<String> pending, long remainingSec, long at, int open) {

        /** The time left now (-1 when unknown, 0 when it should be done). */
        public long remainingNow(long now) {
            return remainingSec < 0 ? -1 : Math.max(0, remainingSec - (now - at) / 1000);
        }
    }

    /**
     * The open feeder this moment, row by row (the overlay draws from it): the row, its mount's key
     * and a second mount's in its queue (the two breed; null for none), the queued foods, the time
     * left (-1: not read), the first free queue slot (-1: none), and whether there is something to
     * collect ({@code alert}).
     */
    public record Live(FeederRead.Row row, String mountKey, String partnerKey, List<String> pending, long remainingSec,
                       int freeSlot, boolean alert) {

        public Live(FeederRead.Row row, String mountKey, List<String> pending, long remainingSec, int freeSlot) {
            this(row, mountKey, null, pending, remainingSec, freeSlot, false);
        }
    }

    private static volatile List<Live> live = List.of();
    private static volatile List<Live> previewLive;

    /** The open Mount Feeder's rows, read twice a second (empty when none is open). */
    public static List<Live> live() {
        return previewLive != null ? previewLive : live;
    }

    /** Previews: these rows as if a feeder were open (null: the real one). */
    public static void previewLive(List<Live> rows) {
        previewLive = rows;
    }

    /** Whether this menu (Wynntils' container type, the title) is the Mount Feeder. */
    public static boolean feeder(String containerType, String title) {
        return containerType != null && containerType.contains("Feeder")
                || title != null && title.toLowerCase(Locale.ROOT).contains("mount feeder");
    }

    /** The places' keys: the inventory, a bank page by its title, an enclosure by its name. */
    static final String INVENTORY = "inventory";

    /** A character's bag: its own place, so another character's read never replaces it. */
    static String inventoryKey(String charId) {
        return INVENTORY + ":" + (charId == null ? "" : charId);
    }

    static String bankKey(String title) {
        return "bank:" + (title == null ? "" : FeederRead.text(title));
    }

    static String feederKey(String enclosure) {
        return "feeder:" + enclosure;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int EVERY = 10;              // ticks between two reads: twice a second
    private static final int PLAYER_SLOTS = 36;       // a menu ends with the player's inventory and hotbar

    /**
     * The maxes last seen in a feeder, by type, skin and potential: a stat's "(max)" shows only in
     * an enclosure, so a read elsewhere keeps these rather than a guess.
     */
    private static final Map<String, int[]> KNOWN_MAX = new HashMap<>();
    /** The mount bank's content slots (0-44) and its page buttons (51 "<", 52 ">"). */
    private static final int MOUNT_BANK_SLOTS = 45;
    private static final int PREV_PAGE = 51;
    private static final int NEXT_PAGE = 52;

    private static boolean loaded;
    private static Map<String, List<Seen>> places = new LinkedHashMap<>();
    private static List<Seen> preview;
    private static int tick;
    /** Bumps on every change; screens cache on it. */
    public static volatile int version;

    private MountBook() {}

    public static void onTick(Minecraft mc) {
        if (++tick % EVERY != 0 || mc.player == null || !Profile.settled()) {
            return;
        }
        load();
        long now = System.currentTimeMillis();
        var inv = mc.player.getInventory();
        List<Seen> bag = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            MountRead m = read(inv.getItem(i), false);
            if (m != null) {
                bag.add(new Seen(m, Where.INVENTORY, now, null, null));
            }
        }
        Map<String, List<Seen>> next = put(places, inventoryKey(Profile.id()), bag);
        var menu = mc.player.containerMenu;
        List<Live> rows = List.of();
        if (menu != null && menu != mc.player.inventoryMenu && mc.screen != null) {
            MenuKind c = Wyn.container();
            String title = mc.screen.getTitle().getString();
            Where where = source(c == null ? null : c.type(), title,
                    c != null && (c.characterBank() || c.accountBank() || c.storage()));
            int own = where == null ? 0 : Math.max(0, menu.slots.size() - PLAYER_SLOTS);   // the player's rows come last
            if (where == Where.BANK) {
                String key = bankKey(title);
                List<Seen> here = new ArrayList<>();
                for (int i = 0; i < own; i++) {
                    MountRead m = read(menu.slots.get(i).getItem(), false);
                    if (m != null) {
                        here.add(new Seen(m, Where.BANK, now, null, key));
                    }
                }
                next = put(next, key, here);
            } else if (where == null && own > NEXT_PAGE) {
                // the mount bank (a bank of its own, no Wynntils type): mounts in slots 0-44, its page
                // from the page buttons' names; read only when a mount is in it (never a click)
                String key = mountBankKey(menu);
                List<Seen> here = new ArrayList<>();
                for (int i = 0; key != null && i < MOUNT_BANK_SLOTS; i++) {
                    MountRead m = read(menu.slots.get(i).getItem(), false);
                    if (m != null) {
                        here.add(new Seen(m, Where.BANK, now, null, key));
                    }
                }
                if (!here.isEmpty()) {
                    next = put(next, key, here);
                }
            } else if (where == Where.ENCLOSURE) {
                var p = mc.player;
                String key = feederKey(Enclosures.nearest(p.getX(), p.getZ()).name());
                List<Seen> here = new ArrayList<>();
                rows = readFeeder(menu, own, now, key, here);
                next = put(next, key, here);
            }
        }
        live = rows;
        if (!same(next, places)) {
            places = next;
            version++;
            save();
        }
    }

    /** The open menu as a mount bank page ("mountbank:2"), from its page buttons; null when it has none. */
    private static String mountBankKey(net.minecraft.world.inventory.AbstractContainerMenu menu) {
        if (menu.slots.size() <= NEXT_PAGE) {
            return null;
        }
        ItemStack prev = menu.slots.get(PREV_PAGE).getItem();
        ItemStack next = menu.slots.get(NEXT_PAGE).getItem();
        int page = FeederRead.bankPage(prev.isEmpty() ? null : prev.getHoverName().getString(),
                next.isEmpty() ? null : next.getHoverName().getString());
        return page > 0 ? "mountbank:" + page : null;
    }

    /**
     * A read with the maxes known: read in a feeder (maxes shown) they are kept; read anywhere else
     * (maxes guessed) the kept ones are used when this mount (type, skin, potential) has them.
     * Pure but for the map it fills, for the tests.
     */
    static MountRead withKnownMax(MountRead m, boolean inFeeder, Map<String, int[]> known) {
        String key = m.type() + "|" + m.skin() + "|" + m.potential();
        if (inFeeder && !m.estimated()) {
            known.put(key, m.maxes());
            return m;
        }
        int[] max = m.estimated() ? known.get(key) : null;
        return max == null ? m : m.withMaxes(max);
    }

    /** A place read in full: its snapshot replaced by what it holds now. Pure, for the tests. */
    static Map<String, List<Seen>> put(Map<String, List<Seen>> before, String place, List<Seen> now) {
        Map<String, List<Seen>> out = new LinkedHashMap<>(before);
        if (now.isEmpty()) {
            out.remove(place);
        } else {
            out.put(place, List.copyOf(now));
        }
        return out;
    }

    /** The mounts of every place's latest snapshot. Pure, for the tests. */
    static List<Seen> union(Map<String, List<Seen>> all) {
        List<Seen> out = new ArrayList<>();
        all.values().forEach(out::addAll);
        return out;
    }

    /** Whether two books hold the same mounts in the same places (the read times aside, so a still bag is not written twice a second). */
    private static boolean same(Map<String, List<Seen>> a, Map<String, List<Seen>> b) {
        if (!a.keySet().equals(b.keySet())) {
            return false;
        }
        for (String k : a.keySet()) {
            List<Seen> x = a.get(k);
            List<Seen> y = b.get(k);
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                Seen s = x.get(i);
                Seen t = y.get(i);
                if (!s.mount().equals(t.mount()) || !java.util.Objects.equals(s.fed() == null ? null : s.fed().pending(),
                        t.fed() == null ? null : t.fed().pending()) || Math.abs(s.at() - t.at()) > 60_000) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A mount item outside a feeder (the held reins), with the maxes last seen in a feeder; null for anything else. */
    public static MountRead readItem(ItemStack stack) {
        return read(stack, false);
    }

    /**
     * Any item that shows a mount's stats: as {@link #readItem}, else from its name and lore
     * ({@link RideLore}: the hotbar item that summons it), with the maxes last seen in a feeder.
     */
    public static MountRead readAny(ItemStack stack) {
        MountRead m = readItem(stack);
        if (m != null || stack == null || stack.isEmpty()) {
            return m;
        }
        List<String> lore = lore(stack);
        if (forSale(lore)) {
            return null;
        }
        MountRead parsed = RideLore.parse(stack.getHoverName().getString(), lore);
        return parsed == null ? null : withKnownMax(parsed, false, KNOWN_MAX);
    }

    /** Dev diagnostics: why {@link #readAny} gives what it gives for this item. */
    public static String explain(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty slot";
        }
        if (readItem(stack) != null) {
            return "read by Wynntils as a mount";
        }
        List<String> lore = lore(stack);
        if (forSale(lore)) {
            return "a shop's offer";
        }
        return RideLore.parse(stack.getHoverName().getString(), lore) != null ? "read from its lore"
                : "not a mount: " + RideLore.statLines(lore) + " stat lines (needs " + RideLore.MIN_STATS + ")";
    }

    /** Dev diagnostics: an item's lore lines as text. */
    public static List<String> loreText(ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (String l : lore(stack)) {
            out.add(FeederRead.text(l));
        }
        return out;
    }

    /** A mount's reins with its skin and state from the lore; null for anything else, a shop's offer included. */
    private static MountRead read(ItemStack stack, boolean inFeeder) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        List<String> lore = lore(stack);
        if (forSale(lore)) {
            return null;
        }
        MountRead m;
        try {
            m = WynntilsItems.mountRead(stack);
        } catch (Throwable t) {
            return null;
        }
        if (m == null) {
            return null;
        }
        m = m.with(FeederRead.skin(lore), FeederRead.state(lore));
        int pot = FeederRead.potential(lore);
        if (pot > 0) {
            m = m.withPotential(pot);                      // the lore's own number: right even when the maxes are guessed
        }
        return withKnownMax(m, inFeeder, KNOWN_MAX);
    }

    /**
     * Which open menus hold the player's own mounts: a bank page, or the Mount Feeder / enclosure
     * (by Wynntils' container or the menu's title). Null for every other menu, shops included.
     */
    static Where source(String containerType, String title, boolean bank) {
        if (bank) {
            return Where.BANK;
        }
        if (containerType != null && containerType.startsWith("Mount")) {
            return Where.ENCLOSURE;
        }
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return t.contains("mount feeder") || t.contains("enclosure") ? Where.ENCLOSURE : null;
    }

    /**
     * The open Mount Feeder: its rows from the menu's slots; each row's mount goes to {@code out}
     * with its queue and time left ({@link Fed}); a second mount in a row's queue breeds with it.
     * Returns the rows for the overlay.
     */
    private static List<Live> readFeeder(net.minecraft.world.inventory.AbstractContainerMenu menu, int own, long now, String key,
                                         List<Seen> out) {
        List<FeederRead.SlotInfo> infos = new ArrayList<>();
        Map<Integer, MountRead> mounts = new HashMap<>();
        Map<Integer, Boolean> alerts = new HashMap<>();
        for (int i = 0; i < own; i++) {
            var slot = menu.slots.get(i);
            ItemStack st = slot.getItem();
            MountRead m = read(st, true);
            if (m != null) {
                mounts.put(i, m);
                alerts.put(i, alert(st));
            }
            infos.add(new FeederRead.SlotInfo(i, slot.x, slot.y, st.isEmpty() ? "" : st.getHoverName().getString(),
                    st.isEmpty() ? List.of() : lore(st), m != null, st.isEmpty()));
        }
        Map<Integer, FeederRead.SlotInfo> byIndex = new HashMap<>();
        infos.forEach(x -> byIndex.put(x.index(), x));
        List<Live> rows = new ArrayList<>();
        for (FeederRead.Row r : FeederRead.rows(infos)) {
            MountRead m = mounts.get(r.mountSlot());
            int partnerAt = FeederRead.partner(r, byIndex);
            MountRead partner = partnerAt < 0 ? null : mounts.get(partnerAt);
            List<String> pending = FeederRead.pending(r, byIndex);
            long left = m == null ? -1 : FeederRead.stateSeconds(m.state());
            rows.add(new Live(r, m == null ? null : m.key(), partner == null ? null : partner.key(), pending, left,
                    FeederRead.firstFree(r, byIndex), Boolean.TRUE.equals(alerts.get(r.mountSlot()))));
            if (m != null) {
                out.add(new Seen(m, Where.ENCLOSURE, now, new Fed(r.number(), pending, left, now, r.open()), key));
            }
            if (partner != null) {
                out.add(new Seen(partner, Where.ENCLOSURE, now, null, key));
            }
        }
        return List.copyOf(rows);
    }

    /** Whether a mount's item asks to be collected: its custom model data strings say "alert". */
    private static boolean alert(ItemStack st) {
        var cmd = st.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd == null) {
            return false;
        }
        for (String s : cmd.strings()) {
            if (s != null && s.toLowerCase(Locale.ROOT).contains("alert")) {
                return true;
            }
        }
        return false;
    }

    /** Whether an item's lore offers it for sale (a price, "Left-Click to Buy"): a shop's, not the player's. */
    static boolean forSale(List<String> lore) {
        for (String line : lore) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("click to buy") || l.trim().startsWith("price")) {
                return true;
            }
        }
        return false;
    }

    private static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        var lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (var line : lore.lines()) {
                out.add(line.getString());
            }
        }
        return out;
    }

    /** Every mount in the places' latest snapshots, newest read first. */
    public static List<Seen> all() {
        if (preview != null) {
            return preview;
        }
        load();
        List<Seen> out = union(places);
        out.sort(Comparator.comparingLong(Seen::at).reversed());
        return out;
    }

    /** Previews and tests: these mounts instead of the file (null: the file again). */
    public static void preview(List<Seen> mounts) {
        preview = mounts;
        version++;
    }

    private static Path file() {
        return Dirs.config().resolve("mounts.json");
    }

    /**
     * Several books as one: every place's newest snapshot (by its newest read). Used once to bring
     * the old per-character files into the account's. Pure, for the tests.
     */
    static Map<String, List<Seen>> mergeAll(List<Map<String, List<Seen>>> books) {
        Map<String, List<Seen>> out = new LinkedHashMap<>();
        for (Map<String, List<Seen>> b : books) {
            for (Map.Entry<String, List<Seen>> e : b.entrySet()) {
                List<Seen> old = out.get(e.getKey());
                if (old == null || newest(e.getValue()) > newest(old)) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        return out;
    }

    private static long newest(List<Seen> list) {
        return list.stream().mapToLong(Seen::at).max().orElse(0);
    }

    /**
     * The file: {"places": {key: [mounts]}}. A file from before (one list) is dropped: the places
     * fill again as the bag, the bank pages and the feeder are seen.
     */
    static Map<String, List<Seen>> parse(String text) {
        Map<String, List<Seen>> out = new LinkedHashMap<>();
        var root = JsonParser.parseString(text);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("places")) {
            return out;
        }
        JsonObject p = root.getAsJsonObject().getAsJsonObject("places");
        for (var e : p.entrySet()) {
            List<Seen> list = GSON.fromJson(e.getValue(), new TypeToken<List<Seen>>() {}.getType());
            if (list != null) {
                list.removeIf(s -> s == null || s.mount() == null || s.mount().stats() == null || s.where() == null
                        || s.where() == Where.MENU);
                if (!list.isEmpty()) {
                    out.put(e.getKey(), List.copyOf(list));
                }
            }
        }
        return out;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        places = new LinkedHashMap<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                places = parse(Files.readString(f));
            } else {
                // once: the per-character files from before, brought together (left in place, not read again)
                List<Map<String, List<Seen>>> old = new ArrayList<>();
                Path chars = Dirs.config().resolve("characters");
                if (Files.isDirectory(chars)) {
                    try (var dirs = Files.list(chars)) {
                        for (Path d : (Iterable<Path>) dirs::iterator) {
                            Path m = d.resolve("mounts.json");
                            if (Files.exists(m)) {
                                Map<String, List<Seen>> one = new LinkedHashMap<>();
                                for (var e : parse(Files.readString(m)).entrySet()) {
                                    // that character's bag keeps its own place
                                    one.put(e.getKey().equals(INVENTORY) ? inventoryKey(d.getFileName().toString()) : e.getKey(), e.getValue());
                                }
                                old.add(one);
                            }
                        }
                    }
                }
                places = mergeAll(old);
                save();
            }
            for (List<Seen> list : places.values()) {
                for (Seen s : list) {
                    if (s.where() == Where.ENCLOSURE) {
                        withKnownMax(s.mount(), true, KNOWN_MAX);
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not read mounts.json: " + t);
        }
        version++;
    }

    private static void save() {
        Map<String, List<Seen>> copy = new LinkedHashMap<>(places);
        Saver.mark(file(), () -> {
            JsonObject root = new JsonObject();
            root.add("places", GSON.toJsonTree(copy));
            return GSON.toJson(root);
        });
    }
}
