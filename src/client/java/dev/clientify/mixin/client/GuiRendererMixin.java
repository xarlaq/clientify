package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.clientify.client.modules.GuiScaleModule;
import net.minecraft.client.gui.render.GuiRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps items sharp in a screen the interface-scale module draws larger than the window's GUI
 * scale. Items are rasterised once per frame into a shared atlas at 16 pixels per scale step and
 * then blitted under whatever transform is in force, so a screen drawn at a larger scale would be
 * magnifying that raster.
 *
 * <p>The scale is modified where the renderer reads it rather than on the way out of the method,
 * so the value also reaches the comparison that invalidates the atlas — the cell size and the
 * positions recorded in it have to change together or items sample each other's cells.
 *
 * <p>On 1.21.11 that read was a call to Window.getGuiScale; 26.x reads the scale off the window's
 * render state instead. It is still the only read in the method, and still first.
 */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {
	@ModifyExpressionValue(method = "getGuiScaleInvalidatingItemAtlasIfChanged",
			at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
					target = "Lnet/minecraft/client/renderer/state/WindowRenderState;guiScale:I"))
	private int clientify$itemAtlasScale(int windowScale) {
		return GuiScaleModule.itemAtlasScale(windowScale);
	}
}
