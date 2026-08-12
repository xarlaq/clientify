package dev.clientify.mixin.client;

import dev.clientify.client.modules.ShulkerTooltipModule;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The container-contents component is what shulker boxes use to list their items as tooltip
 * text (bundles use a different component). When the Shulker Tooltip module's "Hide Vanilla
 * Text" is on, suppress those lines — our grid preview replaces them.
 */
@Mixin(ItemContainerContents.class)
public abstract class ItemContainerContentsMixin {
	@Inject(method = "addToTooltip", at = @At("HEAD"), cancellable = true)
	private void clientify$hideShulkerText(Item.TooltipContext context, Consumer<Component> consumer,
			TooltipFlag flag, DataComponentGetter getter, CallbackInfo ci) {
		if (ShulkerTooltipModule.hideVanillaText()) {
			ci.cancel();
		}
	}
}
