package io.github.anilken.wynnlabs.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** An item display's item (a private getter in 1.21.11), read to find mount powerups. Read only. */
@Mixin(Display.ItemDisplay.class)
public interface PowerupDisplayAccessor {

    @Invoker("getItemStack")
    ItemStack wynnlabs$stack();
}
