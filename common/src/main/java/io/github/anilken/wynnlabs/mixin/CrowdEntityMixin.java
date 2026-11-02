package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.CrowdMode;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Crowd mode: other players' summons and far spell effects are not drawn. */
@Mixin(EntityRenderDispatcher.class)
public class CrowdEntityMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void wynnlabs$crowd(E entity, Frustum frustum, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (CrowdMode.hideEntity(entity)) {
            cir.setReturnValue(false);
        }
    }
}
