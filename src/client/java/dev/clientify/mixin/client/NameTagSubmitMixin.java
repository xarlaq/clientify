package dev.clientify.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.NametagsModule;
import dev.clientify.client.modules.TotemModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nametag tweaks. Vanilla's submission is replaced wholesale while the module is on (a
 * faithful copy of {@code submitNameTag} with our scale, plate color and see-through rules)
 * — a takeover rather than local-variable surgery, which is stable across remaps.
 *
 * <p>On 1.21.11 this was NameTagFeatureRenderer.Storage.add. 26.x folds nametags into the
 * general text feature: SubmitNodeCollection builds a text submit and files it by phase, and
 * SubmitNodeStorage only forwards here, so this one method still sees every nametag.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagSubmitMixin {
	@Shadow
	@Final
	public TranslucentFeatureRenderPhase seeThrough;

	/** Vanilla's own filing: opaque text with no plate goes in the solid phase, the rest is sorted. */
	@Shadow
	private void submitNameTagPart(TextFeatureRenderer.Submit nameTag) {
	}

	@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
	private void clientify$nameTags(PoseStack poseStack, Vec3 attachment, int offset, Component name,
			boolean alsoSeeThrough, int light, CameraRenderState camera, CallbackInfo ci) {
		// The takeover also runs when the totem counter wants a line of its own above or below the
		// name: what follows is a faithful copy of vanilla's own submission, so standing in for it
		// changes nothing except that there is somewhere to put the second line.
		Component countLine = TotemModule.nameTagLine(name);
		if (!NametagsModule.takeOver() && countLine == null) {
			return; // module off — vanilla behavior
		}
		ci.cancel();
		if (NametagsModule.hideAll() || attachment == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float scale = 0.025F * NametagsModule.scaleFactor();
		poseStack.pushPose();
		poseStack.translate(attachment.x, attachment.y + 0.5, attachment.z);
		poseStack.rotate(camera.orientation);
		poseStack.scale(scale, -scale, scale);
		Matrix4f pose = new Matrix4f(poseStack.last().pose());
		float x = -mc.font.width(name) / 2.0F;
		// 26.x derives the text's alpha from the plate's: at the default quarter-opacity plate it is
		// the half-transparent white 1.21.11 used as a constant, and it firms up as the plate does.
		float plateAlpha = mc.gameRenderer.gameRenderState().optionsRenderState.getBackgroundOpacity(0.25F);
		int plate = NametagsModule.backgroundArgb(ARGB.color(plateAlpha, -16777216));
		int textColor = NametagsModule.textColor(ARGB.color(Math.max((plateAlpha + 0.75F) * 0.5F, 0.5F), -1));
		FormattedCharSequence text = name.getVisualOrderText();

		// alsoSeeThrough = vanilla's "also draw a see-through pass" — kept exactly as vanilla.
		if (alsoSeeThrough) {
			submitNameTagPart(clientify$tag(pose, x, offset, text,
					LightCoordsUtil.lightCoordsWithEmission(light, 2), NametagsModule.textColor(-1), 0,
					Font.DisplayMode.NORMAL));
			this.seeThrough.submit(clientify$tag(pose, x, offset, text, light, textColor, plate,
					Font.DisplayMode.SEE_THROUGH));
		} else {
			submitNameTagPart(clientify$tag(pose, x, offset, text, light, textColor, plate,
					Font.DisplayMode.NORMAL));
		}
		if (countLine != null) {
			submitNameTagPart(new TextFeatureRenderer.Submit(pose, Font.DisplayMode.NORMAL, light,
					new TextFeatureRenderer.Content.Text(-mc.font.width(countLine) / 2.0F,
							offset + TotemModule.nameTagLineOffset(), countLine.getVisualOrderText(),
							false, -1, plate, 0)));
		}
		poseStack.popPose();
	}

	/**
	 * One nametag line — vanilla's private nameTag helper, plus the drop shadow the module can ask for.
	 *
	 * <p>1.21.11's NameTagSubmit had no shadow, so the shadow was a second submission of the same text
	 * a pixel across and down. 26.x submits nametags as ordinary text, which carries a shadow flag:
	 * the font draws it at the same offset in the same colour (a quarter of each channel, same alpha)
	 * and a fraction of a pixel behind the text, so it now sits correctly on top of the plate too.
	 */
	@Unique
	private static TextFeatureRenderer.Submit clientify$tag(Matrix4f pose, float x, float y,
			FormattedCharSequence text, int light, int color, int plate, Font.DisplayMode mode) {
		return new TextFeatureRenderer.Submit(pose, mode, light, new TextFeatureRenderer.Content.Text(x, y, text,
				NametagsModule.textShadow(), color, plate, 0));
	}
}
