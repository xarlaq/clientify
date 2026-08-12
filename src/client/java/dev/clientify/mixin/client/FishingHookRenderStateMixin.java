package dev.clientify.mixin.client;

import dev.clientify.client.util.HookedOnSelf;
import net.minecraft.client.renderer.entity.state.FishingHookRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries "this one is stuck in you" from extraction through to the draw. */
@Mixin(FishingHookRenderState.class)
public class FishingHookRenderStateMixin implements HookedOnSelf {
	@Unique
	private boolean clientify$onSelf;

	@Override
	public boolean clientify$onSelf() {
		return clientify$onSelf;
	}

	@Override
	public void clientify$setOnSelf(boolean onSelf) {
		this.clientify$onSelf = onSelf;
	}
}
