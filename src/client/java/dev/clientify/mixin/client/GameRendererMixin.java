package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.OverlayModule;
import dev.clientify.client.modules.WaypointsModule;
import dev.clientify.client.modules.ZoomModule;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix4f;

/**
 * Per-frame hooks on the game renderer. The two field-of-view hooks that lived here on 1.21.11 are
 * in {@link CameraMixin} now, because 26.x moved that calculation into the camera.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** One step of the zoom ramp per rendered frame - see {@link ZoomModule#advanceFrame()}. */
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void clientify$advanceZoom(CallbackInfo ci) {
		ZoomModule.advanceFrame();
	}

	/**
	 * The matrices waypoint labels are projected with, captured as the world is about to be drawn.
	 *
	 * <p>Taken here, not from the camera's render state, because 26.x multiplies the walk bob and the
	 * nausea and portal wobble into a copy of the camera's projection inside this method. The camera's
	 * own matrix is pre-bob, and labels projected with it would slide against the world as you walk.
	 * On 1.21.11 LevelRenderer.renderLevel received the finished matrix as an argument; this is the
	 * same matrix at the same moment. There is exactly one Matrix4f and one CameraRenderState local.
	 *
	 * <p>26.2's LevelRenderer.render still takes the delta tracker and the view-rotation matrix,
	 * which 26.3 dropped; the call is the same one, at the same point.
	 */
	@Inject(method = "renderLevel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/LevelRenderer;render("
							+ "Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;"
							+ "Lnet/minecraft/client/DeltaTracker;Z"
							+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
							+ "Lorg/joml/Matrix4fc;"
							+ "Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
	private void clientify$captureFrame(CallbackInfo ci, @Local Matrix4f projectionMatrix,
			@Local CameraRenderState cameraState) {
		WaypointsModule.captureFrame(cameraState.pos, projectionMatrix, cameraState.viewRotationMatrix);
	}

	/**
	 * Minimal view bobbing: the camera sits out the walk bob while the held item keeps its own.
	 * Vanilla calls bobView for both, so only the world's call is wrapped - the hand's lives in
	 * renderItemInHand and is left alone, the same split 1.21.11 had.
	 */
	@WrapOperation(method = "renderLevel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/GameRenderer;bobView("
							+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;"
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
	private void clientify$minimalBob(GameRenderer renderer, CameraRenderState cameraState,
			PoseStack poseStack, Operation<Void> original) {
		if (!OverlayModule.minimalViewBobbing()) {
			original.call(renderer, cameraState, poseStack);
		}
	}
}
