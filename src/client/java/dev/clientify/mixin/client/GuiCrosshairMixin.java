package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.clientify.client.modules.CrosshairModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Swaps the vanilla crosshair sprite draw for the Custom Crosshair module's render, keeping
 * every vanilla gate (first person, spectator rules, F3 3D crosshair) and the attack
 * indicator intact — the wrap only redirects the crosshair sprite itself.
 */
@Mixin(Gui.class)
public abstract class GuiCrosshairMixin {
	@WrapOperation(
			method = "renderCrosshair",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V")
	)
	private void clientify$customCrosshair(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite,
			int x, int y, int w, int h, Operation<Void> original) {
		if (CrosshairModule.replacesVanilla() && sprite.getPath().equals("hud/crosshair")) {
			CrosshairModule.renderCurrent(graphics);
		} else {
			original.call(graphics, pipeline, sprite, x, y, w, h);
		}
	}
}
