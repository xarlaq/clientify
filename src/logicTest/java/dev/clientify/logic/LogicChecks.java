package dev.clientify.logic;

import dev.clientify.client.util.BlockOutline;
import dev.clientify.client.util.RectSplit;
import dev.clientify.client.util.RectSplit.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regression checks for logic that needs no Minecraft at all. Run with {@code gradlew
 * verifyLogic}; exits non-zero on failure.
 *
 * <p>These exist because the bugs that reached the user were never compile or boot failures —
 * they were silent logic errors (an outline rule that could not trigger, a zoom factor that was
 * always 1). "Compiles and boots" says nothing about arithmetic, so anything pure gets checked
 * here instead.
 */
public final class LogicChecks {
	private static int failures;

	private LogicChecks() {
	}

	public static void main(String[] args) {
		blockOutline();
		rectSplit();
		pixelLo();
		rectCover();
		System.out.println(failures == 0
				? "\nAll logic checks passed."
				: "\n" + failures + " logic check(s) FAILED.");
		if (failures > 0) {
			System.exit(1);
		}
	}

	// ---- BlockOutline: silhouette of a set of unit cubes ----

	private static void blockOutline() {
		System.out.println("BlockOutline.silhouette");

		// A lone cube shows all 12 of its edges.
		eq("single block", edges(new int[][] {{0, 0, 0}}), 12);

		// Two neighbours read as a 2x1x1 box: 8 unit segments along the long axis plus 8 at the
		// ends. The 4 edges on the shared face are the seam and must be dropped.
		eq("two adjacent along X", edges(new int[][] {{0, 0, 0}, {1, 0, 0}}), 16);
		eq("two adjacent along Y", edges(new int[][] {{0, 0, 0}, {0, 1, 0}}), 16);
		eq("two adjacent along Z", edges(new int[][] {{0, 0, 0}, {0, 0, 1}}), 16);

		// A straight run: 4 long edges of n segments each, plus 8 at the two ends. This is the
		// walk-and-hold trace, and the case the first version of the rule got wrong — it tested
		// "all four neighbours filled", which a one-block-wide path never satisfies, so every
		// seam stayed drawn.
		eq("run of 3", edges(line(3)), 4 * 3 + 8);
		eq("run of 10", edges(line(10)), 4 * 10 + 8);

		// Blocks meeting only corner-to-corner touch along a single edge, which still shows, so
		// the pair keeps every edge except the one they share.
		eq("diagonal pair", edges(new int[][] {{0, 0, 0}, {1, 1, 0}}), 12 + 12 - 1);

		// Filled volumes: no interior edge survives, and each box edge is n segments long.
		eq("2x1x2 slab", edges(new int[][] {{0, 0, 0}, {1, 0, 0}, {0, 0, 1}, {1, 0, 1}}), 20);
		eq("2x2x2 cube", edges(cube(2)), 24);
		eq("3x3x3 cube", edges(cube(3)), 36);

		// Coordinates are packed into a long; negatives and far-out values must round-trip.
		eq("negative position", edges(new int[][] {{-40, -60, -1200}}), 12);
		eq("negative adjacent", edges(new int[][] {{-5, 70, -300}, {-4, 70, -300}}), 16);
		eq("far position", edges(new int[][] {{250000, 200, -250000}}), 12);

		// Result must not depend on insertion order.
		int forward = edges(new int[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}});
		int reverse = edges(new int[][] {{1, 0, 1}, {1, 0, 0}, {0, 0, 0}});
		eq("order independent", forward, reverse);

		// A duplicated block must not change the shape (re-walking a traced block).
		eq("duplicate block ignored", edges(new int[][] {{0, 0, 0}, {0, 0, 0}}), 12);
	}

	private static int edges(int[][] blocks) {
		Set<Long> cells = new HashSet<>();
		for (int[] b : blocks) {
			cells.add(BlockOutline.cell(b[0], b[1], b[2]));
		}
		return BlockOutline.silhouette(cells).size();
	}

	private static int[][] line(int n) {
		int[][] out = new int[n][];
		for (int i = 0; i < n; i++) {
			out[i] = new int[] {i, 0, 0};
		}
		return out;
	}

	private static int[][] cube(int n) {
		int[][] out = new int[n * n * n][];
		int i = 0;
		for (int x = 0; x < n; x++) {
			for (int y = 0; y < n; y++) {
				for (int z = 0; z < n; z++) {
					out[i++] = new int[] {x, y, z};
				}
			}
		}
		return out;
	}

	// ---- RectSplit: the uncovered parts of a rectangle ----

	private static void rectSplit() {
		System.out.println("\nRectSplit.subtract");
		R box = new R(0, 0, 10, 10);

		eq("nothing covered", pieces(box), 1);
		eq("cover swallows box", pieces(box, new R(-2, -2, 20, 20)), 0);
		eq("cover misses box", pieces(box, new R(40, 40, 5, 5)), 1);
		// Sharing an edge is not overlapping: two chips side by side must both draw in full.
		eq("cover only abuts", pieces(box, new R(10, 0, 5, 10)), 1);
		eq("left half covered", pieces(box, new R(0, 0, 5, 10)), 1);
		eq("band across middle", pieces(box, new R(-5, 4, 20, 2)), 2);
		eq("corner covered", pieces(box, new R(0, 0, 4, 4)), 2);
		eq("hole in middle", pieces(box, new R(3, 3, 4, 4)), 4);
		eq("zero-size cover ignored", pieces(box, new R(2, 2, 0, 5)), 1);

		// Over budget must report null (draw whole) rather than shred the frame into slivers.
		List<R> combs = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			combs.add(new R(i * 2, 3, 1, 4));
		}
		eq("piece budget respected", RectSplit.subtract(box, combs, 4) == null ? 1 : 0, 1);
		eq("budget met is not null", RectSplit.subtract(box, combs, 64) == null ? 1 : 0, 0);

		// The real invariant: the pieces cover exactly the uncovered pixels, each one once. Any
		// double-covered pixel is a background drawn twice — the very blending this prevents.
		eq("exact: single cover", mismatches(box, new R(2, 2, 3, 3)), 0);
		eq("exact: two overlapping covers", mismatches(box, new R(1, 1, 5, 5), new R(4, 4, 5, 5)), 0);
		eq("exact: cover past two edges", mismatches(box, new R(-4, 6, 20, 9)), 0);
		eq("exact: three covers", mismatches(box, new R(0, 0, 3, 10), new R(4, 4, 2, 2),
				new R(7, -3, 9, 6)), 0);
		eq("exact: stacked identical", mismatches(box, new R(2, 2, 4, 4), new R(2, 2, 4, 4)), 0);
		eq("exact: offset box origin", mismatches(new R(-6, 12, 9, 7), new R(-3, 10, 4, 5),
				new R(-8, 15, 6, 2)), 0);
	}

	// ---- RectSplit.pixelLo: which pixels an edge actually covers ----

	private static void pixelLo() {
		System.out.println("\nRectSplit.pixelLo");

		// A pixel is filled when its CENTRE (at +0.5) falls inside the edge.
		eq("whole number", RectSplit.pixelLo(40.0), 40);
		eq("just past a pixel start", RectSplit.pixelLo(40.1), 40);
		eq("exactly on a centre", RectSplit.pixelLo(40.5), 40); // centre lands on the edge: filled
		eq("past the centre", RectSplit.pixelLo(40.6), 41);
		eq("almost the next pixel", RectSplit.pixelLo(40.99), 41);
		eq("negative", RectSplit.pixelLo(-3.2), -3);
		eq("negative on a centre", RectSplit.pixelLo(-3.5), -4);

		// The two failure modes this replaces, stated as the checks that would have caught them:
		// rounding a far edge UP claims an unpainted pixel (see-through hairline), rounding it
		// DOWN gives away a painted one (double-blended hairline). Both are off by one here.
		eq("not ceil (would over-claim)", RectSplit.pixelLo(40.1) != (int) Math.ceil(40.1) ? 1 : 0, 1);
		eq("not floor (would under-claim)", RectSplit.pixelLo(40.6) != (int) Math.floor(40.6) ? 1 : 0, 1);

		// A one-wide sliver still resolves to exactly one pixel, at either fraction.
		eq("sliver at .2", RectSplit.pixelLo(10.2 + 1) - RectSplit.pixelLo(10.2), 1);
		eq("sliver at .8", RectSplit.pixelLo(10.8 + 1) - RectSplit.pixelLo(10.8), 1);
		// A scaled chip keeps its full pixel count wherever it is placed.
		eq("width 25.5 at .0", RectSplit.pixelLo(16.0 + 25.5) - RectSplit.pixelLo(16.0), 25);
		eq("width 25.5 at .5", RectSplit.pixelLo(16.5 + 25.5) - RectSplit.pixelLo(16.5), 26);
	}

	// ---- RectSplit.cover: the filled shape of a rounded rectangle ----

	private static void rectCover() {
		System.out.println("\nRectSplit.cover");

		// Squared corners: the box is filled exactly, in one band.
		eq("radius 0 is one band", RectSplit.cover(new R(0, 0, 40, 20), 0).size(), 1);
		eq("radius 0 covers all", coverArea(new R(0, 0, 40, 20), 0), 800);

		// The invariant that matters: never claim a pixel the renderer did not fill. A claimed
		// pixel outside the rounded shape is a background clipped away from where nothing was
		// drawn — the transparent seam this whole mechanism exists to avoid.
		for (int r : new int[] {1, 2, 3, 6, 8, 14, 20}) {
			eq("r=" + r + " claims nothing outside", outsideShape(new R(0, 0, 60, 44), r), 0);
		}
		// Non-square boxes and offset origins must hold the same invariant.
		eq("tall box", outsideShape(new R(-13, 7, 21, 60), 9), 0);
		eq("radius clamped to half", outsideShape(new R(0, 0, 12, 12), 30), 0);

		// And it must not give away much: the arc's own fringe only, never a chunk of the corner.
		// missedDepth counts both ends of a row, so 2 is one pixel per side.
		eq("r=6 fringe <= 1px per side", missedDepth(new R(0, 0, 60, 44), 6) <= 2 ? 1 : 0, 1);
		eq("r=14 fringe <= 1px per side", missedDepth(new R(0, 0, 60, 44), 14) <= 2 ? 1 : 0, 1);
		eq("r=20 fringe <= 1px per side", missedDepth(new R(0, 0, 60, 44), 20) <= 2 ? 1 : 0, 1);

		// Bands must not overlap each other, or a claim would be counted twice.
		eq("bands disjoint r=6", coverOverlaps(new R(0, 0, 60, 44), 6), 0);
		eq("bands disjoint r=14", coverOverlaps(new R(0, 0, 60, 44), 14), 0);
	}

	/**
	 * True when pixel (x,y) is filled by a rounded rect — the shape the renderer draws. The pixel
	 * CENTRE is at +0.5 and the corner circles sit on whole coordinates; getting that half pixel
	 * wrong is what produced the seam bug in the first place, so this reference states it
	 * explicitly rather than working in pixel indices.
	 */
	private static boolean inRounded(R box, int r, int x, int y) {
		if (x < box.x() || x >= box.right() || y < box.y() || y >= box.bottom()) {
			return false;
		}
		double px = x + 0.5;
		double py = y + 0.5;
		double cx = Math.min(Math.max(px, box.x() + r), box.right() - r);
		double cy = Math.min(Math.max(py, box.y() + r), box.bottom() - r);
		double dx = px - cx;
		double dy = py - cy;
		return dx * dx + dy * dy <= (double) r * r;
	}

	private static int coverArea(R box, int r) {
		int sum = 0;
		for (R piece : RectSplit.cover(box, r)) {
			sum += piece.w() * piece.h();
		}
		return sum;
	}

	/** Claimed pixels that fall outside the rounded shape (must be zero). */
	private static int outsideShape(R box, int r) {
		int bad = 0;
		for (R piece : RectSplit.cover(box, r)) {
			for (int x = piece.x(); x < piece.right(); x++) {
				for (int y = piece.y(); y < piece.bottom(); y++) {
					if (!inRounded(box, Math.min(r, Math.min(box.w(), box.h()) / 2), x, y)) {
						bad++;
					}
				}
			}
		}
		return bad;
	}

	/** Worst per-row gap between the shape and what was claimed, in pixels. */
	private static int missedDepth(R box, int r) {
		boolean[][] claimed = new boolean[box.w()][box.h()];
		for (R piece : RectSplit.cover(box, r)) {
			for (int x = piece.x(); x < piece.right(); x++) {
				for (int y = piece.y(); y < piece.bottom(); y++) {
					claimed[x - box.x()][y - box.y()] = true;
				}
			}
		}
		int worst = 0;
		int rr = Math.min(r, Math.min(box.w(), box.h()) / 2);
		for (int y = 0; y < box.h(); y++) {
			int missed = 0;
			for (int x = 0; x < box.w(); x++) {
				if (inRounded(box, rr, box.x() + x, box.y() + y) && !claimed[x][y]) {
					missed++;
				}
			}
			worst = Math.max(worst, missed);
		}
		return worst; // counts both ends of the row, so 2 means one pixel per side
	}

	private static int coverOverlaps(R box, int r) {
		List<R> parts = RectSplit.cover(box, r);
		int bad = 0;
		for (int i = 0; i < parts.size(); i++) {
			for (int j = i + 1; j < parts.size(); j++) {
				if (parts.get(i).overlaps(parts.get(j))) {
					bad++;
				}
			}
		}
		return bad;
	}

	private static int pieces(R box, R... covers) {
		List<R> got = RectSplit.subtract(box, List.of(covers), 64);
		return got == null ? -1 : got.size();
	}

	/** Pixels the pieces get wrong: not drawn when they should be, or drawn more than once. */
	private static int mismatches(R box, R... covers) {
		List<R> got = RectSplit.subtract(box, List.of(covers), 64);
		if (got == null) {
			return -1;
		}
		int bad = 0;
		for (int x = box.x() - 2; x < box.right() + 2; x++) {
			for (int y = box.y() - 2; y < box.bottom() + 2; y++) {
				int drawn = 0;
				for (R p : got) {
					if (x >= p.x() && x < p.right() && y >= p.y() && y < p.bottom()) {
						drawn++;
					}
				}
				boolean inBox = x >= box.x() && x < box.right() && y >= box.y() && y < box.bottom();
				boolean covered = false;
				for (R c : covers) {
					if (x >= c.x() && x < c.right() && y >= c.y() && y < c.bottom()) {
						covered = true;
					}
				}
				if (drawn != (inBox && !covered ? 1 : 0)) {
					bad++;
				}
			}
		}
		return bad;
	}

	// ---- tiny assertion helper (no framework) ----

	private static void eq(String what, int got, int want) {
		boolean ok = got == want;
		if (!ok) {
			failures++;
		}
		System.out.printf("  %-28s got=%-5d want=%-5d %s%n", what, got, want, ok ? "ok" : "FAIL");
	}
}
