package dev.clientify.mixin.client;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Decides which of our mixins are worth applying on the setup the game is actually running.
 *
 * <p>Only one so far, and it earned its place: VulkanMod merges
 * {@code PictureInPictureRenderer.blitTexture} into a mixin of its own at the same priority, which
 * leaves our injectors there nothing to attach to. Mixin treats that as a hard error while it is
 * preparing the injection — before {@code require} is looked at, so marking them optional does not
 * help — and the game never reaches the title screen.
 *
 * <p>Standing out of the way is the right answer rather than fighting over the method: what our
 * mixin does there is scale a picture-in-picture to match the HUD scale, and VulkanMod has its own
 * ideas about that method for reasons of its own.
 */
public class ClientifyMixinPlugin implements IMixinConfigPlugin {
	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.endsWith("PictureInPictureRendererMixin")) {
			return !FabricLoader.getInstance().isModLoaded("vulkanmod");
		}
		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
			IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
			IMixinInfo mixinInfo) {
	}
}
