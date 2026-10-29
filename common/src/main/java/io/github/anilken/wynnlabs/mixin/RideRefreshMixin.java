package io.github.anilken.wynnlabs.mixin;

import io.github.anilken.wynnlabs.mounts.RideWatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundMountScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The mount panel's refresh ({@link io.github.anilken.wynnlabs.mounts.RideRefresh}): the action
 * bar watched for the pill and the energy glyph, and the server's answer to our own inventory
 * request kept from the screen (its menu never opens, its contents are read, then it is closed).
 * Every other packet goes on as before. Each hook runs on the client thread only (a packet is
 * first seen on the network thread and handed over).
 */
@Mixin(value = ClientPacketListener.class, priority = 900)
public class RideRefreshMixin {

    @Inject(method = "setActionBarText", at = @At("HEAD"))
    private void wynnlabs$rideBar(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) {
            return;
        }
        try {
            RideWatch.onBar(packet.text());
        } catch (Throwable ignored) {
            // the action bar must never break
        }
    }

    @Inject(method = "handleOpenScreen", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$rideOpen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && RideWatch.onOpenScreen(packet.getContainerId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleMountScreenOpen", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$rideMountOpen(ClientboundMountScreenOpenPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && RideWatch.onOpenScreen(packet.getContainerId())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"), cancellable = true)
    private void wynnlabs$rideContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread() && RideWatch.onContent(packet.containerId(), packet.items())) {
            ci.cancel();
        }
    }
}
