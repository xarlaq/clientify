package dev.clientify.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.OverlayModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drops the arrows and stingers stuck in people when the Overlay module asks. */
@Mixin(StuckInBodyLayer.class)
public class StuckInBodyLayerMixin {
	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void clientify$hideStuck(PoseStack poseStack, SubmitNodeCollector collector, int light,
			AvatarRenderState state, float f, float g, CallbackInfo ci) {
		if (!OverlayModule.showStuckArrows()) {
			ci.cancel();
		}
	}
}
