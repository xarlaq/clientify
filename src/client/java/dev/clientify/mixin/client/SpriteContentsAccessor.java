package dev.clientify.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the pixels behind a stitched sprite. Vanilla keeps them to itself once the atlas is
 * built, and painting INTO a block's texture — rather than drawing something over the block — is
 * the only way for it to behave like part of the block: shaded with it, hidden with it, and free
 * every frame thereafter.
 *
 * <p>The strategy and the cutoff bias are the sprite's own mipmap settings, read back so the
 * smaller levels can be rebuilt the way this texture asks for rather than by a guess. A pack (and
 * vanilla's own glass) can set {@code mipmap_strategy} in a {@code .mcmeta}, and getting it wrong
 * shows up as dark fringes or vanishing detail in the distance.
 */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor("originalImage")
	NativeImage clientify$original();

	@Accessor("byMipLevel")
	NativeImage[] clientify$mips();

	@Accessor("byMipLevel")
	void clientify$setMips(NativeImage[] mips);

	@Accessor("mipmapStrategy")
	MipmapStrategy clientify$mipmapStrategy();

	/**
	 * Set on a sprite we have painted, so the game does not scale our alpha away again.
	 *
	 * <p>A cutout strategy holds a textures COVERAGE as it shrinks, which for a pane we have
	 * just made half transparent means pushing level nought back towards solid while the smaller
	 * levels fade -- solid up close, gone at distance.
	 */
	@org.spongepowered.asm.mixin.Mutable
	@Accessor("mipmapStrategy")
	void clientify$setMipmapStrategy(MipmapStrategy strategy);

	@Accessor("alphaCutoffBias")
	float clientify$alphaCutoffBias();
}
