package dev.clientify.mixin.client;

import dev.clientify.client.modules.OverlayModule;
import dev.clientify.client.util.GlintContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Drops the enchantment shimmer where the Overlay module says it should not be.
 *
 * <p>The incoming type is changed rather than the call cancelled: these layers are pooled and
 * reused, so a cancelled write would leave the last item's glint in place and enchant something
 * that is not.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public class ItemLayerFoilMixin {
	@ModifyVariable(method = "setFoilType", at = @At("HEAD"), argsOnly = true)
	private ItemStackRenderState.FoilType clientify$foil(ItemStackRenderState.FoilType type) {
		if (type == ItemStackRenderState.FoilType.NONE) {
			return type;
		}
		ItemDisplayContext context = GlintContext.get();
		// No context means this layer was built outside the resolver, so there is nothing to
		// decide with; leaving it alone is the safe answer.
		return context == null || OverlayModule.glintAllowed(context)
				? type : ItemStackRenderState.FoilType.NONE;
	}
}
