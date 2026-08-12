package dev.clientify.client.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a rectangle into the parts of it that are still uncovered. Used to stop two module
 * backgrounds blending where they overlap: the second one is drawn only on the pieces the first
 * has not already claimed, so the overlap keeps a single background's colour and alpha instead
 * of stacking two translucent fills into a darker patch.
 *
 * <p>Pure integer geometry with no Minecraft types, so it can be checked by {@code verifyLogic}.
 *
 * @see dev.clientify.client.hud.ChromeMask
 */
public final class RectSplit {
	private RectSplit() {
	}

	public record R(int x, int y, int w, int h) {
		public int right() {
			return x + w;
		}

		public int bottom() {
			return y + h;
		}

		public boolean empty() {
			return w <= 0 || h <= 0;
		}

		public boolean overlaps(R o) {
			return x < o.right() && right() > o.x && y < o.bottom() && bottom() > o.y;
		}
	}

	/**
	 * The parts of {@code r} not covered by any of {@code covers}, left to right and top to
	 * bottom. An empty list means {@code r} is completely hidden and should not be drawn at all.
	 *
	 * @return {@code null} if the result would need more than {@code maxPieces} rectangles — the
	 *     caller should then draw {@code r} whole rather than spend the frame on it.
	 */
	public static List<R> subtract(R r, List<R> covers, int maxPieces) {
		List<R> pieces = new ArrayList<>(4);
		if (r.empty()) {
			return pieces;
		}
		pieces.add(r);
		for (R cover : covers) {
			if (cover.empty() || !r.overlaps(cover)) {
				continue; // cheap reject against the original box
			}
			List<R> next = new ArrayList<>(pieces.size() + 3);
			for (R p : pieces) {
				split(p, cover, next);
			}
			if (next.size() > maxPieces) {
				return null;
			}
			pieces = next;
			if (pieces.isEmpty()) {
				return pieces;
			}
		}
		return pieces;
	}

	/**
	 * The first pixel whose CENTRE is at or past {@code v} — i.e. exactly where the rasteriser
	 * starts filling an edge at {@code v}. Used for both edges of a rectangle: on the near edge it
	 * is the first pixel drawn, on the far edge the first pixel NOT drawn.
	 *
	 * <p>Chips drawn under a pose scale land on fractional coordinates, and this is the whole
	 * game: rounding the edge outward claims a pixel that was never painted, and a neighbour
	 * clipped out of it leaves a see-through hairline; rounding inward gives that pixel away and
	 * the neighbour blends over it, leaving a darker hairline. Both show only at the fractions
	 * that round the wrong way, which is why a seam appears at some positions and not others.
	 */
	public static int pixelLo(double v) {
		return (int) Math.ceil(v - 0.5);
	}

	/**
	 * The parts of a rounded rectangle that are definitely filled, as horizontal bands: one for
	 * the straight middle, then a band per distinct row of the two corner arcs (equal rows
	 * merged). This mirrors how {@link Draw#smoothRounded} actually lays a rounded fill down, so
	 * the result is the shape on screen rather than its bounding box — which matters when another
	 * background is clipped against it: claiming the box would carve a neighbour out of the empty
	 * corners, and claiming a square inside the arc would let the neighbour paint over the arc.
	 *
	 * <p>Rows round INWARD, so the arc's own antialiased fringe is left out; a neighbour may blend
	 * over that single pixel, which is far less visible than a gap.
	 */
	public static List<R> cover(R box, int radius) {
		List<R> out = new ArrayList<>();
		if (box.empty()) {
			return out;
		}
		int r = Math.max(0, Math.min(radius, Math.min(box.w(), box.h()) / 2));
		out.add(new R(box.x(), box.y() + r, box.w(), box.h() - 2 * r));
		int row = 0;
		while (row < r) {
			int in = arcInset(r, row);
			int end = row + 1;
			while (end < r && arcInset(r, end) == in) {
				end++; // the arc is monotonic, so equal rows are always adjacent
			}
			int band = end - row;
			int w = box.w() - 2 * in;
			if (w > 0) {
				out.add(new R(box.x() + in, box.y() + row, w, band));
				out.add(new R(box.x() + in, box.bottom() - row - band, w, band));
			}
			row = end;
		}
		return out;
	}

	/** How far in from the edge an r-px corner arc's fill starts on {@code row} (0 = outermost). */
	private static int arcInset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.ceil(r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy)));
	}

	/** Appends the parts of {@code p} outside {@code cover} — up to four bands around it. */
	private static void split(R p, R cover, List<R> out) {
		if (!p.overlaps(cover)) {
			out.add(p);
			return;
		}
		int top = Math.max(p.y(), cover.y());
		int bottom = Math.min(p.bottom(), cover.bottom());
		if (top > p.y()) {
			out.add(new R(p.x(), p.y(), p.w(), top - p.y()));
		}
		if (bottom < p.bottom()) {
			out.add(new R(p.x(), bottom, p.w(), p.bottom() - bottom));
		}
		// The left and right bands only span the rows the cover actually occupies.
		int left = Math.max(p.x(), cover.x());
		int right = Math.min(p.right(), cover.right());
		if (left > p.x()) {
			out.add(new R(p.x(), top, left - p.x(), bottom - top));
		}
		if (right < p.right()) {
			out.add(new R(right, top, p.right() - right, bottom - top));
		}
	}
}
