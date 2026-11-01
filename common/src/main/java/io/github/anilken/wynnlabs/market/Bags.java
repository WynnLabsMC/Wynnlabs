package io.github.anilken.wynnlabs.market;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Each character's inventory as it last was, so the bank tab and the Mounts list know what the
 * other characters carry. Only reads the player's own inventory. Kept with the bank pages in
 * {@link Vault} but saved apart in bags.json: a bag changes all the time, a bank page rarely.
 */
public final class Bags {

    private static final int CHECK_TICKS = 40;
    /** A bag changes with every pickup: written at most this often. */
    private static final long SAVE_MS = 30_000;
    /** The hotbar and the three rows above it; armour and the off hand come after. */
    private static final int STACKS = 36;
    /** As wide as a bank page, so a bag draws in the bank's grid. */
    private static final int COLS = 8;

    private static int tick;
    private static String lastRead = "";
    private static String kept = "";
    private static boolean dirty;
    private static long savedAt;

    private Bags() {}

    public static void onTick(Minecraft mc) {
        if (++tick % CHECK_TICKS != 0 || mc.player == null) {
            return;
        }
        if (dirty && System.currentTimeMillis() - savedAt >= SAVE_MS) {
            save();
        }
        // while a character is loading or the class menu is up the inventory is not its bag
        if (!Profile.settled()) {
            lastRead = "";
            return;
        }
        try {
            read(mc.player.getInventory());
        } catch (Throwable t) {
            System.out.println("[WynnLabs] could not read the inventory: " + t);
        }
    }

    private static void read(Inventory inv) {
        String key = Vault.bagKey(Profile.id());
        StringBuilder names = new StringBuilder(key);
        int shown = 0;
        for (int i = 0; i < STACKS; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                names.append(';').append(i).append(stack.getHoverName().getString()).append('x').append(stack.getCount());
                shown++;
            }
        }
        String seenNow = names.toString();
        boolean stable = seenNow.equals(lastRead);
        lastRead = seenNow;
        // two reads apart that agree, and never an empty bag: a world switch shows one for a moment
        if (!stable || shown == 0 || seenNow.equals(kept)) {
            return;
        }
        List<Vault.Stored> items = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < STACKS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Vault.Stored st = Vault.readBag(stack, key, now, slot(i));
            if (st != null) {
                items.add(st);
            }
        }
        kept = seenNow;
        Vault.putBag(key, items);
        dirty = true;
    }

    /** Where inventory index {@code i} draws: the rows from the top, the hotbar last, in lines of {@link #COLS}. */
    static int slot(int i) {
        int place = i < 9 ? 27 + i : i - 9;
        return place / COLS * 9 + place % COLS;
    }

    private static Path file() {
        return Dirs.config().resolve("bags.json");
    }

    static void load(Map<String, List<Vault.Stored>> pages) {
        try {
            if (!Files.exists(file())) {
                return;
            }
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            o.entrySet().forEach(e -> pages.put(e.getKey(), Vault.pageOf(e.getKey(), e.getValue().getAsJsonArray())));
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read bags.json: " + e);
        }
    }

    private static void save() {
        dirty = false;
        savedAt = System.currentTimeMillis();
        try {
            JsonObject o = new JsonObject();
            Vault.bags().forEach((k, v) -> o.add(k, Vault.savedPage(v)));
            Files.createDirectories(file().getParent());
            Files.writeString(file(), o.toString());
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not save bags.json: " + e);
        }
    }
}
