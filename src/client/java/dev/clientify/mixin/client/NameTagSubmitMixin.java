package dev.clientify.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.NametagsModule;
import dev.clientify.client.modules.TotemModule;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
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
 * <p>26.2 is between the other versions: nametags are filed by SubmitNodeCollection into render
 * phases, as on 26.3, but still as NameTagFeatureRenderer submits with 1.21.11's constant text
 * colour and no shadow flag of their own.
 */
@Mixin(SubmitNodeCollection.class)
public abstract class NameTagSubmitMixin {
	@Shadow
	@Final
	public SimpleFeatureRenderPhase nameTags;
	@Shadow
	@Final
	public TranslucentFeatureRenderPhase seeThroughNameTags;

	/** Vanilla's translucent-white nametag text color (0x80FFFFFF). */
	private static final int TEXT_COLOR = -2130706433;

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
		poseStack.mulPose(camera.orientation);
		poseStack.scale(scale, -scale, scale);
		Matrix4f pose = new Matrix4f(poseStack.last().pose());
		float x = -mc.font.width(name) / 2.0F;
		int plate = NametagsModule.backgroundArgb(ARGB.color(
				mc.gameRenderer.gameRenderState().optionsRenderState.getBackgroundOpacity(0.25F), -16777216));
		int textColor = NametagsModule.textColor(TEXT_COLOR);

		// alsoSeeThrough = vanilla's "also draw a see-through pass" — kept exactly as vanilla.
		if (alsoSeeThrough) {
			clientify$tag(nameTags::submit, pose, x, offset, name,
					LightCoordsUtil.lightCoordsWithEmission(light, 2), NametagsModule.textColor(-1), 0,
					Font.DisplayMode.NORMAL);
			clientify$tag(seeThroughNameTags::submit, pose, x, offset, name, light, textColor, plate,
					Font.DisplayMode.SEE_THROUGH);
		} else {
			clientify$tag(nameTags::submit, pose, x, offset, name, light, textColor, plate,
					Font.DisplayMode.NORMAL);
		}
		if (countLine != null) {
			nameTags.submit(new NameTagFeatureRenderer.Submit(pose, -mc.font.width(countLine) / 2.0F,
					offset + TotemModule.nameTagLineOffset(), countLine, light, -1, plate, Font.DisplayMode.NORMAL));
		}
		poseStack.popPose();
	}

	/**
	 * Submits one nametag line, with a drop shadow under it when the module asks for one.
	 *
	 * <p>The nametag submit has no shadow of its own — vanilla prepares its text with the shadow off
	 * — so it is a second submission of the same text, a pixel across and down, at vanilla's own
	 * shadow colour (a quarter brightness, same alpha) and carrying no plate of its own. It goes in
	 * first so it lands behind: both phases keep submission order for submits at one distance.
	 */
	@Unique
	private static void clientify$tag(Consumer<NameTagFeatureRenderer.Submit> into, Matrix4f pose, float x,
			float y, Component text, int light, int color, int plate, Font.DisplayMode mode) {
		if (NametagsModule.textShadow()) {
			int shadow = ((color & 0xFCFCFC) >> 2) | (color & 0xFF000000);
			into.accept(new NameTagFeatureRenderer.Submit(pose, x + 1f, y + 1f, text, light, shadow, 0, mode));
		}
		into.accept(new NameTagFeatureRenderer.Submit(pose, x, y, text, light, color, plate, mode));
	}
}
