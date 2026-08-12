package dev.clientify.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes what a bobber is hooked into; vanilla keeps it to itself and offers no getter. */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {
	@Accessor("hookedIn")
	Entity clientify$hookedIn();
}
