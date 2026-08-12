package dev.clientify.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.clientify.client.util.BlockTextures;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints a texture as it is loaded, before anything has been made of it.
 *
 * <p>This is the only moment that works for an ANIMATED texture. Water and fire keep every frame
 * in one tall image and blit the current one over the atlas each tick from a copy taken when the
 * atlas was built — so a repaint after the fact is wiped within a tick, however it is uploaded.
 * Painted here, the copy is made FROM our pixels and every frame carries them.
 *
 * <p>Here rather than at atlas upload, which was the earlier attempt and broke the ore textures:
 * the mipmaps have not been generated yet, so the game builds them from what we painted instead of
 * us rebuilding them underneath an atlas that had already been laid out.
 *
 * <p>Costs one pass over a handful of images while textures are loading, and nothing afterwards.
 */
@Mixin(SpriteContents.class)
public class SpriteContentsPaintMixin {
	@Inject(method = "<init>(Lnet/minecraft/resources/Identifier;"
			+ "Lnet/minecraft/client/resources/metadata/animation/FrameSize;"
			+ "Lcom/mojang/blaze3d/platform/NativeImage;"
			+ "Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V", at = @At("RETURN"))
	private void clientify$paintOnLoad(Identifier name,
			net.minecraft.client.resources.metadata.animation.FrameSize frameSize, NativeImage image,
			java.util.Optional<?> animation, java.util.List<?> metadata,
			java.util.Optional<?> textureMeta, CallbackInfo ci) {
		if (BlockTextures.paintAtLoad(name, image, frameSize.width(), frameSize.height())
				&& (Object) this instanceof SpriteContentsAccessor access) {
			access.clientify$setMipmapStrategy(net.minecraft.client.renderer.texture.MipmapStrategy.MEAN);
		}
	}
}
