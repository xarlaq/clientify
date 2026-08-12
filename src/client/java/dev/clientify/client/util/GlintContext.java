package dev.clientify.client.util;

import net.minecraft.world.item.ItemDisplayContext;

/**
 * What an item is currently being drawn FOR, while its layers are being built.
 *
 * <p>The enchantment shimmer is set on a layer that knows nothing about where it will end up, so
 * the context has to be carried across from the resolver — which is what makes "inventory only"
 * possible rather than the glint being all or nothing. It lives here rather than in the mixin
 * because a mixin may not hold anything static that is not private: those members are merged into
 * the class being changed, and vanilla's own {@code ItemModelResolver} is no place to keep it.
 */
public final class GlintContext {
	private static ItemDisplayContext current;

	private GlintContext() {
	}

	/** Only ever set and cleared around one call, on the render thread. */
	public static void set(ItemDisplayContext context) {
		current = context;
	}

	/** Null when an item is being built outside the resolver, so nothing is known about it. */
	public static ItemDisplayContext get() {
		return current;
	}
}
