package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.PriceBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Wynnventory's price box and our item tooltip ({@link PriceBox}): the box's lines are taken as
 * Wynnventory is about to draw them, to be drawn beside our tooltip; and where Wynnventory puts
 * the box (anchored, or handed back) is noted so our tooltip keeps out from under it. Soft:
 * without Wynnventory the target does not exist and nothing is applied.
 */
@Pseudo
@Mixin(targets = "com.wynnventory.util.RenderUtils", remap = false)
public abstract class PriceBoxMixin {

    @Inject(method = "drawTooltip", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void wynnlabs$take(GuiGraphics g, int mouseX, int mouseY, List<Component> vanilla,
            List<Component> custom, CallbackInfo ci) {
        try {
            if (PriceBox.take(g, mouseX, mouseY, vanilla, custom)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // Wynnventory then draws as usual
        }
    }

    @Inject(method = "calculateTooltipCoords", at = @At("RETURN"), remap = false, require = 0)
    private static void wynnlabs$noteBox(int mouseX, int mouseY, List<ClientTooltipComponent> vanilla,
            List<ClientTooltipComponent> price, CallbackInfoReturnable<Vector2i> cir) {
        try {
            Vector2i at = cir.getReturnValue();
            if (at == null || price == null || price.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;
            int w = 0;
            int h = 0;
            for (ClientTooltipComponent c : price) {
                w = Math.max(w, c.getWidth(font));
                h += c.getHeight(font);
            }
            // the place is in the box's own scaled units: it shrinks to fit the screen's height
            int screenH = mc.getWindow().getGuiScaledHeight();
            float scale = h + 10 > screenH ? screenH / (float) (h + 10) : 1f;
            int frame = 3;
            PriceBox.note(Math.round(at.x * scale) - frame, Math.round(at.y * scale) - frame,
                    Math.round(w * scale) + 2 * frame, Math.round(h * scale) + 2 * frame);
        } catch (Throwable ignored) {
            // a note must never break the tooltip
        }
    }
}
