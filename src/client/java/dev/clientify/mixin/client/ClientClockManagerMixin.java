package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.TimeModule;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The time changer's override of the overworld's day.
 *
 * <p>26.x replaced a level's day time with world clocks, and the sky reads its sun angle from a
 * timeline sampled against the overworld clock. On 26.2 every reader - the timeline sampler, the
 * level's own day time, loot checks - asks the manager through getTotalTicks, and the instances are
 * private data holders nothing else touches, so the return of that one method is the whole surface.
 *
 * <p>26.3 hands its instances out and lets callers read them directly, which is why that version
 * tracks the day clock's instance instead. This is the simpler shape for the simpler API.
 */
@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
	@ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
	private long clientify$overrideDayTime(long original, Holder<WorldClock> definition) {
		return definition.is(WorldClocks.OVERWORLD) ? TimeModule.overrideDayTime(original) : original;
	}
}
