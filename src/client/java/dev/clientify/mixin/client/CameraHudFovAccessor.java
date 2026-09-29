package dev.clientify.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The field of view the HUD's 3D elements are drawn through - the hand, and the totem pop.
 *
 * <p>Replaces an invoker on 1.21.11's {@code GameRenderer.getFov(camera, partialTick, false)}. On 26.x
 * the camera computes this once a frame, from the same seventy-degree base with the same death and
 * fluid adjustments, and keeps it; reading the field gives the value that frame is really drawn with.
 */
@Mixin(Camera.class)
public interface CameraHudFovAccessor {
	@Accessor("hudFov")
	float clientify$hudFov();
}
