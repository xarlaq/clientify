package dev.clientify.mixin.client;

import dev.clientify.client.modules.TitleModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
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
 * Redirects vanilla title rendering into the draggable Title module (same fade math; the
 * module handles position, scale, font, chrome and opacity — or disables titles).
 */
@Mixin(Gui.class)
public abstract class GuiTitleMixin {
	@Shadow
	private int titleTime;
	@Shadow
	private @Nullable Component title;
	@Shadow
	private @Nullable Component subtitle;
	@Shadow
	private int titleFadeInTime;
	@Shadow
	private int titleStayTime;
	@Shadow
	private int titleFadeOutTime;

	@Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
	private void clientify$titleTweaks(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
		TitleModule.Settings s = TitleModule.active();
		if (s == null) {
			return;
		}
		ci.cancel();
		if (s.disableTitles || this.title == null || this.titleTime <= 0) {
			return;
		}
		float f = this.titleTime - deltaTracker.getGameTimeDeltaPartialTick(false);
		int alpha = 255;
		if (this.titleTime > this.titleFadeOutTime + this.titleStayTime) {
			float fadeIn = this.titleFadeInTime + this.titleStayTime + this.titleFadeOutTime - f;
			alpha = (int) (fadeIn * 255.0F / this.titleFadeInTime);
		}
		if (this.titleTime <= this.titleFadeOutTime) {
			alpha = (int) (f * 255.0F / this.titleFadeOutTime);
		}
		alpha = Mth.clamp(alpha, 0, 255);
		if (alpha <= 0) {
			return;
		}
		g.nextStratum();
		TitleModule m = TitleModule.get();
		if (m != null) {
			m.renderTitle(g, this.title, this.subtitle, alpha);
		}
	}
}
