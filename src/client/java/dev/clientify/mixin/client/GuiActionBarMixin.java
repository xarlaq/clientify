package dev.clientify.mixin.client;

import dev.clientify.client.modules.ActionBarModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects vanilla action-bar (overlay message) rendering into the draggable Action Bar
 * module (same fade math; module handles position/scale/font/chrome).
 */
@Mixin(Hud.class)
public abstract class GuiActionBarMixin {
	@Shadow
	private @Nullable Component overlayMessageString;
	@Shadow
	private int overlayMessageTime;
	@Shadow
	private boolean animateOverlayMessageColor;

	@Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void clientify$actionBarTweaks(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		ActionBarModule.Settings s = ActionBarModule.active();
		if (s == null) {
			return;
		}
		ci.cancel();
		if (s.hide || this.overlayMessageString == null || this.overlayMessageTime <= 0) {
			return;
		}
		float f = this.overlayMessageTime - deltaTracker.getGameTimeDeltaPartialTick(false);
		int alpha = Math.min(255, (int) (f * 255.0F / 20.0F));
		if (alpha <= 0) {
			return;
		}
		g.nextStratum();
		ActionBarModule m = ActionBarModule.get();
		if (m != null) {
			int animated = this.animateOverlayMessageColor ? Mth.hsvToArgb(f / 50.0F, 0.7F, 0.6F, alpha) : 0;
			m.renderMessage(g, this.overlayMessageString, alpha, this.animateOverlayMessageColor, animated);
		}
	}
}
