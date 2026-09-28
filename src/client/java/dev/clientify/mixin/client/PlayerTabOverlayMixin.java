package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.clientify.client.modules.TabModule;
import dev.clientify.client.modules.TotemModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tab list tweaks: header/footer suppression (field-read overrides), head hiding (skip the
 * face draw), ping as icons/numeric/hidden, and a scale + vertical offset pose transform.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

	/**
	 * Writes totem counts into the tab list for the Totem Counter. One method decides what a row
	 * says, so decorating its answer reaches every row without touching the layout.
	 */
	@ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
	private Component clientify$totemCounts(Component original, PlayerInfo info) {
		Component decorated = TotemModule.decorateTabName(info, original);
		return decorated == null ? original : decorated;
	}

	/**
	 * One wrapper around the list, rather than a push at the head and a pop at the return.
	 *
	 * <p>{@code @At("RETURN")} binds to the original method's returns, so a mod cancelling the tab
	 * list left the matrix pushed and {@code TabModule.beginFrame} never closed — and since the
	 * flag stayed set, the next frame pushed again on top of it. The finally closes both whatever
	 * happens.
	 */
	@WrapMethod(method = "extractRenderState")
	private void clientify$tabList(GuiGraphicsExtractor g, int width, Scoreboard scoreboard,
			@Nullable Objective objective, Operation<Void> original) {
		TabModule.Settings s = TabModule.active();
		TabModule m = TabModule.get();
		boolean pushed = false;
		if (s != null && m != null) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			var r = m.bounds(mc, g.guiWidth(), g.guiHeight());
			// Map vanilla's (width/2, 10) onto the module rect's top-center.
			float tx = r.x() + r.w() / 2f - s.scale * width / 2f;
			float ty = r.y() - s.scale * 10;
			TabModule.setTransform(tx, ty, s.scale);
			g.pose().pushMatrix();
			g.pose().translate(tx, ty);
			g.pose().scale(s.scale, s.scale);
			pushed = true;
			// Inside the same transform, so the panel lines up with the list's own fills.
			TabModule.beginFrame(g);
		}
		try {
			original.call(g, width, scoreboard, objective);
		} finally {
			if (pushed) {
				TabModule.endFrame();
				g.pose().popMatrix();
			}
		}
	}

	@ModifyExpressionValue(method = "extractRenderState",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;"))
	private Component clientify$hideHeader(Component original) {
		TabModule.Settings s = TabModule.active();
		return s != null && s.hideHeader ? null : original;
	}

	@ModifyExpressionValue(method = "extractRenderState",
			at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;"))
	private Component clientify$hideFooter(Component original) {
		TabModule.Settings s = TabModule.active();
		return s != null && s.hideFooter ? null : original;
	}

	@WrapOperation(method = "extractRenderState",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/PlayerFaceExtractor;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V"))
	private void clientify$hideHeads(GuiGraphicsExtractor g, Identifier skin, int x, int y, int size,
			boolean hat, boolean upsideDown, int tint, Operation<Void> original) {
		TabModule.Settings s = TabModule.active();
		if (s == null || !s.hideHeads) {
			original.call(g, skin, x, y, size, hat, upsideDown, tint);
		}
	}

	/** Recolors the tab list's own fills: panel background vs per-player row strips. */
	@WrapOperation(method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void clientify$tabFills(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color,
			Operation<Void> original) {
		if (!TabModule.drawFill(g, x0, y0, x1, y1, color)) {
			original.call(g, x0, y0, x1, y1, color);
		}
	}

	@Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true)
	private void clientify$pingTweaks(GuiGraphicsExtractor g, int cellWidth, int x, int y, PlayerInfo info,
			CallbackInfo ci) {
		TabModule.Settings s = TabModule.active();
		if (s == null || s.ping == TabModule.PingMode.ICONS) {
			return;
		}
		ci.cancel();
		if (s.ping == TabModule.PingMode.NUMERIC) {
			var mc = net.minecraft.client.Minecraft.getInstance();
			String text = info.getLatency() < 0 ? "?" : Integer.toString(info.getLatency());
			int tw = mc.font.width(text);
			g.pose().pushMatrix();
			g.pose().translate(x + cellWidth - tw * 0.75f, y + 1);
			g.pose().scale(0.75f, 0.75f);
			g.text(mc.font, text, 0, 0, TabModule.pingColor(info.getLatency()), false);
			g.pose().popMatrix();
		}
	}
}
