package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.TimeModule;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-side time override on 26.x: the overworld day clock reports the time the module chose.
 *
 * <p>The successor to overriding {@code ClientLevelData.getDayTime} on 1.21.11. Everything that
 * reads the clock through this method - the timelines behind the sun, moon and star angles, sky
 * colour and light - sees the chosen time. The clock's own bookkeeping writes and advances its
 * fields directly, so the server's real time keeps running underneath and returns as soon as the
 * module is switched off.
 */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClientClockInstanceMixin {
	@ModifyReturnValue(method = "totalTicks", at = @At("RETURN"))
	private long clientify$overrideDayTime(long original) {
		return TimeModule.isDayClock(this) ? TimeModule.overrideDayTime(original) : original;
	}
}
