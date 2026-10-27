package io.github.anilken.wynnlabs.preview;

import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The preview runs without a window on the screen: the game's window is created hidden. The game
 * draws into its own render target as always, so the shots and the resize to -PpreviewSize are the
 * same. -PpreviewHidden=false shows it again (to watch a run). Preview only: not in the mod's jar.
 */
@Mixin(Window.class)
abstract class HiddenWindowMixin {

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",
            remap = false))
    private void wynnlabsPreview$hidden(CallbackInfo ci) {
        if (!"false".equals(System.getProperty("wynnlabs.previewHidden", "true"))) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        }
    }
}
