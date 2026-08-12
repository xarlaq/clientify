package dev.clientify.mixin.client;

import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets the ore outlines ask the atlas to rebuild itself from its sprites' pixels.
 *
 * <p>Writing changed pixels straight into the atlas at a sprite's x and y looked obvious and was
 * wrong: this version composes the atlas by RENDERING each sprite into it, so a sprite's recorded
 * position is not where a direct write has to go, and paint for one ore landed across another's
 * texture. Vanilla's own compose step puts every sprite exactly where it belongs, so the pixels are
 * changed and then this is called to lay them out.
 */
@Mixin(TextureAtlas.class)
public interface TextureAtlasInvoker {
	@Invoker("uploadInitialContents")
	void clientify$rebuildContents();

	/**
	 * The stitched sprites, or null before the atlas has been uploaded.
	 *
	 * <p>Worth asking rather than assuming: the block atlas is not ready when the title screen is,
	 * and {@code getSprite} does not return null there — it throws.
	 */
	@Accessor("texturesByName")
	Map<Identifier, TextureAtlasSprite> clientify$sprites();
}
