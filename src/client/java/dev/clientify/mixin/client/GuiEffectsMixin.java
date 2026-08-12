package dev.clientify.mixin.client;

import dev.clientify.client.modules.EffectsHudModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides vanilla's top-right potion icons while the Effects HUD module replaces them. */
@Mixin(Gui.class)
public abstract class GuiEffectsMixin {
	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
	private void clientify$hideVanillaEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		if (EffectsHudModule.hideVanillaActive()) {
			ci.cancel();
		}
	}
}
