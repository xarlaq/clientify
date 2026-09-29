package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.clientify.client.modules.GuiScaleModule;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes which pictures in pictures were submitted by a screen the interface-scale module is
 * rescaling. They are drawn later, in a pass of their own, by which time there is no telling a
 * screen's from the HUD's — and an oversized item in the hotbar behind an open inventory is
 * exactly that case.
 */
@Mixin(GuiRenderState.class)
public class GuiRenderStateMixin {
	@Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
	private void clientify$tagPicture(PictureInPictureRenderState state, CallbackInfo ci) {
		GuiScaleModule.tagPictureInPicture(state);
	}

	// Layering happens on the element's bounds, and a tagged picture's are the ones it was
	// submitted with rather than the ones it will be drawn at. Every other element passes through.
	@ModifyExpressionValue(method = "findAppropriateNode",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/state/gui/ScreenArea;bounds()"
							+ "Lnet/minecraft/client/gui/navigation/ScreenRectangle;"))
	private ScreenRectangle clientify$pictureBounds(ScreenRectangle original) {
		return GuiScaleModule.scalePictureBounds(original);
	}
}
