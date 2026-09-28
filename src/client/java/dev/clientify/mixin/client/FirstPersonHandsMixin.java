package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.OverlayModule;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drops a raised shield out of the way, for the Overlay module. The hand goes with it, since a
 * shield floating away from the arm holding it looks worse than the shield being in the way.
 *
 * <p>Pushed and popped around vanilla's own draw rather than translating in place, so the shift
 * cannot leak into whatever is rendered next.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
	/** Far enough down that nothing of the shield is left in view. */
	private static final float CLEAR_OF_VIEW = 1.2f;

	/**
	 * One wrapper around the whole draw, rather than a push at the head and a pop at the return.
	 *
	 * <p>{@code @At("RETURN")} binds to the return instructions of the ORIGINAL method. Another mod
	 * cancelling this — which anything that hides the first-person hand does — returns through its
	 * own callback instead, so the pop never ran and the pose stack was left one deep. Wrapping the
	 * method puts the push and the pop outside every other injection, and the finally puts them
	 * outside a thrown exception too, so they cannot come apart.
	 */
	@WrapMethod(method = "renderArmWithItem")
	private void clientify$lowerShield(AbstractClientPlayer player, float partialTick, float pitch,
			InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
			SubmitNodeCollector collector, int light, Operation<Void> original) {
		poseStack.pushPose();
		try {
			// The setting reads as "how much of it you see", so 1 leaves vanilla alone.
			float shown = OverlayModule.shieldHeight();
			if (shown < 1f && stack.getUseAnimation() == ItemUseAnimation.BLOCK) {
				poseStack.translate(0f, -(1f - shown) * CLEAR_OF_VIEW, 0f);
			}
			original.call(player, partialTick, pitch, hand, swing, stack, equip, poseStack,
					collector, light);
		} finally {
			poseStack.popPose();
		}
	}

	/**
	 * Resizes the off-hand item — a shield or a totem at vanilla's size covers a corner of the
	 * screen you would rather see through.
	 *
	 * <p>Wrapped around the item draw rather than added to the transform above: by here the arm's
	 * own transforms have all been applied, so the item scales where it stands instead of the whole
	 * hand sliding toward the middle of the screen.
	 *
	 * <p>The hand comes from the enclosing method rather than off the display context, which says
	 * left or right — and which of those is the off hand depends on the main hand setting.
	 */
	@WrapOperation(method = "renderArmWithItem",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem("
							+ "Lnet/minecraft/world/entity/LivingEntity;"
							+ "Lnet/minecraft/world/item/ItemStack;"
							+ "Lnet/minecraft/world/item/ItemDisplayContext;"
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
	private void clientify$offHandSize(ItemInHandRenderer self, LivingEntity entity, ItemStack held,
			ItemDisplayContext context, PoseStack poseStack, SubmitNodeCollector collector, int light,
			Operation<Void> original, @Local(argsOnly = true) InteractionHand hand) {
		float scale = hand == InteractionHand.OFF_HAND ? OverlayModule.offHandScale() : 1f;
		if (scale == 1f) {
			original.call(self, entity, held, context, poseStack, collector, light);
			return;
		}
		poseStack.pushPose();
		poseStack.scale(scale, scale, scale);
		original.call(self, entity, held, context, poseStack, collector, light);
		poseStack.popPose();
	}
}
