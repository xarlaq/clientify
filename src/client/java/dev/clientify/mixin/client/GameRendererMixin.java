package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.OverlayModule;
import dev.clientify.client.modules.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** One step of the zoom ramp per rendered frame — see {@link ZoomModule#advanceFrame()}. */
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void clientify$advanceZoom(DeltaTracker deltaTracker, CallbackInfo ci) {
		ZoomModule.advanceFrame();
	}

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void clientify$zoomFov(Camera camera, float partialTick, boolean useFovSetting,
			CallbackInfoReturnable<Float> cir) {
		if (useFovSetting) { // world FOV only; leave the hand renderer alone
			float fov = ZoomModule.modifyFov(cir.getReturnValueF());
			cir.setReturnValue(fov);
			// Whatever ends up here IS the world FOV, so waypoint labels can scale with any
			// zoom mod rather than only ours.
			dev.clientify.client.modules.WaypointsModule.captureFov(fov);
		}
	}

	/**
	 * The hand's own projection. Vanilla builds it from {@code getFov(camera, partialTick, false)},
	 * which ignores the FOV setting entirely and returns a flat 70 — that is why the held item does
	 * not grow when you lower your FOV, and why our zoom leaves it alone unless asked.
	 *
	 * <p>Anchored on the buffer call rather than on a {@code getFov} ordinal: this invocation is
	 * unique in the class, so another mod adding an FOV lookup to renderLevel cannot shift it.
	 */
	@ModifyArg(method = "renderLevel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/CachedPerspectiveProjectionMatrixBuffer;"
							+ "getBuffer(IIF)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"),
			index = 2)
	private float clientify$handFov(float fov) {
		return ZoomModule.modifyHandFov(fov);
	}

	/**
	 * Minimal view bobbing: the camera sits out the walk bob while the held item keeps its own.
	 * Vanilla calls the same method for both, so only the world's call is wrapped — the hand's
	 * lives in renderItemInHand and is left alone.
	 */
	@WrapOperation(method = "renderLevel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"))
	private void clientify$minimalBob(GameRenderer renderer, PoseStack poseStack, float partialTick,
			Operation<Void> original) {
		if (!OverlayModule.minimalViewBobbing()) {
			original.call(renderer, poseStack, partialTick);
		}
	}
}
