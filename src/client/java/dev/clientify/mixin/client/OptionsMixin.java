package dev.clientify.mixin.client;

import dev.clientify.client.hud.BlurBackdrop;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes Clientify's independent blur strengths for the "Menu Background Blurriness"
 * option while a Clientify surface needs blur (menus with menu-blur on; chips with
 * background blur). The vanilla blur post chain reads this value, so the sliders work even
 * when the user's accessibility option is 0.
 *
 * <p>NOTE: the method returns a primitive int — the CallbackInfoReturnable MUST be typed
 * Integer (a Float here compiles but CCEs at runtime; learned the hard way).
 */
@Mixin(Options.class)
public class OptionsMixin {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("HEAD"), cancellable = true)
	private void clientify$overrideBlurriness(CallbackInfoReturnable<Integer> cir) {
		Integer override = BlurBackdrop.blurOverride();
		if (override != null) {
			cir.setReturnValue(override);
		}
	}
}
