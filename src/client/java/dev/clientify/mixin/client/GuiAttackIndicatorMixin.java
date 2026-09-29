package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.clientify.client.modules.AttackIndicatorModule;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stands vanilla's attack indicator down while the module is on, since the module then draws every
 * style itself — including vanilla's own two, so that they can be coloured and moved like the
 * drawn one.
 *
 * <p>The setting is overridden where vanilla READS it rather than by writing it, so the value in
 * the options screen stays the user's own and comes straight back when the module is switched off.
 */
@Mixin(Gui.class)
public abstract class GuiAttackIndicatorMixin {
	@ModifyExpressionValue(method = "extractCrosshair",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object clientify$crosshairIndicator(Object vanilla) {
		return AttackIndicatorModule.vanillaStatus(vanilla);
	}

	@ModifyExpressionValue(method = "extractItemHotbar",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object clientify$hotbarIndicator(Object vanilla) {
		return AttackIndicatorModule.vanillaStatus(vanilla);
	}
}
