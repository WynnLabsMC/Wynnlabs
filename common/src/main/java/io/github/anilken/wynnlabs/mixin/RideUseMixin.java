package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.mounts.RideWatch;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mount panel's last used mount slot: a main-hand item used in the air or on a block (how a
 * Wynncraft mount is summoned) is noted when it reads as a mount. Only looks; the use goes on as
 * before.
 */
@Mixin(MultiPlayerGameMode.class)
public class RideUseMixin {

    @Inject(method = "useItem", at = @At("HEAD"))
    private void wynnlabs$rideUse(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        wynnlabs$note(hand);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void wynnlabs$rideUseOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        wynnlabs$note(hand);
    }

    private static void wynnlabs$note(InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }
        try {
            RideWatch.onUse();
        } catch (Throwable ignored) {
            // a use must never break
        }
    }
}
