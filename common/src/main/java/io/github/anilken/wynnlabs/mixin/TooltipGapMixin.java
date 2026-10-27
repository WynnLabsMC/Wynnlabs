package io.github.anilken.wynnlabs.mixin;

import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The game's tooltip sits 12 GUI px right of and above the mouse (24 when it flips left). A
 * screen drawn at the menu scale places its tooltip in its own logical pixels, where those 12
 * shrink on screen when the menu is smaller than the game's GUI: the gap is scaled back so it is
 * the same on screen at every menu scale ({@link io.github.anilken.wynnlabs.ui.MenuScale#tooltipGap}).
 */
@Mixin(DefaultTooltipPositioner.class)
public abstract class TooltipGapMixin {

    @ModifyConstant(method = "positionTooltip(IIIIII)Lorg/joml/Vector2ic;", constant = {@Constant(intValue = 12), @Constant(intValue = -12)})
    private int wynnlabs$gap(int v) {
        return Math.round(v * io.github.anilken.wynnlabs.ui.MenuScale.tooltipGap());
    }

    @ModifyConstant(method = "positionTooltip(IILorg/joml/Vector2i;II)V", constant = @Constant(intValue = 24))
    private int wynnlabs$flip(int v) {
        return Math.round(v * io.github.anilken.wynnlabs.ui.MenuScale.tooltipGap());
    }
}
