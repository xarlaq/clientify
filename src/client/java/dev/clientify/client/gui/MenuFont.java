package dev.clientify.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.clientify.client.ClientifyClient;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Pixel-perfect UI font. Glyph sets are keyed by their PHYSICAL pixel ascent: for any
 * requested on-screen size (menu body/title, or a HUD chip at an arbitrary user scale) AWT
 * rasterizes Roboto at exactly that many device pixels and glyphs are drawn 1:1
 * texel-to-pixel on the physical grid (the pose is scaled by 1/guiScale around each draw).
 * No minification, no magnification — text is crisp at every GUI scale AND every module
 * scale. The cache clears when the GUI scale changes; sets are built lazily per size.
 *
 * <p>This replaces the render-big-scale-down approach whose bilinear minification skipped
 * texels (see RESEARCH.md). Falls back to Minecraft's own font if AWT rasterization fails.
 */
public final class MenuFont {
	/** Named text styles; sizes are the on-screen ascent in GUI pixels. */
	public enum Size {
		BODY(7f),
		TITLE(11f);

		public final float ascentGui;

		Size(float ascentGui) {
			this.ascentGui = ascentGui;
		}
	}

	private static final int PAD = 2;   // padding around each glyph cell
	private static final int COLS = 16;
	private static final int MAX_SETS = 16; // safety cap: clear cache when exceeded
	// Extra glyphs beyond Latin-1 that the menu uses: ellipsis, minus sign, bullets, arrows, etc.
	private static final String EXTRA = "…−∞‹›•×°→←";

	/** Key = physical ascent px. */
	private static final Map<Integer, GlyphSet> SETS = new HashMap<>();
	private static int builtForGuiScale = -1;
	private static Font baseFont;
	private static boolean failed;

	private MenuFont() {
	}

	private record Glyph(int u, int v, int w, int h, int advance) {
	}

	private static final class GlyphSet {
		final Identifier atlasId;
		final Map<Character, Glyph> glyphs = new HashMap<>();
		int atlasW;
		int atlasH;
		int cellH;

		GlyphSet(Identifier atlasId) {
			this.atlasId = atlasId;
		}
	}

	private static int guiScale() {
		return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
	}

	private static Font baseFont() throws Exception {
		if (baseFont == null) {
			try (InputStream in = MenuFont.class.getResourceAsStream("/assets/clientify/font/roboto-medium.ttf")) {
				if (in == null) {
					throw new IllegalStateException("roboto-medium.ttf not on the classpath");
				}
				baseFont = Font.createFont(Font.TRUETYPE_FONT, in);
			}
		}
		return baseFont;
	}

	/** The glyph set for an on-screen ascent of {@code ascentGui} GUI px, or null if AWT failed. */
	private static GlyphSet ensure(float ascentGui) {
		if (failed) {
			return null;
		}
		int scale = guiScale();
		if (scale != builtForGuiScale) {
			SETS.clear();
			builtForGuiScale = scale;
		}
		int ascentPx = Math.max(4, Math.round(ascentGui * scale));
		GlyphSet set = SETS.get(ascentPx);
		if (set != null) {
			return set;
		}
		if (SETS.size() >= MAX_SETS) {
			SETS.clear();
		}
		set = new GlyphSet(ClientifyClient.id("menu_font_" + ascentPx));
		try {
			build(set, ascentPx);
			SETS.put(ascentPx, set);
			return set;
		} catch (Throwable t) {
			failed = true;
			ClientifyClient.LOGGER.warn("Font atlas unavailable; using the vanilla font", t);
			return null;
		}
	}

	private static void build(GlyphSet set, int targetAscentPx) throws Exception {
		// Derive the AWT point size that yields exactly the wanted physical-pixel ascent.
		Font probeFont = baseFont().deriveFont(100f);
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D pg = probe.createGraphics();
		pg.setFont(probeFont);
		float ascentPerPt = pg.getFontMetrics().getAscent() / 100f;
		pg.dispose();
		Font awt = baseFont().deriveFont(targetAscentPx / ascentPerPt);

		pg = probe.createGraphics();
		pg.setFont(awt);
		FontMetrics fm = pg.getFontMetrics();
		int ascent = fm.getAscent();
		int cellH = fm.getHeight();
		pg.dispose();

		StringBuilder chars = new StringBuilder();
		for (char c = 0x20; c <= 0xFF; c++) {
			chars.append(c);
		}
		chars.append(EXTRA);

		int maxW = 1;
		for (int i = 0; i < chars.length(); i++) {
			maxW = Math.max(maxW, fm.charWidth(chars.charAt(i)));
		}
		int cellW = maxW + PAD * 2;
		int fullH = cellH + PAD * 2;
		int rows = (chars.length() + COLS - 1) / COLS;
		int atlasW = COLS * cellW;
		int atlasH = rows * fullH;

		BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = atlas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(awt);
		g.setColor(Color.WHITE);
		for (int i = 0; i < chars.length(); i++) {
			char c = chars.charAt(i);
			int cx = (i % COLS) * cellW;
			int cy = (i / COLS) * fullH;
			int w = Math.max(1, fm.charWidth(c));
			g.drawString(String.valueOf(c), cx + PAD, cy + PAD + ascent);
			set.glyphs.put(c, new Glyph(cx + PAD, cy + PAD, w, cellH, w));
		}
		g.dispose();

		// Coverage-only alpha over pure-white RGB: tint-friendly, clean edges, drawn 1:1 so
		// AWT's own hinting/antialiasing is exactly what shows on screen.
		NativeImage img = new NativeImage(atlasW, atlasH, false);
		for (int y = 0; y < atlasH; y++) {
			for (int x = 0; x < atlasW; x++) {
				int a = (atlas.getRGB(x, y) >>> 24) & 0xFF;
				img.setPixel(x, y, (a << 24) | 0x00FFFFFF);
			}
		}
		Minecraft.getInstance().getTextureManager().register(set.atlasId, new LinearTex(img));

		set.atlasW = atlasW;
		set.atlasH = atlasH;
		set.cellH = cellH;
	}

	/** On-screen line height in GUI px for a given ascent. */
	public static float lineHeight(float ascentGui) {
		GlyphSet set = ensure(ascentGui);
		return set != null ? (float) set.cellH / guiScale() : ascentGui * 1.3f;
	}

	public static float lineHeight(Size size) {
		return lineHeight(size.ascentGui);
	}

	public static int width(String s, Size size) {
		return width(s, size.ascentGui, 0f);
	}

	public static int width(String s, Size size, float tracking) {
		return width(s, size.ascentGui, tracking);
	}

	/** Width in GUI px, with letter-tracking (extra GUI px between characters). */
	public static int width(String s, float ascentGui, float tracking) {
		GlyphSet set = ensure(ascentGui);
		if (set == null) {
			return Minecraft.getInstance().font.width(s);
		}
		int scale = guiScale();
		int trackPx = Math.round(tracking * scale);
		int w = 0;
		for (int i = 0; i < s.length(); i++) {
			Glyph gl = set.glyphs.get(s.charAt(i));
			w += (gl != null ? gl.advance : set.cellH / 2) + trackPx;
		}
		if (!s.isEmpty()) {
			w -= trackPx;
		}
		return Math.round((float) w / scale);
	}

	public static void draw(GuiGraphics g, String s, float x, float y, int color) {
		draw(g, s, x, y, color, Size.BODY.ascentGui, false, 0f);
	}

	public static void draw(GuiGraphics g, String s, float x, float y, int color, Size size, boolean shadow) {
		draw(g, s, x, y, color, size.ascentGui, shadow, 0f);
	}

	public static void draw(GuiGraphics g, String s, float x, float y, int color, Size size, boolean shadow,
			float tracking) {
		draw(g, s, x, y, color, size.ascentGui, shadow, tracking);
	}

	/** Draws at an arbitrary on-screen ascent (GUI px) — used by scaled HUD chips. */
	public static void draw(GuiGraphics g, String s, float x, float y, int color, float ascentGui, boolean shadow,
			float tracking) {
		draw(g, s, x, y, i -> color, ascentGui, shadow, tracking);
	}

	/** Per-character colors ({@code colorByIndex} maps char index → ARGB) for text effects. */
	public static void draw(GuiGraphics g, String s, float x, float y,
			java.util.function.IntUnaryOperator colorByIndex, float ascentGui, boolean shadow, float tracking) {
		GlyphSet set = ensure(ascentGui);
		if (set == null) {
			g.drawString(Minecraft.getInstance().font, s, Math.round(x), Math.round(y),
					colorByIndex.applyAsInt(0), shadow);
			return;
		}
		int scale = guiScale();
		int trackPx = Math.round(tracking * scale);
		g.pose().pushMatrix();
		g.pose().scale(1f / scale, 1f / scale);
		// Snap to the physical pixel grid so texels map 1:1 (crisp at every GUI scale).
		int px = Math.round(x * scale);
		int py = Math.round(y * scale);
		if (shadow) {
			int off = Math.max(1, Math.round(scale * ascentGui / 14f));
			drawGlyphs(g, set, s, px + off, py + off,
					i -> {
						int c = colorByIndex.applyAsInt(i);
						return (c & 0xFF000000) | ((c >> 2) & 0x3F3F3F);
					}, trackPx);
		}
		drawGlyphs(g, set, s, px, py, colorByIndex, trackPx);
		g.pose().popMatrix();
	}

	/** Draws at PHYSICAL pixel coords; caller has already scaled the pose by 1/guiScale. */
	private static void drawGlyphs(GuiGraphics g, GlyphSet set, String s, int px, int py,
			java.util.function.IntUnaryOperator colorByIndex, int trackPx) {
		int penX = px;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			Glyph gl = set.glyphs.get(c);
			if (gl == null) {
				gl = set.glyphs.get('?');
			}
			if (gl == null) {
				penX += set.cellH / 2 + trackPx;
				continue;
			}
			if (c != ' ') {
				g.blit(RenderPipelines.GUI_TEXTURED, set.atlasId, penX, py,
						(float) gl.u, (float) gl.v, gl.w, gl.h, gl.w, gl.h, set.atlasW, set.atlasH,
						colorByIndex.applyAsInt(i));
			}
			penX += gl.advance + trackPx;
		}
	}

	private static final class LinearTex extends DynamicTexture {
		LinearTex(NativeImage img) {
			super(() -> "clientify-menu-font", img);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}
}
