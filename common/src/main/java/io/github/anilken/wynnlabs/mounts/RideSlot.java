package io.github.anilken.wynnlabs.mounts;

import java.util.function.IntFunction;

/**
 * Which inventory slot holds the ridden mount ({@link #choose}): Wynntils' mount model says it when
 * it knows; otherwise the fallbacks. On Wynncraft a mount is summoned by using its item
 * from the hotbar, and the player then often holds something else; so the slot of the last used
 * item that read as a mount is kept (seen client-side, nothing sent). While riding, the held item
 * wins when it reads as a mount, else that slot's item; neither means no mount (the panel hides).
 * Pure.
 */
public final class RideSlot {

    private int slot = -1;

    /** The player used the item in hotbar slot {@code slot}; {@code mount}: it read as a mount. */
    public void used(int slot, boolean mount) {
        if (mount && slot >= 0 && slot < 9) {
            this.slot = slot;
        }
    }

    /** The remembered slot, -1 when none. */
    public int slot() {
        return slot;
    }

    /**
     * The hotbar slot the mount is read from: the selected one when its item reads as a mount, else
     * the remembered one when its item still does; -1 when neither.
     */
    public <T> int from(int selected, IntFunction<T> readSlot) {
        if (selected >= 0 && readSlot.apply(selected) != null) {
            return selected;
        }
        if (slot >= 0 && readSlot.apply(slot) != null) {
            return slot;
        }
        return -1;
    }

    /**
     * The slot to read the mount from: Wynntils' answer ({@code wynntils}: a slot, -1 for none, -2
     * when it cannot tell) when that slot reads as a mount; else the held mount or the last used
     * slot ({@link #from}); else the first slot of the hotbar, then the inventory, that reads as one.
     * -1 when the player carries no mount.
     */
    public <T> int choose(int wynntils, int selected, IntFunction<T> readSlot) {
        if (wynntils >= 0 && readSlot.apply(wynntils) != null) {
            return wynntils;
        }
        int own = from(selected, readSlot);
        if (own >= 0) {
            return own;
        }
        for (int i = 0; i < 36; i++) {
            if (readSlot.apply(i) != null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Where an inventory slot (0-8 the hotbar, 9-35 the rest) is in a menu's contents of
     * {@code size}: the player's 27 slots then the hotbar end every menu. -1 when it is not there.
     */
    public static int inMenu(int slot, int size) {
        if (slot < 0 || slot > 35 || size < 36) {
            return -1;
        }
        return slot < 9 ? size - 9 + slot : size - 36 + (slot - 9);
    }

    public void clear() {
        slot = -1;
    }
}
