package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.WaypointsModule;
import dev.clientify.client.modules.ZoomModule;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Zoom on 26.x, where the camera works out both fields of view itself.
 *
 * <p>On 1.21.11 GameRenderer had one getFov with a flag: true for the world, false for the hand at
 * a flat seventy, and the hand's value only reached us as an argument to a projection buffer. 26.x
 * splits them - calculateFov is the world, calculateHudFov the hand and the other things drawn in
 * the HUD's own 3D projection - so each gets a return hook and neither needs the flag or the buffer.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float clientify$zoomFov(float fov) {
		float zoomed = ZoomModule.modifyFov(fov);
		// Whatever ends up here IS the world FOV, so waypoint labels scale with any zoom mod
		// rather than only ours.
		WaypointsModule.captureFov(zoomed);
		return zoomed;
	}

	/**
	 * The hand's field of view - a flat seventy until death or water bends it, and never the FOV
	 * setting, which is why the held item does not grow when the setting drops and why zoom
	 * leaves it alone unless Zoom Hand asks otherwise. GameRenderer builds the hand's projection
	 * straight from this value.
	 */
	@ModifyReturnValue(method = "calculateHudFov", at = @At("RETURN"))
	private float clientify$handFov(float fov) {
		return ZoomModule.modifyHandFov(fov);
	}
}
