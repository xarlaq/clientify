package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.clientify.client.modules.FullbrightModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Fullbright: substitutes the gamma the lightmap is built from. Values far above 1 wash it out to
 * full brightness (OptiFine's "gamma 15"); 26.x only floors the result at zero, so that still holds.
 *
 * <p>On 1.21.11 this sat on LightTexture.updateLightTexture and picked the gamma read by position -
 * the third option read in the method. 26.x moved the work to LightmapRenderStateExtractor and
 * REORDERED those reads: gamma is second and darkness effect scale third. Keeping the old ordinal
 * would have compiled cleanly and turned the darkness pulse up fifteenfold instead. So the read is
 * found by what precedes it - the first get() after the gamma() call - which no reordering moves.
 */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {
	@ModifyExpressionValue(
			method = "extract",
			slice = @Slice(from = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/Options;gamma()Lnet/minecraft/client/OptionInstance;")),
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;",
					ordinal = 0)
	)
	private Object clientify$fullbrightGamma(Object original) {
		Double override = FullbrightModule.gammaOverride();
		return override != null ? override : original;
	}
}
