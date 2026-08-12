package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.clientify.client.modules.OverlayModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fades the full-screen camera overlays for the Overlay module: the pumpkin (and any helmet like
 * it), the freezing vignette, and the spyglass scope.
 *
 * <p>The first two come through vanilla's one overlay method, told apart by the texture it is
 * handed, so a single wrap covers both. The spyglass draws its scope and then four black bars over
 * the corners, and those have to fade together or a see-through scope would sit in a solid black
 * frame.
 */
@Mixin(Gui.class)
public abstract class GuiOverlayMixin {
	@WrapOperation(method = "renderCameraOverlays",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Gui;renderTextureOverlay("
							+ "Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/Identifier;F)V"))
	private void clientify$fadeOverlay(Gui gui, GuiGraphics guiGraphics, Identifier texture, float alpha,
			Operation<Void> original) {
		float opacity = OverlayModule.overlayOpacity(texture);
		if (opacity <= 0f) {
			return; // fully faded: skip the draw rather than submit an invisible one
		}
		original.call(gui, guiGraphics, texture, alpha * opacity);
	}

	@WrapOperation(method = "renderSpyglassOverlay",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;"
							+ "Lnet/minecraft/resources/Identifier;IIFFIIII)V"))
	private void clientify$fadeScope(GuiGraphics guiGraphics, RenderPipeline pipeline, Identifier texture,
			int x, int y, float u, float v, int w, int h, int texW, int texH, Operation<Void> original) {
		float opacity = OverlayModule.spyglassOpacity();
		if (opacity >= 1f) {
			original.call(guiGraphics, pipeline, texture, x, y, u, v, w, h, texW, texH);
			return;
		}
		if (opacity > 0f) {
			guiGraphics.blit(pipeline, texture, x, y, u, v, w, h, texW, texH, ARGB.white(opacity));
		}
	}

	@ModifyArg(method = "renderSpyglassOverlay",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V"),
			index = 5)
	private int clientify$fadeScopeBars(int color) {
		float opacity = OverlayModule.spyglassOpacity();
		return opacity >= 1f ? color : ARGB.color(Math.round(255 * opacity), color & 0xFFFFFF);
	}

}
