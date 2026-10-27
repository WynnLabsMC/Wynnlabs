package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * The crafting tab's item pictures: an ingredient's or a material's own model (as the search shows
 * it: Wynntils' guide stack, else the item of the data's icon spec), drawn in an HD slot. Kept per
 * name, made on first use on the render thread.
 */
final class CraftIcons {

    private static final SearchIcons ICONS = new SearchIcons();
    private static final Map<String, ItemStack> NONE = new HashMap<>();
    private static final ItemStack PAPER = new ItemStack(Items.PAPER);

    private CraftIcons() {}

    /** The stack for an ingredient or material name; paper when the index does not know it (yet). */
    static ItemStack of(String name) {
        if (name == null) {
            return null;
        }
        Entry e = ItemIndex.get().byName(name);
        if (e == null) {
            return NONE.computeIfAbsent(name, k -> PAPER);
        }
        return ICONS.of(e);
    }

    /** An HD slot {@code size} square with the item inside it ({@code null}: an empty slot). */
    static void slot(GuiGraphics g, int x, int y, int size, ItemStack stack) {
        Kit.slot(g, x, y, size, 0);
        if (stack == null || stack.isEmpty()) {
            return;
        }
        int isz = size <= 24 ? size - 4 : size - 8;
        int in = (size - isz) / 2;
        g.pose().pushMatrix();
        g.pose().translate(x + in, y + in);
        g.pose().scale(isz / 16f, isz / 16f);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    /** A crafted type's slot: its icon (a sprite) in an HD slot. */
    static void typeSlot(GuiGraphics g, String type, int x, int y, int size, int ink) {
        Kit.slot(g, x, y, size, 0);
        int isz = size <= 18 ? 8 : 12;
        Art.icon(g, CraftFormat.typeIcon(type), x + (size - isz) / 2, y + (size - isz) / 2, isz, ink);
    }
}
