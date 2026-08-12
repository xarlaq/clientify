package dev.clientify.client.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Silhouette of a set of unit cubes: given the blocks in a selection, works out which of their
 * edges lie on the outside of the combined shape. Drawing only those makes a multi-block
 * waypoint read as one connected outline instead of a grid of separate boxes.
 *
 * <p>Deliberately free of any Minecraft types — it is pure integer geometry, which is both the
 * trickiest logic in the mod and the easiest to get wrong (an earlier version tested "all four
 * neighbours present", which a one-block-wide path can never satisfy, so every internal seam
 * was still drawn).
 */
public final class BlockOutline {
	/** Bit budget per axis in a packed key; ±524288 blocks covers any world coordinate. */
	private static final int BIAS = 0x80000;
	private static final long MASK = 0xFFFFF;

	private BlockOutline() {
	}

	/** One edge of the silhouette: a unit segment along {@code axis} (0 = X, 1 = Y, 2 = Z). */
	public record Edge(int axis, int x, int y, int z) {
	}

	/** Packs a block position into a set key. */
	public static long cell(int x, int y, int z) {
		return ((long) ((x + BIAS) & MASK) << 40)
				| ((long) ((y + BIAS) & MASK) << 20)
				| ((z + BIAS) & MASK);
	}

	/**
	 * The edges on the outside of the union of {@code cells} (keys from {@link #cell}). Each
	 * edge is returned once even where several blocks share it.
	 */
	public static List<Edge> silhouette(Set<Long> cells) {
		Set<Long> seen = new HashSet<>();
		List<Edge> out = new ArrayList<>();
		for (long key : cells) {
			int bx = (int) ((key >>> 40) & MASK) - BIAS;
			int by = (int) ((key >>> 20) & MASK) - BIAS;
			int bz = (int) (key & MASK) - BIAS;
			// The 12 edges of this cube: one per axis at each of the four perpendicular corners.
			for (int axis = 0; axis < 3; axis++) {
				for (int a = 0; a < 2; a++) {
					for (int c = 0; c < 2; c++) {
						int ex = bx + (axis == 0 ? 0 : a);
						int ey = by + (axis == 1 ? 0 : (axis == 0 ? a : c));
						int ez = bz + (axis == 2 ? 0 : c);
						if (!seen.add(edgeKey(axis, ex, ey, ez))) {
							continue; // a neighbour already considered this edge
						}
						if (onSilhouette(cells, axis, ex, ey, ez)) {
							out.add(new Edge(axis, ex, ey, ez));
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * Whether an edge belongs to the silhouette, judged by the four cells that meet along it:
	 *
	 * <ul>
	 *   <li>none or all four filled — the edge is not on the surface at all;
	 *   <li>exactly two filled side by side — the surface runs flat through it, so this is the
	 *       seam between adjacent blocks and must NOT be drawn;
	 *   <li>two filled diagonally — they touch only along this edge, so it shows;
	 *   <li>one or three filled — a convex or concave corner, so it shows.
	 * </ul>
	 */
	public static boolean onSilhouette(Set<Long> cells, int axis, int ex, int ey, int ez) {
		boolean[] filled = new boolean[4];
		int n = 0;
		for (int i = 0; i < 2; i++) {
			for (int j = 0; j < 2; j++) {
				int cx;
				int cy;
				int cz;
				switch (axis) {
					case 0 -> {
						cx = ex;
						cy = ey - 1 + i;
						cz = ez - 1 + j;
					}
					case 1 -> {
						cx = ex - 1 + i;
						cy = ey;
						cz = ez - 1 + j;
					}
					default -> {
						cx = ex - 1 + i;
						cy = ey - 1 + j;
						cz = ez;
					}
				}
				boolean has = cells.contains(cell(cx, cy, cz));
				filled[i * 2 + j] = has;
				if (has) {
					n++;
				}
			}
		}
		if (n == 0 || n == 4) {
			return false;
		}
		if (n == 2) {
			// [0]=(0,0) [1]=(0,1) [2]=(1,0) [3]=(1,1): the diagonals are 0&3 and 1&2.
			return (filled[0] && filled[3]) || (filled[1] && filled[2]);
		}
		return true;
	}

	private static long edgeKey(int axis, int x, int y, int z) {
		return ((long) axis << 60) | cell(x, y, z);
	}
}
