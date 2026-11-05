package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.ui.RawInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keys and typed characters reach a {@link RawInput} screen before the screen's own handling. */
@Mixin(KeyboardHandler.class)
public class RawInputMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$rawKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == 0) {
            return;                                 // releases go on as before
        }
        try {
            if (Minecraft.getInstance().screen instanceof RawInput r && r.rawKey(event.key())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // input must never break
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$rawChar(long window, CharacterEvent event, CallbackInfo ci) {
        try {
            if (Minecraft.getInstance().screen instanceof RawInput r && r.rawChar(event.codepoint())) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // input must never break
        }
    }
}
