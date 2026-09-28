package dev.clientify.mixin.client;

import dev.clientify.client.modules.OverlayModule;
import dev.clientify.client.modules.TotemModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides arrows that have already landed, for the Overlay module — a heavily shot-at floor is a
 * wall of them otherwise.
 *
 * <p>On the base renderer rather than ArrowRenderer, which inherits this method without declaring
 * it: a mixin can only inject into a method the target class actually has, and an injection with
 * nothing to bind to fails quietly.
 *
 * <p>Judged by whether the arrow has stopped moving rather than by its stuck flag, which the
 * client's copy does not carry — the same test the hitbox module already uses.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void clientify$hideGroundArrows(Entity entity, Frustum frustum, double x, double y, double z,
			float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof AbstractArrow arrow && !OverlayModule.showGroundArrows()
				&& arrow.getDeltaMovement().lengthSqr() < 1.0E-5) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * Drops the flames drawn on everyone but you, for the Overlay module.
	 *
	 * <p>Cleared on the render state rather than by skipping the draw: this is the one place the
	 * flag is written ({@code state.displayFireAnimation = entity.displayFireAnimation()}), so
	 * anything that reads it downstream agrees, and nothing has to know how the flame is drawn.
	 *
	 * <p>At TAIL, after the base renderer has assigned it. Subclasses reach this through super and
	 * none of them touch the flag afterwards, so the value set here is the one that survives.
	 *
	 * <p>The camera entity is spared: in first person your own burning is the screen overlay, and
	 * in third person seeing yourself alight is how you know to move.
	 */
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void clientify$hideOthersFire(Entity entity, EntityRenderState state, float partialTick,
			CallbackInfo ci) {
		if (entity == Minecraft.getInstance().getCameraEntity()) {
			return;
		}
		boolean hide = entity instanceof Player ? OverlayModule.hideFirePlayers()
				: OverlayModule.hideFireMobs();
		if (hide) {
			state.displayFireAnimation = false;
		}
	}

	/**
	 * Writes a player's totem count into their nametag for the Totem Counter.
	 *
	 * <p>On the one method that decides what a nametag says, rather than on the several places that
	 * draw one: every path through to a rendered tag comes back here first, including the Nametags
	 * module's own takeover.
	 */
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void clientify$totemNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
		if (entity instanceof Player player) {
			Component decorated = TotemModule.decorateNameTag(player, cir.getReturnValue());
			if (decorated != null) {
				cir.setReturnValue(decorated);
			}
		}
	}
}
