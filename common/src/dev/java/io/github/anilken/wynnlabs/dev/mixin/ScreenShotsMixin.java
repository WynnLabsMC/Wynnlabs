package io.github.anilken.wynnlabs.dev.mixin;

import io.github.anilken.wynnlabs.dev.Shots;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dev builds only (wynnlabs.dev.mixins.json): the mod's own windows the screenshot tool opens over
 * the menu (the box window, the update window) drawn as the menu draws itself while it captures:
 * the frame's boxes noted afresh, the tool's mouse instead of the real one, the flat backdrop
 * instead of the world. The menu does the same itself (RoadmapScreen). Does nothing otherwise.
 */
@Mixin(Screen.class)
public abstract class ScreenShotsMixin {

    @Inject(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"))
    private void wynnlabs$beginFrame(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (Shots.capturing()) {
            Shots.beginFrame();
        }
    }

    @ModifyVariable(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int wynnlabs$mouseX(int mx) {
        return Shots.capturing() ? Shots.mouseX() : mx;
    }

    @ModifyVariable(method = "renderWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int wynnlabs$mouseY(int my) {
        return Shots.capturing() ? Shots.mouseY() : my;
    }

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$backdrop(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        if (Shots.capturing()) {
            var w = Minecraft.getInstance().getWindow();
            g.fill(0, 0, w.getGuiScaledWidth() + 1, w.getGuiScaledHeight() + 1, Shots.backdrop());
            ci.cancel();
        }
    }
}
