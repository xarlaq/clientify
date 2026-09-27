package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.clientify.client.modules.GuiScaleModule;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts a picture in a picture — the inventory's player preview, sign text, banner results, the
 * enchanting book — where a screen the interface-scale module rescaled actually draws it.
 *
 * <p>These are rendered to a texture of their own and blitted back, and that blit carries no
 * transform by design ({@code PictureInPictureRenderState.pose()} is the identity). The scissor
 * around it is taken at submit time and does carry one, so under a rescaled screen the two
 * disagree: the picture lands at unscaled coordinates and is clipped away by a scissor around
 * where it should have been. Scaling its coordinates here is what the transform would have done.
 *
 * <p>The scale the texture is rendered at moves with them, or the picture would be rasterised for
 * the old size and stretched into the new one.
 */
@Mixin(PictureInPictureRenderer.class)
public class PictureInPictureRendererMixin {
	// blitTexture is required to hit on vanilla but NOT on VulkanMod, which merges that method
	// into its own and leaves nothing of ours to attach to. Required, it took the game down at
	// startup; optional, the picture in picture sizing is simply left to VulkanMod there.
	private static final String STATE = "Lnet/minecraft/client/gui/render/state/pip/PictureInPictureRenderState;";

	// Everything below runs inside prepare, blitTexture included, so one lookup covers the lot.
	@Inject(method = "prepare", at = @At("HEAD"))
	private void clientify$beginPicture(PictureInPictureRenderState state, GuiRenderState guiRenderState,
			int guiScale, CallbackInfo ci) {
		GuiScaleModule.beginPictureInPicture(state);
	}

	@Inject(method = "prepare", at = @At("RETURN"))
	private void clientify$endPicture(PictureInPictureRenderState state, GuiRenderState guiRenderState,
			int guiScale, CallbackInfo ci) {
		GuiScaleModule.endPictureInPicture();
	}

	// The size of the texture it is rendered into.
	@ModifyExpressionValue(method = "prepare", at = @At(value = "INVOKE", target = STATE + "x0()I"))
	private int clientify$prepareX0(int original) {
		return GuiScaleModule.scalePicture(original);
	}

	@ModifyExpressionValue(method = "prepare", at = @At(value = "INVOKE", target = STATE + "x1()I"))
	private int clientify$prepareX1(int original) {
		return GuiScaleModule.scalePicture(original);
	}

	@ModifyExpressionValue(method = "prepare", at = @At(value = "INVOKE", target = STATE + "y0()I"))
	private int clientify$prepareY0(int original) {
		return GuiScaleModule.scalePicture(original);
	}

	@ModifyExpressionValue(method = "prepare", at = @At(value = "INVOKE", target = STATE + "y1()I"))
	private int clientify$prepareY1(int original) {
		return GuiScaleModule.scalePicture(original);
	}

	@ModifyExpressionValue(method = "prepare", at = @At(value = "INVOKE", target = STATE + "scale()F"))
	private float clientify$prepareScale(float original) {
		return GuiScaleModule.scalePicture(original);
	}

	// Where it lands on screen.
	@ModifyExpressionValue(require = 0, method = "blitTexture", at = @At(value = "INVOKE", target = STATE + "x0()I"))
	private int clientify$blitX0(int original) {
		return GuiScaleModule.scalePicture(original);
	}

	@ModifyExpressionValue(require = 0, method = "blitTexture", at = @At(value = "INVOKE", target = STATE + "x1()I"))
	private int clientify$blitX1(int original) {
		return GuiScaleModule.scalePicture(original);
	}

	@ModifyExpressionValue(require = 0, method = "blitTexture", at = @At(value = "INVOKE", target = STATE + "y0()I"))
	private int clientify$blitY0(int original) {
		return GuiScaleModule.scalePicture(original);
	}

	@ModifyExpressionValue(require = 0, method = "blitTexture", at = @At(value = "INVOKE", target = STATE + "y1()I"))
	private int clientify$blitY1(int original) {
		return GuiScaleModule.scalePicture(original);
	}
}
