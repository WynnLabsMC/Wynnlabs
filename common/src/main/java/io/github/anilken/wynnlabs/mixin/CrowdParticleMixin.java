package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.CrowdMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crowd mode: particles farther than the radius are never made. */
@Mixin(ClientLevel.class)
public class CrowdParticleMixin {

    @Inject(method = "doAddParticle", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$crowd(ParticleOptions options, boolean force, boolean decreased, double x, double y,
            double z, double dx, double dy, double dz, CallbackInfo ci) {
        if (CrowdMode.dropParticle(x, y, z)) {
            ci.cancel();
        }
    }
}
