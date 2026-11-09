package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.NodeBoxes;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The gathering node boxes ({@link NodeBoxes}) as debug boxes at the start of the level render:
 * the game takes debug boxes for a frame only until it starts drawing them, later in this method.
 */
@Mixin(LevelRenderer.class)
public class NodeBoxesFrameMixin {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void wynnlabs$nodeBoxes(CallbackInfo ci) {
        NodeBoxes.drawFrame();
    }
}
