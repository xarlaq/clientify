package dev.clientify.mixin.client;

import dev.clientify.client.modules.HitboxModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tail of vanilla gizmo emission: the thread-local GizmoCollector is armed here, so the
 * hitbox module can emit cuboids that render exactly like F3+B boxes (depth-tested).
 */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
	@Inject(method = "emitGizmos", at = @At("TAIL"))
	private void clientify$hitboxes(Frustum frustum, double camX, double camY, double camZ, float partialTick,
			CallbackInfo ci) {
		HitboxModule.emitGizmos(frustum, camX, camY, camZ, partialTick);
		dev.clientify.client.modules.WaypointsModule.emitGizmos();

	}
}
