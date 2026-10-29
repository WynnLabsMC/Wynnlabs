package io.github.anilken.wynnlabs.mounts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The mounts this character has shown: every mount's reins seen in the inventory, a bank page or
 * the enclosure are remembered (newest stats, where and when) in
 * {@code config/wynnlabs/characters/<id>/mounts.json}. Only reads what the client already shows;
 * never opens, clicks or sends anything.
 */
public final class MountBook {

    /**
     * Where a mount was last seen: the places it is yours. {@code MENU} is only read back from
     * older files (any open menu, a shop among them) and dropped on loading.
     */
    public enum Where { INVENTORY, BANK, ENCLOSURE, MENU }

    /** One mount: its last read, where and when (epoch ms). */
    public record Seen(MountRead mount, Where where, long at) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int EVERY = 10;              // ticks between two reads: twice a second
    private static final int PLAYER_SLOTS = 36;       // a menu ends with the player's inventory and hotbar

    private static String loadedFor;
    private static Map<String, Seen> seen = new LinkedHashMap<>();
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
        boolean changed = false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            changed |= note(inv.getItem(i), Where.INVENTORY, now);
        }
        var menu = mc.player.containerMenu;
        if (menu != null && menu != mc.player.inventoryMenu && mc.screen != null) {
            MenuKind c = Wyn.container();
            Where where = source(c == null ? null : c.type(), mc.screen.getTitle().getString(),
                    c != null && (c.characterBank() || c.accountBank() || c.storage()));
            int own = where == null ? 0 : menu.slots.size() - PLAYER_SLOTS;   // the player's rows come last
            for (int i = 0; i < Math.max(0, own); i++) {
                changed |= note(menu.slots.get(i).getItem(), where, now);
            }
        }
        if (changed) {
            version++;
            save();
        }
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

    /** What the player owns from a file's entries: those in the inventory, a bank or the enclosure. */
    static List<Seen> owned(List<Seen> list) {
        List<Seen> out = new ArrayList<>();
        for (Seen s : list) {
            if (s != null && s.mount() != null && s.mount().stats() != null && s.where() != null && s.where() != Where.MENU) {
                out.add(s);
            }
        }
        return out;
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

    /** One stack: a mount's newer read replaces the old one; returns whether anything changed. */
    private static boolean note(ItemStack stack, Where where, long now) {
        if (stack == null || stack.isEmpty() || forSale(lore(stack))) {
            return false;
        }
        MountRead m;
        try {
            m = WynntilsItems.mountRead(stack);
        } catch (Throwable t) {
            return false;
        }
        if (m == null) {
            return false;
        }
        Seen old = seen.get(m.key());
        // the same read in the same place: only the time moves, written at most once a minute
        if (old != null && old.mount().equals(m) && old.where() == where && now - old.at() < 60_000) {
            return false;
        }
        seen.put(m.key(), new Seen(m, where, now));
        return true;
    }

    /** Every mount seen on this character, newest first. */
    public static List<Seen> all() {
        if (preview != null) {
            return preview;
        }
        load();
        List<Seen> out = new ArrayList<>(seen.values());
        out.sort(Comparator.comparingLong(Seen::at).reversed());
        return out;
    }

    /** Previews and tests: these mounts instead of the file (null: the file again). */
    public static void preview(List<Seen> mounts) {
        preview = mounts;
        version++;
    }

    private static Path file() {
        return Dirs.config().resolve("characters").resolve(Profile.id()).resolve("mounts.json");
    }

    private static void load() {
        String id = Profile.id();
        if (id == null || id.isEmpty() || id.equals(loadedFor)) {
            return;
        }
        loadedFor = id;
        seen = new LinkedHashMap<>();
        try {
            Path f = file();
            if (Files.exists(f)) {
                List<Seen> list = GSON.fromJson(Files.readString(f), new TypeToken<List<Seen>>() {}.getType());
                if (list != null) {
                    List<Seen> keep = owned(list);
                    for (Seen s : keep) {
                        seen.put(s.mount().key(), s);
                    }
                    if (keep.size() < list.size()) {
                        save();                                 // mounts seen in a shop or another menu were never yours
                    }
                }
            }
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not read mounts.json: " + t);
        }
        version++;
    }

    private static void save() {
        List<Seen> copy = List.copyOf(seen.values());
        Saver.mark(file(), () -> GSON.toJson(copy));
    }
}
