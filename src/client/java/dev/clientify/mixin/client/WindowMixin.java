package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.platform.Window;
import dev.clientify.client.modules.GuiScaleModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reports the GUI canvas of a screen that has a scale of its own, but only while that screen is
 * drawing itself — the module arms it around the screen's draw and disarms it straight after.
 *
 * <p>Everything else in the frame, the HUD above all, asks the same question outside that window
 * and gets the game's own answer, which is what keeps the two sizes independent. The scale itself
 * ({@code getGuiScale}) is deliberately left alone: the GUI projection matrix is built from it, so
 * changing it would resize the whole frame — the very thing this replaced.
 */
@Mixin(Window.class)
public class WindowMixin {
	@ModifyReturnValue(method = "getGuiScaledWidth", at = @At("RETURN"))
	private int clientify$canvasWidth(int original) {
		return GuiScaleModule.canvasWidth(original);
	}

	@ModifyReturnValue(method = "getGuiScaledHeight", at = @At("RETURN"))
	private int clientify$canvasHeight(int original) {
		return GuiScaleModule.canvasHeight(original);
	}
}
