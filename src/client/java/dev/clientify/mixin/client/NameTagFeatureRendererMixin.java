package dev.clientify.mixin.client;

import dev.clientify.client.modules.NametagsModule;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Nametags module's text shadow, drawn by the font itself.
 *
 * <p>Vanilla draws nametags with the shadow off; this turns it on while the module wants one. The
 * font then puts the shadow a fraction of a block behind the text and darkens each glyph's own
 * colour for it. The earlier way - a second copy of the text a pixel across - took its colour from
 * the submission, which a coloured name overrides: on a server with team colours the "shadow" was a
 * full-colour copy fighting the text for depth, and tags came out smeared. Both passes (the normal
 * one and the see-through one) go through the same call.
 */
@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {
	@ModifyArg(method = "renderTranslucent",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZ"
							+ "Lorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;"
							+ "Lnet/minecraft/client/gui/Font$DisplayMode;II)V"),
			index = 4)
	private boolean clientify$nameTagShadow(boolean dropShadow) {
		return dropShadow || NametagsModule.textShadow();
	}
}
