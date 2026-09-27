package dev.clientify.client.hud;

import dev.clientify.client.util.RectSplit;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Keeps module backgrounds from blending into each other. Two translucent chips that overlap
 * normally stack their alpha, so the shared corner turns into a darker patch. With this on, each
 * background remembers the screen area it covered and every later one is drawn only on the
 * area still free — the overlap keeps the first chip's colour, at its own alpha.
 *
 * <p>Clipping is done with the vanilla scissor stack, so no extra pass and no shader is needed.
 * A chip claims the pixels it certainly filled and clips against nothing else, so the two chips
 * always meet — see {@link #claim}. Nothing is claimed while the feature is off.
 */
public final class ChromeMask {
	/**
	 * Past this the clipping costs more than the artifact it prevents; draw whole instead (which
	 * blends, as before the feature). Stepping around one rounded corner already takes about a
	 * dozen pieces, so this has to leave room for a few of them.
	 */
	private static final int MAX_PIECES = 48;
	/** Safety valve in case a frame ends without {@link #beginFrame} (hidden HUD, odd screens). */
	private static final int MAX_CLAIMS = 256;

	private static final List<RectSplit.R> CLAIMED = new ArrayList<>();
	private static boolean active;

	private ChromeMask() {
	}

	/**
	 * Whether clipping is on for this frame. Callers check this to skip building a draw callback
	 * at all when it is off — background drawing is per chip per frame, so the usual path should
	 * allocate nothing for a feature it is not using.
	 */
	public static boolean enabled() {
		return active;
	}

	/** Starts a fresh frame. Called before the first vanilla HUD layer, and by the editor. */
	public static void beginFrame(boolean enabled) {
		CLAIMED.clear();
		active = enabled;
	}

	/**
	 * Draws a module background through {@code sink}, clipped to the parts of (x,y,w,h) that no
	 * earlier background has taken, then claims what it certainly covered. {@code sink} may draw
	 * under any pose — the rectangle is in screen pixels either way. {@code radius} is the
	 * chip's outer corner radius, also in screen pixels.
	 */
	public static void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, Runnable sink) {
		if (!active || w <= 0 || h <= 0) {
			sink.run();
			return;
		}
		// Clip against the OUTER pixel box: the chip's own edge may land mid-pixel (a scaled chip
		// drawn under a pose), and clipping to the rounded-off box would cut that edge off.
		int x1 = (int) Math.floor(x);
		int y1 = (int) Math.floor(y);
		RectSplit.R paint = new RectSplit.R(x1, y1,
				(int) Math.ceil(x + w) - x1, (int) Math.ceil(y + h) - y1);
		List<RectSplit.R> pieces = RectSplit.subtract(paint, CLAIMED, MAX_PIECES);
		if (pieces == null) {
			sink.run(); // too fragmented to be worth clipping
		} else {
			for (RectSplit.R piece : pieces) {
				scissor(g, piece);
				sink.run();
				g.disableScissor();
			}
		}
		claim(x, y, w, h, radius);
	}

	/**
	 * Records the pixels this chip definitely filled. Claiming too much leaves a seam — the next
	 * background gets clipped out of pixels this one never painted, and the world shows through;
	 * claiming too little only risks a pixel of double blending, so every rounding here errs
	 * inward.
	 *
	 * <p>A rounded chip is not its bounding box, so the shape comes from
	 * {@link RectSplit#cover} — the same row decomposition the renderer fills. Two earlier
	 * attempts were not tight enough and both showed: insetting the box uniformly let the next
	 * chip paint over this one's entire outer ring, and a square inscribed in each arc still gave
	 * away a third of the corner.
	 */
	private static void claim(float x, float y, float w, float h, float radius) {
		// Fractional edges resolve to the pixels the rasteriser actually filled — see pixelLo.
		int x1 = RectSplit.pixelLo(x);
		int y1 = RectSplit.pixelLo(y);
		RectSplit.R box = new RectSplit.R(x1, y1,
				RectSplit.pixelLo(x + w) - x1, RectSplit.pixelLo(y + h) - y1);
		if (box.empty()) {
			return;
		}
		if (CLAIMED.size() >= MAX_CLAIMS) {
			CLAIMED.clear();
		}
		CLAIMED.addAll(RectSplit.cover(box, (int) Math.ceil(Math.max(0f, radius))));
	}

	/**
	 * Scissors to a screen-space rectangle. {@code enableScissor} bakes in the current pose, so
	 * the pose is neutralised for that one call — the vanilla-font module path draws under a
	 * translate+scale, and its clip must not be scaled twice. Restoring the pose afterwards does
	 * not disturb the rectangle already pushed.
	 */
	private static void scissor(GuiGraphicsExtractor g, RectSplit.R r) {
		g.pose().pushMatrix();
		g.pose().identity();
		g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
		g.pose().popMatrix();
	}
}
