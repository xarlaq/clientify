package dev.clientify.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the field of view the game draws the totem animation through.
 *
 * <p>That animation is not drawn through the field of view setting — the game asks for this one
 * with the setting switched off, which normally leaves it at a flat seventy degrees. Placing the
 * totem on a chosen spot means undoing that perspective, so the number has to be the same one, and
 * asking for it is the only way to stay right if it ever is not seventy: panoramic screenshots
 * widen it, dying narrows it, and a field of view mod is free to do as it likes with it.
 *
 * <p>An invoker rather than an injection, so there is nothing here for another rendering mod to
 * collide with.
 */
@Mixin(GameRenderer.class)
public interface GameRendererFovAccessor {
	@Invoker("getFov")
	float clientify$getFov(Camera camera, float partialTick, boolean useFovSetting);
}
