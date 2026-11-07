package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.game.Wyn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Loot chest and cave icons on Wynntils' map and minimap are drawn smaller ("smallMapIcons"): the scale
 * Wynntils draws an icon at, and the hit box that goes with it, pass through {@link Wyn}.
 */
@Mixin(targets = "com.wynntils.services.map.pois.IconPoi", remap = false)
public abstract class ChestIconScaleMixin {

    @ModifyVariable(method = "renderAt", at = @At("HEAD"), argsOnly = true, index = 5)
    private float wynnlabs$drawScale(float scale) {
        return scaled(scale);
    }

    @ModifyVariable(method = {"getWidth", "getHeight"}, at = @At("HEAD"), argsOnly = true, index = 2)
    private float wynnlabs$boxScale(float scale) {
        return scaled(scale);
    }

    private float scaled(float scale) {
        try {
            return Wyn.chestIconScale(this, scale);
        } catch (Throwable t) {
            return scale;
        }
    }
}
