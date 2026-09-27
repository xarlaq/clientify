package dev.clientify.mixin.client;

import dev.clientify.client.modules.EffectsHudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla's top-right potion icons while the Effects HUD module replaces them. */
@Mixin(Hud.class)
public abstract class GuiEffectsMixin {
	@Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
	private void clientify$hideVanillaEffects(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (EffectsHudModule.hideVanillaActive()) {
			ci.cancel();
		}
	}
}
