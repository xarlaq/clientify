package dev.clientify.mixin.client;

import dev.clientify.client.modules.HitboxModule;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer;
import net.minecraft.util.debug.DebugValueAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla's F3+B entity hitbox boxes while the Hitbox module is on, so our
 * custom boxes replace them (rather than doubling up). When the module is off, vanilla's
 * boxes render normally.
 */
@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
	@Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
	private void clientify$replaceVanillaHitboxes(double camX, double camY, double camZ,
			DebugValueAccess values, Frustum frustum, float partialTick, CallbackInfo ci) {
		if (HitboxModule.replacesVanilla()) {
			ci.cancel();
		}
	}
}
