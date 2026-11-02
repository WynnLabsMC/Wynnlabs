package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.PriceBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Notes where Wynnventory puts its price box ({@link PriceBox}), so our item tooltip keeps out
 * from under it. Soft: without Wynnventory the target does not exist and nothing is applied.
 * Reading only; the box is drawn where Wynnventory meant it.
 */
@Pseudo
@Mixin(targets = "com.wynnventory.util.RenderUtils", remap = false)
public abstract class PriceBoxMixin {

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
