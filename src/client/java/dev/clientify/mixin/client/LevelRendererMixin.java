package dev.clientify.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.clientify.client.modules.WaypointsModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the camera position and world-to-clip matrix used for waypoint projection.
 *
 * <p>GameRenderer calls this with THREE matrices: the camera rotation, the real projection,
 * and a separate culling projection built as {@code max(fov, optionsFov)} — the culling one
 * deliberately never narrows when you zoom, so it must not be used here.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void clientify$captureFrame(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
			boolean renderBlockOutline, Camera camera, Matrix4f cameraRotation, Matrix4f projection,
			Matrix4f cullingProjection, GpuBufferSlice fogBuffer, Vector4f fogColor, boolean sky,
			CallbackInfo ci) {
		WaypointsModule.captureFrame(camera.position(), projection, cameraRotation);
	}
}
