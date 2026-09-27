package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.WeatherModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.biome.Biome.Precipitation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Forces the precipitation TYPE for the weather-changer module: Snow mode snows (and Rain
 * rains) in every biome that can precipitate at all. Biomes with no precipitation (deserts)
 * stay dry, matching vanilla's per-biome gate.
 *
 * <p>On 26.x this sits on ClientLevel rather than WeatherEffectRenderer. The renderer's own copy
 * served two callers on 1.21.11 - the falling rain or snow, and the splash and sound check - and on
 * 26.x both of those call ClientLevel.getPrecipitationAt instead, which nothing else on the client
 * calls. Hooking it keeps the old scope exactly; hooking only the renderer's call would have left
 * rain sounds playing under forced snow.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelPrecipitationMixin {
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
