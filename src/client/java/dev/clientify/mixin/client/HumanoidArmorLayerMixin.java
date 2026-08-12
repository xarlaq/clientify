package dev.clientify.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.HitColorModule;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arms the wearer's hurt overlay around armor submission (armor-flash option). */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
	@Inject(method = "submit", at = @At("HEAD"))
	private void clientify$armArmorFlash(PoseStack poseStack, SubmitNodeCollector collector, int light,
			HumanoidRenderState state, float f, float g, CallbackInfo ci) {
		if (HitColorModule.armorFlashEnabled() && state.hasRedOverlay) {
			HitColorModule.setArmorOverlay(LivingEntityRenderer.getOverlayCoords(state, 0.0F));
		}
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void clientify$disarmArmorFlash(PoseStack poseStack, SubmitNodeCollector collector, int light,
			HumanoidRenderState state, float f, float g, CallbackInfo ci) {
		HitColorModule.clearArmorOverlay();
	}

	/**
	 * Skips a piece the Overlay module is hiding. Per piece rather than per layer, since vanilla
	 * draws all four from one method and the point is to hide some and keep others.
	 */
	@Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
	private void clientify$hideArmorPiece(PoseStack poseStack, SubmitNodeCollector collector,
			net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot,
			int light, HumanoidRenderState state, CallbackInfo ci) {
		if (dev.clientify.client.modules.OverlayModule.hideArmor(slot, state)) {
			ci.cancel();
		}
	}
}
