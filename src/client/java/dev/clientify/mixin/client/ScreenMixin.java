package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.clientify.client.modules.GuiScaleModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives a screen its own GUI scale, for the interface-scale module.
 *
 * <p>A screen is laid out entirely in the width and height it is handed — that is the contract
 * every vanilla screen and nearly every modded one keeps — so handing it the canvas its own scale
 * would produce, and then drawing it under the matching transform, is enough to resize it whole.
 * Slot positions, buttons and scroll regions all follow, and the pointer is converted into the
 * same space in {@code MouseHandlerMixin}, so clicks land on what is drawn.
 */
@Mixin(Screen.class)
public class ScreenMixin {
	@ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int clientify$initWidth(int width) {
		return GuiScaleModule.layoutWidth((Screen) (Object) this, width);
	}

	@ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int clientify$initHeight(int height) {
		return GuiScaleModule.layoutHeight((Screen) (Object) this, height);
	}

	@ModifyVariable(method = "resize(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int clientify$resizeWidth(int width) {
		return GuiScaleModule.layoutWidth((Screen) (Object) this, width);
	}

	@ModifyVariable(method = "resize(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int clientify$resizeHeight(int height) {
		return GuiScaleModule.layoutHeight((Screen) (Object) this, height);
	}

	// The whole screen goes through here — background, contents and the deferred tooltips after
	// them — so one transform around it covers everything the screen draws.
	/**
	 * One wrapper rather than a push at the head and a pop at the return.
	 *
	 * <p>Two things were wrong with the pair. {@code endScreenDraw} answers whether the module is
	 * drawing scaled, which is not the same question as whether a matrix was pushed: asking for a
	 * screen scale equal to the one already in force gives a factor of exactly one, so nothing was
	 * pushed and something was popped anyway. The pop now follows what this method itself did.
	 *
	 * <p>And {@code @At("RETURN")} binds to the original method's returns, so a mod cancelling the
	 * screen render skipped the reset entirely — leaving the module telling the rest of the game it
	 * was drawing scaled for good. The finally puts that beyond reach of a cancel or a throw.
	 */
	@WrapMethod(method = "renderWithTooltipAndSubtitles")
	private void clientify$scaleScreen(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
			float partialTick, Operation<Void> original) {
		float scale = GuiScaleModule.beginScreenDraw((Screen) (Object) this);
		boolean pushed = scale != 1f;
		if (pushed) {
			guiGraphics.pose().pushMatrix();
			guiGraphics.pose().scale(scale, scale);
		}
		try {
			original.call(guiGraphics, mouseX, mouseY, partialTick);
		} finally {
			GuiScaleModule.endScreenDraw();
			if (pushed) {
				guiGraphics.pose().popMatrix();
			}
		}
	}
}
