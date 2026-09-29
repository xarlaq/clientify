package dev.clientify.mixin.client;

import dev.clientify.client.modules.ShulkerTooltipModule;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void clientify$shulkerGrid(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (!ShulkerTooltipModule.active() || cir.getReturnValue().isPresent()) {
			return;
		}
		ItemStack self = (ItemStack) (Object) this;
		if (!(self.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock box)) {
			return;
		}
		ItemContainerContents contents = self.get(DataComponents.CONTAINER);
		if (contents != null && contents.nonEmptyItems().iterator().hasNext()) {
			DyeColor color = box.getColor();
			int tint = color != null ? color.getTextureDiffuseColor() : 0;
			cir.setReturnValue(Optional.of(new ShulkerTooltipModule.ShulkerGridTooltip(contents, tint)));
		}
	}
}
