package dev.clientify.client.hud;

/**
 * Identifies the current HUD render pass, so a module can build its content once and reuse it for
 * the rest of the pass.
 *
 * <p>Laying a chip out takes three questions — how wide, how tall, and what to draw — and each
 * used to rebuild the content from scratch: {@code render} measures, {@code bounds} measures
 * again, and the editor measures once more for its outline. The status effects chip was sorting
 * its effect list and measuring every label five times a frame to draw it once.
 *
 * <p>The token advances per PASS rather than per frame, because the editor re-renders every module
 * a second time within the same frame using placeholder content, and that must not reuse what the
 * real HUD pass built. Only cache against this in modules that draw inside one of those passes; a
 * module driven from a vanilla hook (boss bar, scoreboard, tab list) can render when the token has
 * not advanced and would hold a stale value.
 */
public final class HudFrame {
	private static int token;

	private HudFrame() {
	}

	/** Starts a new pass; anything cached against the previous token is now stale. */
	public static void begin() {
		token++;
	}

	public static int token() {
		return token;
	}
}
