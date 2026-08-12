package dev.clientify.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.clientify.client.modules.OverlayModule;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reports hidden foliage as having nothing to draw.
 *
 * <p>Cancelling {@code BlockRenderDispatcher.renderBatched} was the first way and only worked on
 * vanilla's chunk builder: Sodium replaces that pipeline outright, so on the profile this is
 * actually played on the hook never ran. Every builder, vanilla's or a mod's, asks a block whether
 * it has a shape to render, so answering here is the one place that covers all of them.
 *
 * <p>Client-side only, and this is a rendering answer rather than a gameplay one: collision,
 * breaking, redstone and the server's idea of the block are all untouched.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateRenderShapeMixin {
	@ModifyReturnValue(method = "getRenderShape", at = @At("RETURN"))
	private RenderShape clientify$hideFoliage(RenderShape shape) {
		return OverlayModule.hiddenInWorld((BlockBehaviour.BlockStateBase) (Object) this)
				? RenderShape.INVISIBLE : shape;
	}
}
