package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.clientify.client.modules.FullbrightModule;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	/**
	 * updateLightTexture reads three OptionInstance values in bytecode order:
	 * hideLightningFlash (0), darknessEffectScale (1), gamma (2). We substitute the gamma
	 * read — values far above 1 wash the lightmap to fullbright (OptiFine "gamma 15").
	 */
	@ModifyExpressionValue(
			method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 2)
	)
	private Object clientify$fullbrightGamma(Object original) {
		Double override = FullbrightModule.gammaOverride();
		return override != null ? override : original;
	}
}
