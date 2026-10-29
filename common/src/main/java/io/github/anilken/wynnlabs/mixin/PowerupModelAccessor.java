package io.github.anilken.wynnlabs.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers a resolved item model filled in: their quads name the powerup's sprite. Read only. */
@Mixin(ItemStackRenderState.class)
public interface PowerupModelAccessor {

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] wynnlabs$layers();

    @Accessor("activeLayerCount")
    int wynnlabs$layerCount();
}
