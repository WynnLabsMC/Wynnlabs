package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.CrowdMode;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crowd mode: the flames on burning entities other than you are not drawn. */
@Mixin(EntityRenderer.class)
public class CrowdFireMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void wynnlabs$noFire(T entity, S state, float partialTick, CallbackInfo ci) {
        if (state.displayFireAnimation && CrowdMode.hideFire(entity)) {
            state.displayFireAnimation = false;
        }
    }
}
