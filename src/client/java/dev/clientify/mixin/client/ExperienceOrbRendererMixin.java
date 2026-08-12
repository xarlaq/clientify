package dev.clientify.mixin.client;

import dev.clientify.client.modules.OverlayModule;
import net.minecraft.client.renderer.entity.ExperienceOrbRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * A flat colour for experience orbs, for the Overlay module. Vanilla shimmers them through a
 * green-yellow cycle; a chosen colour replaces the whole of it, alpha included.
 */
@Mixin(ExperienceOrbRenderer.class)
public class ExperienceOrbRendererMixin {
	@ModifyArgs(method = "vertex",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(IIII)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private static void clientify$orbColor(Args args) {
		Integer color = OverlayModule.xpOrbColor();
		if (color == null) {
			return;
		}
		args.set(0, (color >> 16) & 0xFF);
		args.set(1, (color >> 8) & 0xFF);
		args.set(2, color & 0xFF);
		args.set(3, (color >>> 24) & 0xFF);
	}
}
