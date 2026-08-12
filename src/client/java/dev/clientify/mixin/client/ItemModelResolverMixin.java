package dev.clientify.mixin.client;

import dev.clientify.client.util.GlintContext;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers what an item is being drawn FOR while its layers are being built.
 *
 * <p>The glint is set deep inside, on a layer that knows nothing about where it will end up, so
 * the context has to be carried in from here — this is the one method every path goes through
 * ({@code updateForLiving}, {@code updateForNonLiving} and {@code updateForTopItem} all call it),
 * which is what makes "inventory only" possible at all rather than glint being all or nothing.
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {
	@Inject(method = "appendItemLayers", at = @At("HEAD"))
	private void clientify$enter(ItemStackRenderState state, ItemStack stack,
			ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		GlintContext.set(context);
	}

	@Inject(method = "appendItemLayers", at = @At("RETURN"))
	private void clientify$exit(ItemStackRenderState state, ItemStack stack,
			ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		// Cleared rather than left behind: a layer built outside this call has no context, and
		// guessing it from whatever ran last is how a setting starts behaving at random.
		GlintContext.set(null);
	}
}
