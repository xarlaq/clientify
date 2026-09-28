package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.modules.BossBarModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-anchors vanilla boss bar rendering to the Boss Bar module's draggable position and
 * scale (vanilla draws centered at guiWidth/2 starting at y = 12), draws our chip backdrop
 * underneath, and applies the hide / max-bars / hide-names options.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Unique
	private int clientify$barIndex;

	/**
	 * One wrapper around the bars, rather than a push at the head and a pop at the return.
	 *
	 * <p>{@code @At("RETURN")} binds to the original method's returns, so a mod cancelling the boss
	 * bars left the matrix pushed with the flag still set — and the next frame pushed another on
	 * top of it, once per frame, without end. The finally cannot be skipped.
	 */
	@WrapMethod(method = "extractRenderState")
	private void clientify$bossBars(GuiGraphicsExtractor g, Operation<Void> original) {
		clientify$barIndex = 0;
		BossBarModule.Settings s = BossBarModule.active();
		if (s == null) {
			original.call(g);
			return;
		}
		if (s.hide) {
			return; // hidden outright: vanilla never draws
		}
		Minecraft mc = Minecraft.getInstance();
		BossBarModule m = BossBarModule.get();
		if (m == null) {
			original.call(g);
			return;
		}
		HudModule.Rect r = m.bounds(mc, g.guiWidth(), g.guiHeight());
		g.pose().pushMatrix();
		try {
			// Map vanilla's (guiWidth/2, 12) onto the module rect's top-center.
			// Align the bar name's top with the module rect top (vanilla name sits at y = 3).
			g.pose().translate(r.x() + r.w() / 2f - s.scale * g.guiWidth() / 2f,
					r.y() - s.scale * 3 + s.scale);
			g.pose().scale(s.scale, s.scale);
			original.call(g);
		} finally {
			g.pose().popMatrix();
		}
	}

	@WrapOperation(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V"))
	private void clientify$limitBars(BossHealthOverlay self, GuiGraphicsExtractor g, int x, int y, BossEvent event,
			Operation<Void> original) {
		if (!BossBarModule.overLimit(clientify$barIndex) && !BossBarModule.hideBar()) {
			original.call(self, g, x, y, event);
		}
		clientify$barIndex++;
	}

	/** Recolors the bar sprites by swapping in the tinted blitSprite overload. */
	@WrapOperation(method = "extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/Identifier;[Lnet/minecraft/resources/Identifier;)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
	private void clientify$barColor(GuiGraphicsExtractor g, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
			net.minecraft.resources.Identifier sprite, int texW, int texH, int u, int v, int x, int y,
			int w, int h, Operation<Void> original) {
		int tint = BossBarModule.barTint();
		if (tint != -1) {
			g.blitSprite(pipeline, sprite, texW, texH, u, v, x, y, w, h, tint);
		} else {
			original.call(g, pipeline, sprite, texW, texH, u, v, x, y, w, h);
		}
	}

	@WrapOperation(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
	private void clientify$barName(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int color,
			Operation<Void> original) {
		if (BossBarModule.hideText() || BossBarModule.overLimit(clientify$barIndex - 1)) {
			return;
		}
		original.call(g, font, text, x, y, color);
	}

}
