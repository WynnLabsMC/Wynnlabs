package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Platform;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.mixin.ContainerHoverAccessor;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemNames;
import io.github.anilken.wynnlabs.search.UseList;
import io.github.anilken.wynnlabs.search.Uses;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * The search key (default: the key under Esc, which is {@code "} on a Turkish Q keyboard;
 * rebindable in Controls). In the world it opens the J menu on the search tab; over an item in
 * any container screen (bank, Trade Market, chests, the inventory) or Wynntils' guides it opens
 * the tab on that item, and closing the menu goes back to that screen. Never while typing.
 *
 * <p>The uses key (default U, rebindable) is its sibling: over any item the index knows it opens
 * the tab on that item with what it is used for under it (a material's crafts, an ingredient's
 * professions, mount feed), so how to get it and what to do with it are one look; over anything
 * else, and in the world, the press is not ours and goes on untouched.
 */
public final class SearchKey {

    private static KeyMapping key;
    private static KeyMapping usesKey;

    private SearchKey() {}

    /** Adds the key to Controls in the mod's category and opens the tab when it is pressed in the world. */
    public static void register(Platform platform, KeyMapping.Category category) {
        KeyMapping k = platform.registerKey(new KeyMapping("key.wynnlabs.search", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT, category));
        key = k;
        KeyMapping u = platform.registerKey(new KeyMapping("key.wynnlabs.uses", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, category));
        usesKey = u;
        platform.onEndTick(client -> {
            while (u.consumeClick()) {
                // in the world the uses key does nothing: there is no item under the mouse
            }
            while (k.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    swallowNextChar();                  // its ` or " must not land in the focused box
                    client.setScreen(RoadmapScreen.search(null, null));
                }
            }
        });
    }

    /** Whether this press is the search key. */
    static boolean matches(KeyEvent event) {
        return key != null && key.matches(event);
    }

    /** The key's own character (` or ") follows its press: the search box must not get it. */
    static void swallowNextChar() {
        swallowUntil = System.currentTimeMillis() + 150;
    }

    /** True once, for the character right after the key opened the tab. */
    static boolean swallow() {
        if (System.currentTimeMillis() < swallowUntil) {
            swallowUntil = 0;
            return true;
        }
        return false;
    }

    private static long swallowUntil;

    /**
     * A press while a screen is open: over an item, the search tab opens on it (the search key) or
     * on it and what it is used for (the uses key, for any item the index knows); true when the
     * press was used (the screen then never sees it).
     */
    public static boolean onScreenKey(Screen screen, KeyEvent event) {
        boolean search = matches(event);
        boolean uses = !search && usesKey != null && usesKey.matches(event);
        if (screen == null || !search && !uses || screen instanceof RoadmapScreen || screen instanceof ChatScreen
                || screen.getFocused() instanceof EditBox) {
            return false;
        }
        String hover = hoveredName(screen);
        if (hover == null || hover.isBlank()) {
            return false;
        }
        ItemIndex index = ItemIndex.get();
        Entry e = ItemNames.find(index, hover);
        String name = e != null ? e.name() : ItemNames.clean(hover);
        if (name.isEmpty()) {
            return false;
        }
        if (uses) {
            if (!UseList.opens(e != null && e.item(), e != null ? Uses.of(e) : Uses.of(name))) {
                return false;                       // not an item we know: the key is the screen's
            }
            swallowNextChar();
            Minecraft.getInstance().setScreen(RoadmapScreen.uses(name, screen));
            return true;
        }
        swallowNextChar();
        Minecraft.getInstance().setScreen(RoadmapScreen.search(name, screen));
        return true;
    }

    /** The name of the item under the mouse, or null. */
    private static String hoveredName(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> c) {
            Slot slot = ((ContainerHoverAccessor) c).wynnlabs$hoveredSlot();
            return slot == null || !slot.hasItem() ? null : slot.getItem().getHoverName().getString();
        }
        return Wyn.hoveredGuideItem(screen);
    }
}
