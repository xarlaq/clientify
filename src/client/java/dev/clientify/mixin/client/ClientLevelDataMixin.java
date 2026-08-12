package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.TimeModule;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-side time override: everything that reads the client level's day time (sky, sun
 * angle, lighting) sees the module's chosen time. Server-side time is untouched, and the
 * server's regular time sync writes the field directly so the real value survives.
 */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataMixin {
	@ModifyReturnValue(method = "getDayTime", at = @At("RETURN"))
	private long clientify$overrideDayTime(long original) {
		return TimeModule.overrideDayTime(original);
	}
}
