package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.OverlayModule;
import dev.clientify.client.util.HookedOnSelf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fades a fishing bobber for the Overlay module — in particular the one somebody else has hooked
 * into you, which parks itself on your face and stays there.
 *
 * <p>Three things make that awkward. The hook does not say what it caught, so an accessor reads
 * vanilla's private field; the render state carries no entity, so the answer is recorded during
 * extraction and read back at draw time; and vanilla draws the bobber on a CUTOUT render type,
 * where alpha is a yes-or-no test rather than a blend, so fading needs a translucent type instead.
 *
 * <p>The opacity for the bobber being drawn is parked in a field between submit and its return,
 * because the vertex colour is set in a static helper that never sees which hook it belongs to.
 */
@Mixin(FishingHookRenderer.class)
public class FishingHookRendererMixin {
	private static final Identifier HOOK_TEXTURE =
			Identifier.withDefaultNamespace("textures/entity/fishing_hook.png");

	/** Opacity for the bobber being DRAWN — set only while the deferred geometry callback runs. */
	@Unique
	private static float clientify$opacity = 1f;
	/** Opacity worked out for the bobber being submitted, for the callback to capture. */
	@Unique
	private static float clientify$pending = 1f;

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void clientify$markHookedTarget(FishingHook hook, FishingHookRenderState state,
			float partialTick, CallbackInfo ci) {
		if (state instanceof HookedOnSelf marker) {
			marker.clientify$setOnSelf(hook instanceof FishingHookAccessor accessor
					&& accessor.clientify$hookedIn() == Minecraft.getInstance().player);
		}
	}

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void clientify$beginHook(FishingHookRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState cameraState, CallbackInfo ci) {
		boolean onSelf = state instanceof HookedOnSelf marker && marker.clientify$onSelf();
		clientify$pending = OverlayModule.hookOpacity(onSelf);
		if (clientify$pending <= 0f) {
			// Cancelled whole rather than drawn invisible, so the line goes with the bobber —
			// a string running to nothing is worse than the bobber was.
			clientify$pending = 1f;
			ci.cancel();
		}
	}

	/**
	 * Carries this bobber's opacity into the draw.
	 *
	 * <p>The geometry is handed over as a callback and run later, in the draw pass, so a value
	 * parked in a field during submit is long gone by the time the vertex colours are set — which
	 * is exactly why fading did nothing before. Capturing it in a wrapper around the callback ties
	 * the value to the bobber it was worked out for, however many are on screen.
	 */
	@ModifyArg(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitCustomGeometry("
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lnet/minecraft/client/renderer/rendertype/RenderType;"
							+ "Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;)V"),
			index = 2)
	private SubmitNodeCollector.CustomGeometryRenderer clientify$fadeGeometry(
			SubmitNodeCollector.CustomGeometryRenderer original) {
		float opacity = clientify$pending;
		if (opacity >= 1f) {
			return original;
		}
		return (pose, consumer) -> {
			clientify$opacity = opacity;
			original.render(pose, consumer);
			clientify$opacity = 1f;
		};
	}

	@ModifyExpressionValue(method = "submit",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/client/renderer/entity/FishingHookRenderer;RENDER_TYPE:"
							+ "Lnet/minecraft/client/renderer/rendertype/RenderType;"))
	private RenderType clientify$blendableHook(RenderType original) {
		return clientify$pending >= 1f ? original : RenderTypes.entityTranslucent(HOOK_TEXTURE);
	}

	// The bobber's colour is set in the private vertex helper, not in submit — submit only hands
	// the geometry over as a lambda.
	@ModifyArg(method = "vertex",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private static int clientify$hookOpacity(int color) {
		return clientify$opacity >= 1f ? color
				: ARGB.color(Math.round(255 * clientify$opacity), color & 0xFFFFFF);
	}

	@ModifyArg(method = "stringVertex",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private static int clientify$lineColor(int color) {
		Integer custom = OverlayModule.fishingLineColor();
		return custom == null ? color : custom;
	}

	@ModifyArg(method = "stringVertex",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setLineWidth(F)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private static float clientify$lineThickness(float width) {
		return width * OverlayModule.fishingLineThickness();
	}
}
