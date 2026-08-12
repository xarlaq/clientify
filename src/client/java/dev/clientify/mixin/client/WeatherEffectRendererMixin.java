package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.WeatherModule;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forces the precipitation TYPE for the weather-changer module: Snow mode snows (and Rain
 * rains) in every biome that can precipitate at all. Biomes with no precipitation (deserts)
 * stay dry, matching vanilla's per-biome gate.
 */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
	@ModifyReturnValue(method = "getPrecipitationAt", at = @At("RETURN"))
	private Precipitation clientify$forcePrecipitation(Precipitation original) {
		WeatherModule.Weather forced = WeatherModule.forced();
		if (forced == null || original == Precipitation.NONE) {
			return original;
		}
		return switch (forced) {
			case SNOW -> Precipitation.SNOW;
			case RAIN, THUNDER -> Precipitation.RAIN;
			case CLEAR -> original;
		};
	}
}
