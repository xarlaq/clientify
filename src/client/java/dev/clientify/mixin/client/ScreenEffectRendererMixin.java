package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.OverlayModule;
import dev.clientify.client.modules.TotemModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lowers the burning overlay for the Overlay module. Vanilla plants the two fire quads at a fixed
 * height in front of the camera; pushing that down is the difference between fighting while on fire
 * and fighting a wall of orange.
 *
 * <p>The setting reads as "how much of it you see", so 1 leaves vanilla alone and 0 skips the draw
 * outright rather than pushing it just far enough to hope it is off screen.
 */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {
	/** Far enough below the camera that nothing shows, at any field of view. */
	private static final float CLEAR_OF_VIEW = 1.6f;

	@Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
	private static void clientify$skipFire(com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.MultiBufferSource bufferSource,
			net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, CallbackInfo ci) {
		if (OverlayModule.fireOverlay() <= 0f) {
			ci.cancel();
		}
	}

	@ModifyArg(method = "renderFire",
			at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
			index = 1)
	private static float clientify$fireHeight(float y) {
		return y - (1f - OverlayModule.fireOverlay()) * CLEAR_OF_VIEW;
	}

	// ---- the totem animation ----
	//
	// Vanilla's is a fixed piece of choreography: a wobble out from the middle, a spin of 900
	// degrees, two shivers, over forty ticks. Rather than rewrite it, each part of that is caught
	// where it happens, so everything vanilla does about lighting, fading and submitting the item
	// still happens exactly as it did.

	@Shadow
	private int itemActivationTicks;

	@Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
	private void clientify$hideTotem(PoseStack pose, float partialTick, SubmitNodeCollector collector,
			CallbackInfo ci) {
		if (TotemModule.hidden()) {
			ci.cancel();
		}
	}

	/**
	 * Stretches the animation over the chosen time.
	 *
	 * <p>The countdown is started longer in {@link #clientify$totemLength} and read back divided,
	 * so vanilla's curve still runs from nought to one over what it thinks are forty ticks. Slowing
	 * the countdown itself instead would leave the partial tick sweeping a whole tick each frame,
	 * and the totem would judder rather than drift.
	 */
	@ModifyExpressionValue(method = "renderItemActivationAnimation",
			at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
					target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;itemActivationTicks:I"))
	private int clientify$totemProgress(int ticks) {
		float factor = TotemModule.durationFactor();
		return factor == 1f ? ticks : Math.round(ticks / factor);
	}

	@Inject(method = "displayItemActivation", at = @At("RETURN"))
	private void clientify$totemLength(ItemStack stack, RandomSource random, CallbackInfo ci) {
		float factor = TotemModule.durationFactor();
		if (factor != 1f) {
			itemActivationTicks = Math.max(1, Math.round(itemActivationTicks * factor));
		}
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

	@WrapOperation(method = "renderItemActivationAnimation",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void clientify$totemSpin(PoseStack pose, Quaternionfc rotation, Operation<Void> original) {
		// All three turns are skipped together: the spin, and the two shivers riding on it.
		if (!TotemModule.rotationLocked()) {
			original.call(pose, rotation);
		}
	}
}
