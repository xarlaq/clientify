package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.clientify.client.modules.OverlayModule;
import dev.clientify.client.modules.TotemModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lowers the burning overlay for the Overlay module, and takes apart the totem animation for Totem
 * Tweaks. Vanilla plants the two fire quads at a fixed height in front of the camera; pushing that
 * down is the difference between fighting while on fire and fighting a wall of orange.
 *
 * <p>The fire setting reads as "how much of it you see", so 1 leaves vanilla alone and 0 skips the
 * draw outright rather than pushing it just far enough to hope it is off screen.
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
	/** Far enough below the camera that nothing shows, at any field of view. */
	private static final float CLEAR_OF_VIEW = 1.6f;

	/**
	 * Skips or lowers the fire, around the call rather than inside it.
	 *
	 * <p>On 26.x the fire quads are built in a lambda from a copy of the pose, so there is no
	 * PoseStack.translate left in submitFire to adjust. Lowering the stack for the length of the call
	 * does the same thing - submitCustomGeometry copies the pose as it is submitted - and doing it in
	 * one wrap keeps the push and the pop in the same scope. Split across a HEAD and a RETURN hook, a
	 * skipped fire would never push and could still pop.
	 */
	@WrapOperation(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire("
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
							+ "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
	private void clientify$fire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite,
			Operation<Void> original) {
		float shown = OverlayModule.fireOverlay();
		if (shown <= 0f) {
			return;
		}
		if (shown >= 1f) {
			original.call(poseStack, collector, sprite);
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0f, -(1f - shown) * CLEAR_OF_VIEW, 0f);
		original.call(poseStack, collector, sprite);
		poseStack.popPose();
	}

	// ---- the totem animation ----
	//
	// Vanilla's is a fixed piece of choreography: a wobble out from the middle, a spin of 900
	// degrees, two shivers, over forty ticks. Rather than rewrite it, each part of that is caught
	// where it happens, so everything vanilla does about lighting, fading and submitting the item
	// still happens exactly as it did. The countdown itself lives in ItemActivation on 26.x - see
	// ItemActivationMixin for the half of the timing that happens there.

	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
	private void clientify$hideTotem(PlayerRenderState playerRenderState, PoseStack pose, float partialTick,
			SubmitNodeCollector collector, CallbackInfo ci) {
		if (TotemModule.hidden()) {
			ci.cancel();
		}
	}

	/**
	 * Stretches the animation over the chosen time.
	 *
	 * <p>The countdown is started longer in ItemActivationMixin and read back divided here, so
	 * vanilla's curve still runs from nought to one over what it thinks are forty ticks. Slowing the
	 * countdown itself instead would leave the partial tick sweeping a whole tick each frame, and the
	 * totem would judder rather than drift.
	 */
	@ModifyExpressionValue(method = "renderItemActivationAnimation",
			at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
					target = "Lnet/minecraft/client/renderer/state/level/PlayerRenderState$ItemActivationRenderState;ticks:I"))
	private int clientify$totemProgress(int ticks) {
		float factor = TotemModule.durationFactor();
		return factor == 1f ? ticks : Math.round(ticks / factor);
	}

	@WrapOperation(method = "renderItemActivationAnimation",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
	private void clientify$totemPlace(PoseStack pose, float x, float y, float z, Operation<Void> original) {
		// Locked drops vanilla's throw out from the middle; where the module's box sits in the
		// editor is added on top. The z here is how far out the totem is this frame, and the module
		// needs it: this is a perspective, so what an offset is worth on screen depends on it.
		float wobbleX = TotemModule.positionLocked() ? 0f : x;
		float wobbleY = TotemModule.positionLocked() ? 0f : y;
		float[] offset = TotemModule.offset(Minecraft.getInstance(), Math.max(0.05f, Math.abs(z)));
		if (offset != null) {
			wobbleX += offset[0];
			wobbleY += offset[1];
		}
		original.call(pose, wobbleX, wobbleY, z);
	}

	@WrapOperation(method = "renderItemActivationAnimation",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
	private void clientify$totemSize(PoseStack pose, float x, float y, float z, Operation<Void> original) {
		float scale = TotemModule.scale();
		original.call(pose, x * scale, y * scale, z * scale);
	}

	/**
	 * All three turns are skipped together: the spin, and the two shivers riding on it. 26.x makes
	 * them three rotateDegrees calls where 1.21.11 used mulPose with quaternions; this wraps each.
	 */
	@WrapOperation(method = "renderItemActivationAnimation",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"))
	private void clientify$totemSpin(PoseStack pose, Axis axis, float degrees, Operation<Void> original) {
		if (!TotemModule.rotationLocked()) {
			original.call(pose, axis, degrees);
		}
	}
}
