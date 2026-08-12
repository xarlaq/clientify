package dev.clientify.client.util;

/**
 * Marks a fishing bobber's render state as being hooked into the player at the keyboard — the one
 * that ends up parked on your face when somebody reels you in.
 *
 * <p>Render states carry no entity, and this one carries nothing that identifies what the hook
 * caught, so the answer is recorded during extraction while the entity is still in hand.
 */
public interface HookedOnSelf {
	boolean clientify$onSelf();

	void clientify$setOnSelf(boolean onSelf);
}
