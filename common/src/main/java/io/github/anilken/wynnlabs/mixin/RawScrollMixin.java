package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.RawInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The scroll wheel reaches a {@link RawInput} screen before the screen's own handling. */
@Mixin(MouseHandler.class)
public class RawScrollMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$rawScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof RawInput r) {
                MouseHandler self = (MouseHandler) (Object) this;
                if (r.rawScroll(self.getScaledXPos(mc.getWindow()), self.getScaledYPos(mc.getWindow()), vertical)) {
                    ci.cancel();
                }
            }
        } catch (Throwable ignored) {
            // input must never break
        }
    }
}
