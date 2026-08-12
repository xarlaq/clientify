package dev.clientify.mixin.client;

import dev.clientify.client.modules.ShulkerTooltipModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the shulker module hold a tooltip open. Vanilla draws the tooltip of whatever the pointer
 * is on, so moving onto the preview to inspect an item is what dismisses it; while the lock key is
 * down the module draws the pinned one instead and this call stands aside.
 */
@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Shadow
	protected @Nullable Slot hoveredSlot;

	@Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
	private void clientify$lockTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
		ItemStack hovered = hoveredSlot != null ? hoveredSlot.getItem() : ItemStack.EMPTY;
		if (ShulkerTooltipModule.renderLocked(guiGraphics, Minecraft.getInstance().font, hovered, mouseX, mouseY)) {
			ci.cancel();
		}
	}
}
