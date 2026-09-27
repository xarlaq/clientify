package dev.clientify.mixin.client;

import dev.clientify.client.modules.TotemModule;
import net.minecraft.client.player.ItemActivation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starts the totem animation's countdown longer when Totem Tweaks stretches it.
 *
 * <p>On 1.21.11 the countdown was a field on ScreenEffectRenderer, set in displayItemActivation. 26.x
 * gives it a class of its own, owned by the local player, and the renderer only sees a copy of it in
 * the render state. The other half - reading it back divided so vanilla's curve still spans forty
 * ticks - is in ScreenEffectRendererMixin.
 */
@Mixin(ItemActivation.class)
public abstract class ItemActivationMixin {
	@Shadow
	private int ticks;

	@Inject(method = "activate", at = @At("RETURN"))
	private void clientify$totemLength(ItemStack itemStack, RandomSource random, CallbackInfo ci) {
		float factor = TotemModule.durationFactor();
		if (factor != 1f) {
			ticks = Math.max(1, Math.round(ticks * factor));
		}
	}
}
