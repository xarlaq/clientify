package dev.clientify.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.clientify.client.ClientifyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Runtime-generated UI textures rendered with LINEAR filtering, so rounded corners stay smooth
 * and crisp at any GUI scale (the way Lunar-style menus do it — high-res masks scaled down by
 * the GPU, not integer pixel fills).
 */
public final class Textures {
	public static final int SIZE = 64;

	/** Ring band (texture px, of SIZE) used by the outline corner masks. */
	public static final int RING_BAND = 12;

	public static final Identifier CORNER_TL = ClientifyClient.id("ui/corner_tl");
	public static final Identifier CORNER_TR = ClientifyClient.id("ui/corner_tr");
	public static final Identifier CORNER_BL = ClientifyClient.id("ui/corner_bl");
	public static final Identifier CORNER_BR = ClientifyClient.id("ui/corner_br");
	public static final Identifier RING_TL = ClientifyClient.id("ui/ring_tl");
	public static final Identifier RING_TR = ClientifyClient.id("ui/ring_tr");
	public static final Identifier RING_BL = ClientifyClient.id("ui/ring_bl");
	public static final Identifier RING_BR = ClientifyClient.id("ui/ring_br");
	public static final Identifier SEARCH = ClientifyClient.id("ui/search");
	public static final Identifier GEAR = ClientifyClient.id("ui/gear");
	public static final Identifier PENCIL = ClientifyClient.id("ui/pencil");
	public static final Identifier CLOSE = ClientifyClient.id("ui/close");
	public static final Identifier ARROW_LEFT = ClientifyClient.id("ui/arrow_left");
	public static final Identifier RESET = ClientifyClient.id("ui/reset");
	public static final Identifier MOVE = ClientifyClient.id("ui/move");
	public static final Identifier TRASH = ClientifyClient.id("ui/trash");
	public static final Identifier SUN = ClientifyClient.id("ui/sun");
	public static final Identifier MOON = ClientifyClient.id("ui/moon");
	public static final Identifier EYE = ClientifyClient.id("ui/eye");
	public static final Identifier EYE_OFF = ClientifyClient.id("ui/eye_off");
	public static final Identifier COPY = ClientifyClient.id("ui/copy");
	public static final Identifier SHARE = ClientifyClient.id("ui/share");
	public static final Identifier IMPORT = ClientifyClient.id("ui/import");

	private static boolean ready;

	private Textures() {
	}

	/** Generates + registers the textures once the GPU device is up. Returns availability. */
	public static boolean ensure() {
		if (ready) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || RenderSystem.getDevice() == null) {
			return false;
		}
		try {
			register(mc, CORNER_TL, cornerMask(SIZE, SIZE));
			register(mc, CORNER_TR, cornerMask(0, SIZE));
			register(mc, CORNER_BL, cornerMask(SIZE, 0));
			register(mc, CORNER_BR, cornerMask(0, 0));
			register(mc, RING_TL, ringMask(SIZE, SIZE));
			register(mc, RING_TR, ringMask(0, SIZE));
			register(mc, RING_BL, ringMask(SIZE, 0));
			register(mc, RING_BR, ringMask(0, 0));
			// Official Phosphor Fill glyphs (MIT); procedural shapes remain as fallback.
			register(mc, SEARCH, icon('\uE30C', Textures::search));        // magnifying-glass
			register(mc, GEAR, icon('\uE272', Textures::gear));              // gear-six
			register(mc, PENCIL, icon('\uE3B4', Textures::pencil));          // pencil-simple
			register(mc, CLOSE, close());                                    // Tabler x (user pick)
			register(mc, ARROW_LEFT, icon('\uE138', Textures::arrowLeft));   // caret-left
			register(mc, RESET, icon('\uE038', Textures::reset));            // arrow-counter-clockwise
			register(mc, MOVE, move());                                      // four-way move arrows
			register(mc, TRASH, icon('\uE4A6', Textures::trash));            // trash
			register(mc, SUN, sun());
			register(mc, MOON, moon());
			register(mc, EYE, eye(false));
			register(mc, EYE_OFF, eye(true));
			register(mc, COPY, icon('\uE1CA', Textures::copyGlyph));         // copy
			register(mc, SHARE, icon('\uE408', Textures::shareGlyph));       // share-network
			register(mc, IMPORT, icon('\uE20A', Textures::importGlyph));     // download
			ready = true;
		} catch (Throwable t) {
			ClientifyClient.LOGGER.warn("UI textures unavailable; falling back to pixel corners", t);
		}
		return ready;
	}

	private static void register(Minecraft mc, Identifier id, NativeImage img) {
		mc.getTextureManager().register(id, new LinearTexture(img));
	}

	/** Quarter-disc coverage mask; arc centre (cx,cy) is the inner corner of the tile. */
	private static NativeImage cornerMask(int cx, int cy) {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double dx = x + 0.5 - cx;
				double dy = y + 0.5 - cy;
				double dist = Math.sqrt(dx * dx + dy * dy);
				float cov = (float) Math.max(0.0, Math.min(1.0, SIZE - dist + 0.5));
				int a = Math.round(cov * 255f);
				img.setPixel(x, y, (a << 24) | 0x00FFFFFF); // white, alpha = coverage
			}
		}
		return img;
	}

	/** Quarter-ring (outline) mask: a smooth band of width {@code band} just inside the arc edge. */
	private static NativeImage ringMask(int cx, int cy) {
		return ringMask(cx, cy, RING_BAND);
	}

	private static NativeImage ringMask(int cx, int cy, int band) {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double dx = x + 0.5 - cx;
				double dy = y + 0.5 - cy;
				double dist = Math.sqrt(dx * dx + dy * dy);
				float outer = (float) Math.max(0.0, Math.min(1.0, SIZE - dist + 0.5));
				float inner = (float) Math.max(0.0, Math.min(1.0, dist - (SIZE - band) + 0.5));
				int a = Math.round(Math.min(outer, inner) * 255f);
				img.setPixel(x, y, (a << 24) | 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Cached quarter-ring masks {tl,tr,bl,br} for an arbitrary band width (module borders). */
	private static final java.util.Map<Integer, Identifier[]> RING_CACHE = new java.util.HashMap<>();

	public static Identifier[] ring(int bandPx) {
		if (!ensure()) {
			return null;
		}
		int band = Math.max(2, Math.min(SIZE, bandPx));
		return RING_CACHE.computeIfAbsent(band, b -> {
			Minecraft mc = Minecraft.getInstance();
			Identifier tl = ClientifyClient.id("ui/ringb" + b + "_tl");
			Identifier tr = ClientifyClient.id("ui/ringb" + b + "_tr");
			Identifier bl = ClientifyClient.id("ui/ringb" + b + "_bl");
			Identifier br = ClientifyClient.id("ui/ringb" + b + "_br");
			register(mc, tl, ringMask(SIZE, SIZE, b));
			register(mc, tr, ringMask(0, SIZE, b));
			register(mc, bl, ringMask(SIZE, 0, b));
			register(mc, br, ringMask(0, 0, b));
			return new Identifier[]{tl, tr, bl, br};
		});
	}

	private static java.awt.Font phosphor;

	/** Rasterizes a Phosphor Fill glyph; falls back to the procedural shape on any failure. */
	private static NativeImage icon(char codepoint, java.util.function.Supplier<NativeImage> fallback) {
		try {
			return glyphMask(codepoint);
		} catch (Throwable t) {
			ClientifyClient.LOGGER.warn("Phosphor glyph {} unavailable; using the built-in shape",
					Integer.toHexString(codepoint), t);
			return fallback.get();
		}
	}

	private static NativeImage glyphMask(char codepoint) throws Exception {
		if (phosphor == null) {
			try (java.io.InputStream in =
					Textures.class.getResourceAsStream("/assets/clientify/font/phosphor-fill.ttf")) {
				if (in == null) {
					throw new IllegalStateException("phosphor-fill.ttf not on the classpath");
				}
				phosphor = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, in);
			}
		}
		java.awt.Font f = phosphor.deriveFont(56f);
		java.awt.image.BufferedImage canvas =
				new java.awt.image.BufferedImage(SIZE, SIZE, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
				java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
				java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(f);
		java.awt.FontMetrics fm = g.getFontMetrics();
		int glyphW = Math.max(1, fm.charWidth(codepoint));
		float x = (SIZE - glyphW) / 2f;
		float y = (SIZE - (fm.getAscent() + fm.getDescent())) / 2f + fm.getAscent();
		g.setColor(java.awt.Color.WHITE);
		g.text(String.valueOf(codepoint), x, y);
		g.dispose();

		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int py = 0; py < SIZE; py++) {
			for (int px = 0; px < SIZE; px++) {
				int a = (canvas.getRGB(px, py) >>> 24) & 0xFF;
				img.setPixel(px, py, (a << 24) | 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Magnifying-glass (Tabler-style): a ring + a diagonal handle. */
	private static NativeImage search() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		double cx = 25, cy = 25, radius = 16, ring = 5;
		double hx0 = cx + radius * 0.707, hy0 = cy + radius * 0.707, hx1 = 56, hy1 = 56, hw = 5;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean inRing = Math.abs(Math.hypot(px - cx, py - cy) - radius) <= ring / 2.0;
				boolean inHandle = segDist(px, py, hx0, hy0, hx1, hy1) <= hw / 2.0;
				img.setPixel(x, y, (inRing || inHandle) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Cog icon: a central disc + hole, with evenly-spaced teeth around the rim. */
	private static NativeImage gear() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		double cx = SIZE / 2.0, cy = SIZE / 2.0;
		double bodyR = SIZE * 0.30, toothR = SIZE * 0.43, holeR = SIZE * 0.13;
		int teeth = 8;
		double seg = 2 * Math.PI / teeth;
		double toothHalf = seg * 0.30; // angular half-width of a tooth
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
				double r = Math.hypot(dx, dy);
				boolean solid;
				if (r <= holeR) {
					solid = false;
				} else if (r <= bodyR) {
					solid = true;
				} else if (r <= toothR) {
					double m = ((Math.atan2(dy, dx) % seg) + seg) % seg;
					solid = Math.abs(m - seg / 2) <= toothHalf;
				} else {
					solid = false;
				}
				img.setPixel(x, y, solid ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Pencil: diagonal body bar + a narrower tip stroke (Lunar's profile edit icon). */
	private static NativeImage pencil() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean body = segDist(px, py, 16, 48, 40, 24) <= 5.0;
				boolean tip = segDist(px, py, 42, 22, 50, 14) <= 2.6;
				img.setPixel(x, y, (body || tip) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Back arrow: left-pointing chevron. */
	private static NativeImage arrowLeft() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean a = segDist(px, py, 22, 32, 40, 14) <= 3.6;
				boolean b = segDist(px, py, 22, 32, 40, 50) <= 3.6;
				img.setPixel(x, y, (a || b) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Trash bin: lid + handle + tapered body with two slots. */
	/** Import fallback: an arrow coming down into a tray. */
	private static NativeImage importGlyph() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5;
				double py = y + 0.5;
				boolean shaft = px >= 28 && px <= 36 && py >= 10 && py <= 34;
				// Head is a triangle: half-width shrinks as it descends.
				boolean head = py >= 32 && py <= 44 && Math.abs(px - 32) <= (44 - py);
				boolean tray = py >= 46 && py <= 52 && px >= 14 && px <= 50;
				img.setPixel(x, y, (shaft || head || tray) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Copy fallback: two offset rounded squares, the back one showing behind the front. */
	private static NativeImage copyGlyph() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5;
				double py = y + 0.5;
				boolean back = px >= 12 && px <= 40 && py >= 10 && py <= 38;
				boolean backHole = px >= 16 && px <= 36 && py >= 14 && py <= 34;
				boolean front = px >= 24 && px <= 52 && py >= 22 && py <= 50;
				// The front tile is solid and punches the back one out, which is what reads as
				// "one on top of the other" rather than two loose outlines.
				boolean ink = front || (back && !backHole && !front);
				img.setPixel(x, y, ink ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Share fallback: three nodes joined by two bars — the network shape, not an export arrow. */
	private static NativeImage shareGlyph() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		double[][] nodes = {{44, 14}, {44, 50}, {18, 32}};
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5;
				double py = y + 0.5;
				boolean ink = false;
				for (double[] n : nodes) {
					if (Math.hypot(px - n[0], py - n[1]) <= 8.5) {
						ink = true;
						break;
					}
				}
				if (!ink) {
					ink = onBar(px, py, 18, 32, 44, 14) || onBar(px, py, 18, 32, 44, 50);
				}
				img.setPixel(x, y, ink ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** True when (px,py) lies within 3px of the segment (x0,y0)-(x1,y1). */
	private static boolean onBar(double px, double py, double x0, double y0, double x1, double y1) {
		double dx = x1 - x0;
		double dy = y1 - y0;
		double len2 = dx * dx + dy * dy;
		double t = Math.max(0, Math.min(1, ((px - x0) * dx + (py - y0) * dy) / len2));
		return Math.hypot(px - (x0 + t * dx), py - (y0 + t * dy)) <= 3.0;
	}

	private static NativeImage trash() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean handle = px >= 26 && px <= 38 && py >= 8 && py <= 14;
				boolean lid = px >= 14 && px <= 50 && py >= 14 && py <= 20;
				boolean body = px >= 19 && px <= 45 && py >= 24 && py <= 54;
				boolean slot = body && py >= 30 && py <= 48
						&& ((px >= 26 && px <= 29) || (px >= 35 && px <= 38));
				img.setPixel(x, y, ((handle || lid || body) && !slot) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Reset icon: circular arrow (ring with a gap + arrowhead at the gap). */
	private static NativeImage reset() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		double cx = 32, cy = 32, radius = 18, band = 7;
		// Arrowhead triangle at the gap (top-right), pointing clockwise.
		double ax = 56, ay = 30, bx = 42, by = 24, cxx = 46, cyy = 10;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				double d = Math.hypot(px - cx, py - cy);
				double ang = Math.toDegrees(Math.atan2(py - cy, px - cx)); // -180..180, y-down
				boolean inGap = ang > -75 && ang < -15;
				boolean ring = Math.abs(d - radius) <= band / 2.0 && !inGap;
				boolean head = inTriangle(px, py, ax, ay, bx, by, cxx, cyy);
				img.setPixel(x, y, (ring || head) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/**
	 * Four-way move arrows: a cross of bars with an arrowhead on each end. Drawn procedurally
	 * rather than pulled from Phosphor — a codepoint the font happens not to carry renders as a
	 * blank or a .notdef box without throwing, so {@link #icon} could not fall back from it.
	 */
	private static NativeImage move() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean bars = (px >= 28 && px <= 36 && py >= 15 && py <= 49)
						|| (py >= 28 && py <= 36 && px >= 15 && px <= 49);
				boolean heads = inTriangle(px, py, 32, 4, 22, 17, 42, 17)      // up
						|| inTriangle(px, py, 32, 60, 22, 47, 42, 47)          // down
						|| inTriangle(px, py, 4, 32, 17, 22, 17, 42)           // left
						|| inTriangle(px, py, 60, 32, 47, 22, 47, 42);         // right
				img.setPixel(x, y, (bars || heads) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	private static boolean inTriangle(double px, double py, double ax, double ay, double bx, double by,
			double cx, double cy) {
		double d1 = sign(px, py, ax, ay, bx, by);
		double d2 = sign(px, py, bx, by, cx, cy);
		double d3 = sign(px, py, cx, cy, ax, ay);
		boolean hasNeg = d1 < 0 || d2 < 0 || d3 < 0;
		boolean hasPos = d1 > 0 || d2 > 0 || d3 > 0;
		return !(hasNeg && hasPos);
	}

	private static double sign(double px, double py, double ax, double ay, double bx, double by) {
		return (px - bx) * (ay - by) - (ax - bx) * (py - by);
	}

	/**
	 * Tabler "x": two round-capped diagonal strokes, 6,6→18,18 in a 24-unit box at stroke
	 * width 2 — reproduced exactly at 64px (16→48, thickness 5.33; segDist = round caps).
	 */
	private static NativeImage close() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean a = segDist(px, py, 16, 16, 48, 48) <= 2.67;
				boolean b = segDist(px, py, 16, 48, 48, 16) <= 2.67;
				img.setPixel(x, y, (a || b) ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/**
	 * Tabler "eye" / "eye-off", reproduced from their 24-unit geometry at 64px. The lens is
	 * the pair of arcs through (3,12)-(12,6)-(21,12) and its mirror — circles of radius 9.75
	 * centred at (12,15.75) and (12,8.25) — with a round pupil, all at stroke width 2.
	 */
	private static NativeImage eye(boolean struck) {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		double s = SIZE / 24.0;           // Tabler units → texture px
		double half = 1.0 * s;            // stroke width 2, so half is 1 unit
		double cx = 12 * s;
		double topCy = 15.75 * s;
		double botCy = 8.25 * s;
		double arcR = 9.75 * s;
		double pupilR = 2.0 * s;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				double dTop = Math.hypot(px - cx, py - topCy);
				double dBot = Math.hypot(px - cx, py - botCy);
				// Each arc is only drawn where it bounds the lens (inside the other circle).
				boolean upper = Math.abs(dTop - arcR) <= half && dBot <= arcR + half;
				boolean lower = Math.abs(dBot - arcR) <= half && dTop <= arcR + half;
				boolean pupil = Math.abs(Math.hypot(px - cx, py - 12 * s) - pupilR) <= half;
				boolean solid = upper || lower || pupil;
				if (struck) {
					// The slash, with a cut-out so it reads clearly over the lens.
					boolean slash = segDist(px, py, 3 * s, 3 * s, 21 * s, 21 * s) <= half;
					boolean cut = segDist(px, py, 3 * s, 3 * s + 2.2 * s, 21 * s, 21 * s + 2.2 * s)
							<= half + 0.6 * s;
					solid = (solid && !cut) || slash;
				}
				img.setPixel(x, y, solid ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Sun: central disc + 8 rays (time-slider day marker). */
	private static NativeImage sun() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		double cx = 32, cy = 32, discR = 11;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean solid = Math.hypot(px - cx, py - cy) <= discR;
				if (!solid) {
					for (int i = 0; i < 8 && !solid; i++) {
						double a = i * Math.PI / 4;
						double x0 = cx + Math.cos(a) * 17, y0 = cy + Math.sin(a) * 17;
						double x1 = cx + Math.cos(a) * 26, y1 = cy + Math.sin(a) * 26;
						solid = segDist(px, py, x0, y0, x1, y1) <= 2.6;
					}
				}
				img.setPixel(x, y, solid ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	/** Moon: crescent — a disc with an offset disc cut out (time-slider night marker). */
	private static NativeImage moon() {
		NativeImage img = new NativeImage(SIZE, SIZE, false);
		double cx = 30, cy = 32, r = 20;
		double hx = 40, hy = 26, hr = 17;
		for (int y = 0; y < SIZE; y++) {
			for (int x = 0; x < SIZE; x++) {
				double px = x + 0.5, py = y + 0.5;
				boolean solid = Math.hypot(px - cx, py - cy) <= r && Math.hypot(px - hx, py - hy) > hr;
				img.setPixel(x, y, solid ? 0xFFFFFFFF : 0x00FFFFFF);
			}
		}
		return img;
	}

	private static double segDist(double px, double py, double ax, double ay, double bx, double by) {
		double dx = bx - ax, dy = by - ay;
		double len2 = dx * dx + dy * dy;
		double t = len2 <= 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
		return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
	}

	/** DynamicTexture forced to LINEAR sampling for smooth up/down-scaling. */
	private static final class LinearTexture extends DynamicTexture {
		LinearTexture(NativeImage img) {
			super(() -> "clientify-ui", img);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}
}
