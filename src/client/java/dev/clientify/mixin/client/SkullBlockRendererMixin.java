package dev.clientify.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.OverlayModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves placed heads undrawn when the Overlay module asks — they are a common view-blocker. */
@Mixin(SkullBlockRenderer.class)
public class SkullBlockRendererMixin {
	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void clientify$hideSkulls(SkullBlockRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
		if (OverlayModule.hidePlacedSkulls()) {
			ci.cancel();
		}
	}
}
