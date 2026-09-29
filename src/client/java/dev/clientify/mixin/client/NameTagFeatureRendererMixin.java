package dev.clientify.mixin.client;

import dev.clientify.client.modules.NametagsModule;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The Nametags module's text shadow, drawn by the font itself.
 *
 * <p>Vanilla prepares nametag text with the shadow off; this turns it on while the module wants one.
 * The font then puts the shadow a fraction of a block behind the text and darkens each glyph's own
 * colour for it. The earlier way - a second copy of the text a pixel across - took its colour from
 * the submission, which a coloured name overrides: on a server with team colours the "shadow" was a
 * full-colour copy fighting the text for depth, and tags came out smeared. Both passes (the normal
 * one and the see-through one) prepare their text through this one helper.
 */
@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {
	@ModifyArg(method = "prepareText",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)"
							+ "Lnet/minecraft/client/gui/Font$PreparedText;"),
			index = 4)
	private static boolean clientify$nameTagShadow(boolean dropShadow) {
		return dropShadow || NametagsModule.textShadow();
	}
}
