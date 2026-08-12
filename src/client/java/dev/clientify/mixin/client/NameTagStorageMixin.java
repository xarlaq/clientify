package dev.clientify.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.clientify.client.modules.NametagsModule;
import dev.clientify.client.modules.TotemModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Component;
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
 * faithful copy of {@code Storage.add} with our scale, plate color and see-through rules)
 * — a takeover rather than local-variable surgery, which is stable across remaps.
 */
@Mixin(NameTagFeatureRenderer.Storage.class)
public abstract class NameTagStorageMixin {
	@Shadow
	@Final
	private List<SubmitNodeStorage.NameTagSubmit> nameTagSubmitsSeethrough;
	@Shadow
	@Final
	private List<SubmitNodeStorage.NameTagSubmit> nameTagSubmitsNormal;

	/** Vanilla's translucent-white nametag text color (0x80FFFFFF). */
	private static final int TEXT_COLOR = -2130706433;

	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void clientify$nameTags(PoseStack poseStack, Vec3 attachment, int y, Component text,
			boolean notDiscrete, int light, double distanceSq, CameraRenderState camera, CallbackInfo ci) {
		// The takeover also runs when the totem counter wants a line of its own above or below the
		// name: what follows is a faithful copy of vanilla's own submission, so standing in for it
		// changes nothing except that there is somewhere to put the second line.
		Component countLine = TotemModule.nameTagLine(text);
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
		float x = -mc.font.width(text) / 2.0F;
		int vanillaPlate = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
		int plate = NametagsModule.backgroundArgb(vanillaPlate);

		int textColor = NametagsModule.textColor(TEXT_COLOR);
		// notDiscrete = vanilla's "also draw a see-through pass" — kept exactly as vanilla.
		if (notDiscrete) {
			clientify$tag(nameTagSubmitsNormal, pose, x, y, text,
					LightTexture.lightCoordsWithEmission(light, 2), NametagsModule.textColor(-1), 0,
					distanceSq);
			clientify$tag(nameTagSubmitsSeethrough, pose, x, y, text, light, textColor, plate, distanceSq);
		} else {
			clientify$tag(nameTagSubmitsNormal, pose, x, y, text, light, textColor, plate, distanceSq);
		}
		if (countLine != null) {
			nameTagSubmitsNormal.add(new SubmitNodeStorage.NameTagSubmit(pose,
					-mc.font.width(countLine) / 2.0F, y + TotemModule.nameTagLineOffset(), countLine,
					light, -1, plate, distanceSq));
		}
		poseStack.popPose();
	}

	/**
	 * Submits one nametag line, with a drop shadow under it when the module asks for one.
	 *
	 * <p>NameTagSubmit has no shadow of its own — vanilla nametags do not have one — so it is a
	 * second submission of the same text, a pixel across and down, at vanilla's own shadow colour
	 * (a quarter brightness, same alpha) and carrying no plate of its own.
	 *
	 * <p>The shadow goes in first so it lands behind. That is only strictly right when there is no
	 * plate, because a plate belonging to the line above would be drawn over it — but a dark shadow
	 * under a dark translucent plate is invisible either way, and the setting exists for the case
	 * where the plate is off.
	 */
	@Unique
	private void clientify$tag(List<SubmitNodeStorage.NameTagSubmit> into, Matrix4f pose, float x,
			float y, Component text, int light, int color, int plate, double distanceSq) {
		if (NametagsModule.textShadow()) {
			int shadow = ((color & 0xFCFCFC) >> 2) | (color & 0xFF000000);
			into.add(new SubmitNodeStorage.NameTagSubmit(pose, x + 1f, y + 1f, text, light, shadow, 0,
					distanceSq));
		}
		into.add(new SubmitNodeStorage.NameTagSubmit(pose, x, y, text, light, color, plate, distanceSq));
	}
}
