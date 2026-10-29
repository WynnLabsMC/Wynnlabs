package io.github.anilken.wynnlabs.mixin;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** One item layer's baked quads (each names its sprite). Read only. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface PowerupLayerAccessor {

    @Accessor("quads")
    List<BakedQuad> wynnlabs$quads();
}
