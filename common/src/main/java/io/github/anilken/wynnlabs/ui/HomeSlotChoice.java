package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.MapChoices;
import io.github.anilken.wynnlabs.core.L;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The two small buttons inside a Gear slot's picture: the lock keeps the piece on the road map,
 * the ✕ bans it. They show while the mouse is on the picture; a kept piece's lock always shows.
 * A slot registers them before it draws, so their tooltips come before the item's, and they draw
 * after every slot.
 */
final class HomeSlotChoice {

    private static final int SIZE = 9;
    private static final int INSET = 1;

    /** A slot's buttons this frame: {x, y, size, mouse x, mouse y} and the piece. */
    private record Pending(int x, int y, int s, int mx, int my, boolean hover, boolean kept) {}

    private final List<Pending> pending = new ArrayList<>();

    /** The buttons of the slot at {@code x, y}; {@code name} empty for a slot with nothing shown. */
    void areas(HomeTab.Host host, String name, int x, int y, int s, int mx, int my) {
        if (name.isEmpty()) {
            return;
        }
        boolean kept = MapChoices.current().kept().contains(name);
        boolean hover = mx >= x && mx < x + s && my >= y && my < y + s;
        if (hover) {
            int[] lock = lock(x, y);
            int[] ban = ban(x, y, s);
            host.corner(lock, "#qkeep:" + name);
            host.corner(ban, "#qban:" + name);
            host.area(lock[0], lock[1], lock[2], lock[3], null,
                    List.of(L.c(kept ? "wynnlabs.choices.unkeep" : "wynnlabs.choices.keep").withStyle(ChatFormatting.WHITE)), "#qkeep:" + name);
            host.area(ban[0], ban[1], ban[2], ban[3], null,
                    List.of(L.c("wynnlabs.choices.ban").withStyle(ChatFormatting.WHITE)), "#qban:" + name);
            // no item tooltip on the picture, it would cover the buttons: the name beside it has it
            host.area(x, y, s, s, null, null, null);
        }
        if (hover || kept) {
            pending.add(new Pending(x, y, s, mx, my, hover, kept));
        }
    }

    /** Draws this frame's buttons over the slots. */
    void draw(GuiGraphics g) {
        for (Pending p : pending) {
            int[] lock = lock(p.x(), p.y());
            boolean onLock = p.hover() && in(lock, p.mx(), p.my());
            Art.iconTile(g, "lock", lock[0], lock[1], SIZE, p.kept() || onLock ? HomeTab.CHOSEN : Theme.INK,
                    p.kept() || onLock ? HomeTab.CHOSEN_TEXT : Theme.CREAM);
            if (p.hover()) {
                int[] ban = ban(p.x(), p.y(), p.s());
                Art.iconTile(g, "x", ban[0], ban[1], SIZE, in(ban, p.mx(), p.my()) ? Theme.BAD : Theme.INK, Theme.CREAM);
            }
        }
        pending.clear();
    }

    private static boolean in(int[] r, int mx, int my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static int[] lock(int x, int y) {
        return new int[] {x + INSET, y + INSET, SIZE, SIZE};
    }

    private static int[] ban(int x, int y, int s) {
        return new int[] {x + s - INSET - SIZE, y + INSET, SIZE, SIZE};
    }
}
