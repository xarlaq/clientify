package dev.clientify.mixin.client;

import dev.clientify.client.modules.TimeModule;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes which clock instance is the overworld's day, for the time changer.
 *
 * <p>26.x replaced a level's day time with world clocks, and the sky reads its sun angle from a
 * timeline sampled against one of them - the overworld clock, per the 26.3 {@code day} timeline.
 * An instance does not know which clock it belongs to; the manager does, at the moment it hands one
 * out, so this is where the day clock gets recognised. See {@link ClientClockInstanceMixin}.
 */
@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {
	@Inject(method = "getInstance", at = @At("RETURN"))
	private void clientify$noteDayClock(Holder<WorldClock> definition,
			CallbackInfoReturnable<ClientClockManager.ClientClockInstance> cir) {
		if (definition.is(WorldClocks.OVERWORLD)) {
			TimeModule.dayClock(cir.getReturnValue());
		}
	}
}
