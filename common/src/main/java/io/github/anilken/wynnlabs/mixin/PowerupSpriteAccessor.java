package io.github.anilken.wynnlabs.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A loaded sprite's own pixels, hashed once to tell mount powerups apart. Read only. */
@Mixin(SpriteContents.class)
public interface PowerupSpriteAccessor {

    @Accessor("originalImage")
    NativeImage wynnlabs$image();
}
