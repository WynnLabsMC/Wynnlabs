package io.github.anilken.wynnlabs.model;

/**
 * A gathering tool: one the game has, or one in the player's bag.
 *
 * @param name          the tool's name ("Iron Pickaxe T6"), also its Trade Market name
 * @param profession    the gathering profession it is for ("mining")
 * @param tier          its tier, 1 and up
 * @param level         the profession level it needs
 * @param speed         its gathering speed
 * @param durability    what is left of it; -1 for a tool not in the bag
 * @param maxDurability its full durability
 * @param icon          the item to draw (the bag's own stack, else the tool's model)
 */
public record GatherTool(String name, String profession, int tier, int level, int speed, int durability, int maxDurability,
        net.minecraft.world.item.ItemStack icon) {}
