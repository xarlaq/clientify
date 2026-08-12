package dev.clientify.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.clientify.client.util.HurtColorAccess;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets HitColorModule recolor the hurt-flash rows of the entity overlay texture. */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin implements HurtColorAccess {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Override
	public void clientify$setHurtColor(int argb) {
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				pixels.setPixel(x, y, argb);
			}
		}
		texture.upload();
	}
}
