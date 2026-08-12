import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Derives the small caps alphabet from Minecraft's own font, so the letters keep the game's
 * shapes instead of ones I invented.
 *
 * <p>Reads ascii.png (a 16x16 grid of 8x8 cells), trims each capital and digit to its ink, then
 * squeezes it to five rows by dropping whichever rows repeat their neighbour — a vertical stroke
 * loses a row and still looks like itself, where a crossbar never would. What comes out is the
 * same silhouette at a smaller height, which is what small caps are.
 *
 * <pre>
 * java tools/SmallCapsGen.java ascii.png
 * </pre>
 *
 * Paste the printed block into SmallCaps.ALPHABET. ascii.png lives in the client jar at
 * assets/minecraft/textures/font/ascii.png.
 */
public final class SmallCapsGen {
	private static final int CELL = 8;
	private static final int TARGET_ROWS = 5;
	private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	private SmallCapsGen() {
	}

	public static void main(String[] args) throws Exception {
		BufferedImage sheet = ImageIO.read(new File(args[0]));
		int cols = sheet.getWidth() / CELL;
		for (char c : CHARS.toCharArray()) {
			int index = c; // ascii.png is laid out by code point
			int cx = (index % cols) * CELL;
			int cy = (index / cols) * CELL;
			boolean[][] cell = read(sheet, cx, cy);
			boolean[][] trimmed = trim(cell);
			boolean[][] small = squeeze(trimmed);
			print(c, small);
		}
	}

	private static boolean[][] read(BufferedImage sheet, int cx, int cy) {
		boolean[][] out = new boolean[CELL][CELL];
		for (int y = 0; y < CELL; y++) {
			for (int x = 0; x < CELL; x++) {
				out[y][x] = ((sheet.getRGB(cx + x, cy + y) >>> 24) & 0xFF) > 128;
			}
		}
		return out;
	}

	/** Cuts the empty margin so the glyph is its ink and nothing else. */
	private static boolean[][] trim(boolean[][] cell) {
		int top = CELL;
		int bottom = -1;
		int left = CELL;
		int right = -1;
		for (int y = 0; y < CELL; y++) {
			for (int x = 0; x < CELL; x++) {
				if (cell[y][x]) {
					top = Math.min(top, y);
					bottom = Math.max(bottom, y);
					left = Math.min(left, x);
					right = Math.max(right, x);
				}
			}
		}
		if (bottom < 0) {
			return new boolean[0][0];
		}
		boolean[][] out = new boolean[bottom - top + 1][right - left + 1];
		for (int y = top; y <= bottom; y++) {
			System.arraycopy(cell[y], left, out[y - top], 0, right - left + 1);
		}
		return out;
	}

	/**
	 * Drops rows until five are left, always the row that differs least from the one above it.
	 * Duplicated rows are the straight parts of a stroke, so losing one shortens the letter
	 * without changing what it is; the distinctive rows — bars, joins, curves — survive.
	 */
	private static boolean[][] squeeze(boolean[][] glyph) {
		List<boolean[]> rows = new ArrayList<>(List.of(glyph));
		while (rows.size() > TARGET_ROWS) {
			int bestRow = 1;
			int bestCost = Integer.MAX_VALUE;
			for (int i = 1; i < rows.size(); i++) {
				int cost = difference(rows.get(i - 1), rows.get(i));
				// Never drop the first or last row: they are the letter's top and its foot.
				if (i == rows.size() - 1) {
					cost += 2;
				}
				if (cost < bestCost) {
					bestCost = cost;
					bestRow = i;
				}
			}
			rows.remove(bestRow);
		}
		return rows.toArray(new boolean[0][]);
	}

	private static int difference(boolean[] a, boolean[] b) {
		int diff = 0;
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				diff++;
			}
		}
		return diff;
	}

	private static void print(char c, boolean[][] glyph) {
		StringBuilder line = new StringBuilder("\t\t\t{\"" + c + "\"");
		for (boolean[] row : glyph) {
			StringBuilder bits = new StringBuilder();
			for (boolean on : row) {
				bits.append(on ? '#' : '.');
			}
			line.append(", \"").append(bits).append('"');
		}
		line.append("},");
		System.out.println(line);
	}
}
