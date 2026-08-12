package dev.clientify.mixin.client;

import dev.clientify.client.modules.NametagsModule;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shows your OWN nametag in third person when the Nametags module asks for it. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	// require = 0: generic erasure makes this descriptor version-sensitive, and a missing
	// cosmetic own-nametag is far better than a crash.
	@Inject(method = "shouldShowName*", at = @At("RETURN"), cancellable = true, require = 0)
	private void clientify$showOwnName(Avatar avatar, double distSq, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && NametagsModule.showOwnFor(avatar)) {
			cir.setReturnValue(true);
		}
	}
}
