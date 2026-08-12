package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.GuiScaleModule;
import dev.clientify.client.modules.ZoomModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void clientify$zoomScroll(long window, double xDelta, double yDelta, CallbackInfo ci) {
		if (ZoomModule.handleScroll(yDelta)) {
			ci.cancel(); // adjusting zoom — don't scroll the hotbar
		}
	}

	// Every mouse event a screen receives — click, move, drag, scroll — and the pointer the screen
	// is drawn with come from these two, and nothing else turns pixels into GUI coordinates. When
	// the interface-scale module gives a screen a canvas of its own, this is what puts the pointer
	// in it; with no such screen open the factor is 1 and this costs a comparison.
	@ModifyReturnValue(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"))
	private static double clientify$screenSpaceX(double original) {
		return original * GuiScaleModule.mouseFactor();
	}

	@ModifyReturnValue(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("RETURN"))
	private static double clientify$screenSpaceY(double original) {
		return original * GuiScaleModule.mouseFactor();
	}
}
