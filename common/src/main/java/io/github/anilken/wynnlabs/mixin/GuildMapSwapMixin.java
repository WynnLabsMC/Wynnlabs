package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.game.Wyn;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The mod's guild map opens wherever Wynntils would open its own, so that one is never drawn. */
@Mixin(Minecraft.class)
public class GuildMapSwapMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen wynnlabs$guildMap(Screen screen) {
        try {
            return Wyn.guildMap(screen);
        } catch (Throwable t) {
            return screen;
        }
    }
}
