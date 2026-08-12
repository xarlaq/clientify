package dev.clientify.client.util;

import dev.clientify.client.gui.Textures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Shape helpers on GuiGraphics only — no custom shaders, so everything works unchanged on
 * OpenGL, Sodium, VulkanMod and OptiFine.
 *
 * <p>Rounded corners are GPU-smooth: high-res quarter-disc masks (see {@link Textures}) are
 * blitted scaled-down with LINEAR filtering — the "render big, sample linear" trick Lunar-style
 * clients use — with a pixel-AA fallback until the textures are generated.
 */
public final class Draw {
	public static final int R_SHEET = 8;
	public static final int R_SMALL = 6;

	private Draw() {
	}

	private static int clampRadius(int r, int w, int h) {
		return Math.max(0, Math.min(r, Math.min(w, h) / 2));
	}

	private static int inset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt((double) r * r - dy * dy));
	}

	private static float clamp01(float f) {
		return f < 0 ? 0 : (f > 1 ? 1 : f);
	}

	/** Multiplies the ARGB's existing alpha by {@code f} (for sub-pixel edge coverage). */
	private static int mulAlpha(int argb, float f) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(f));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/**
	 * One rounded corner: a solid span for the interior of each row + a single sub-pixel
	 * anti-aliased pixel at the exact arc boundary. Used only as the fallback until the
	 * corner-mask textures are ready.
	 */
	private static void aaFillCorner(GuiGraphics g, int cx, int cy, int r, boolean left, boolean top, int argb) {
		for (int j = 0; j < r; j++) {
			int py = top ? cy - 1 - j : cy + j;
			double dy = (py + 0.5) - cy;
			double half = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
			if (left) {
				double xEdge = cx - half;
				int xi = (int) Math.floor(xEdge);
				g.fill(xi + 1, py, cx, py + 1, argb);
				float cov = (float) (xi + 1 - xEdge);
				if (cov > 0.03f) {
					g.fill(xi, py, xi + 1, py + 1, mulAlpha(argb, cov));
				}
			} else {
				double xEdge = cx + half;
				int xi = (int) Math.floor(xEdge);
				g.fill(cx, py, xi, py + 1, argb);
				float cov = (float) (xEdge - xi);
				if (cov > 0.03f) {
					g.fill(xi, py, xi + 1, py + 1, mulAlpha(argb, cov));
				}
			}
		}
	}

	/** One rounded corner outline: 1px arc, sub-pixel anti-aliased (texture-less fallback). */
	private static void aaBorderCorner(GuiGraphics g, int cx, int cy, int r, boolean left, boolean top, int argb) {
		for (int j = 0; j < r; j++) {
			int py = top ? cy - 1 - j : cy + j;
			double dy = (py + 0.5) - cy;
			double half = Math.sqrt(Math.max(0.0, (double) r * r - dy * dy));
			double xEdge = left ? cx - half : cx + half;
			int xi = (int) Math.floor(xEdge);
			float frac = (float) (xEdge - xi);
			// Split the 1px line across the two pixels straddling the true edge.
			g.fill(xi, py, xi + 1, py + 1, mulAlpha(argb, 1f - frac));
			g.fill(xi + 1, py, xi + 2, py + 1, mulAlpha(argb, frac));
		}
	}

	/**
	 * Smooth rounded solid fill via GPU-filtered corner masks — crisp at any GUI scale.
	 * Falls back to the pixel-AA {@link #roundedFill} until the textures are ready.
	 */
	public static void smoothRounded(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
		r = clampRadius(r, w, h);
		if (w <= 0 || h <= 0) {
			return;
		}
		if (r == 0) {
			g.fill(x, y, x + w, y + h, argb);
			return;
		}
		if (!Textures.ensure()) {
			roundedFill(g, x, y, w, h, r, argb);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, argb);
		g.fill(x + r, y, x + w - r, y + r, argb);
		g.fill(x + r, y + h - r, x + w - r, y + h, argb);
		int n = Textures.SIZE;
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_TL, x, y, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_TR, x + w - r, y, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_BL, x, y + h - r, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_BR, x + w - r, y + h - r, 0f, 0f, r, r, n, n, n, n, argb);
	}

	/**
	 * Smooth rounded outline via GPU-filtered quarter-ring masks + crisp straight edges — the
	 * outline counterpart to {@link #smoothRounded}. Falls back to {@link #roundedBorder}.
	 */
	public static void smoothBorder(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
		r = clampRadius(r, w, h);
		if (w <= 0 || h <= 0) {
			return;
		}
		if (r == 0 || !Textures.ensure()) {
			roundedBorder(g, x, y, w, h, r, argb);
			return;
		}
		int t = Math.max(1, Math.round((float) Textures.RING_BAND * r / Textures.SIZE));
		g.fill(x + r, y, x + w - r, y + t, argb);             // top
		g.fill(x + r, y + h - t, x + w - r, y + h, argb);     // bottom
		g.fill(x, y + r, x + t, y + h - r, argb);             // left
		g.fill(x + w - t, y + r, x + w, y + h - r, argb);     // right
		int n = Textures.SIZE;
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.RING_TL, x, y, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.RING_TR, x + w - r, y, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.RING_BL, x, y + h - r, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.RING_BR, x + w - r, y + h - r, 0f, 0f, r, r, n, n, n, n, argb);
	}

	/**
	 * SOLID ring of visual thickness {@code t} at any radius: straight edges as fills plus
	 * corner ring masks whose band matches t/r exactly — one seamless piece (unlike stacked
	 * 1px arcs, whose AA edges leave seams).
	 */
	public static void thickBorder(GuiGraphics g, int x, int y, int w, int h, int r, int t, int argb) {
		r = clampRadius(r, w, h);
		t = Math.max(1, t);
		if (w <= 0 || h <= 0) {
			return;
		}
		if (r == 0) {
			g.fill(x, y, x + w, y + t, argb);
			g.fill(x, y + h - t, x + w, y + h, argb);
			g.fill(x, y + t, x + t, y + h - t, argb);
			g.fill(x + w - t, y + t, x + w, y + h - t, argb);
			return;
		}
		net.minecraft.resources.Identifier[] rings = Textures.ring(Math.round(64f * Math.min(t, r) / r));
		if (rings == null) {
			roundedBorder(g, x, y, w, h, r, argb);
			return;
		}
		g.fill(x + r, y, x + w - r, y + t, argb);
		g.fill(x + r, y + h - t, x + w - r, y + h, argb);
		g.fill(x, y + r, x + t, y + h - r, argb);
		g.fill(x + w - t, y + r, x + w, y + h - r, argb);
		int n = Textures.SIZE;
		g.blit(RenderPipelines.GUI_TEXTURED, rings[0], x, y, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, rings[1], x + w - r, y, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, rings[2], x, y + h - r, 0f, 0f, r, r, n, n, n, n, argb);
		g.blit(RenderPipelines.GUI_TEXTURED, rings[3], x + w - r, y + h - r, 0f, 0f, r, r, n, n, n, n, argb);
	}

	public static void roundedFill(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
		r = clampRadius(r, w, h);
		if (w <= 0 || h <= 0) {
			return;
		}
		if (r == 0) {
			g.fill(x, y, x + w, y + h, argb);
			return;
		}
		g.fill(x, y + r, x + w, y + h - r, argb);         // middle band, full width
		g.fill(x + r, y, x + w - r, y + r, argb);         // top straight
		g.fill(x + r, y + h - r, x + w - r, y + h, argb); // bottom straight
		aaFillCorner(g, x + r, y + r, r, true, true, argb);
		aaFillCorner(g, x + w - r, y + r, r, false, true, argb);
		aaFillCorner(g, x + r, y + h - r, r, true, false, argb);
		aaFillCorner(g, x + w - r, y + h - r, r, false, false, argb);
	}

	public static void roundedBorder(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
		r = clampRadius(r, w, h);
		if (w <= 0 || h <= 0) {
			return;
		}
		if (r == 0) {
			g.renderOutline(x, y, w, h, argb);
			return;
		}
		g.fill(x + r, y, x + w - r, y + 1, argb);           // top
		g.fill(x + r, y + h - 1, x + w - r, y + h, argb);   // bottom
		g.fill(x, y + r, x + 1, y + h - r, argb);           // left
		g.fill(x + w - 1, y + r, x + w, y + h - r, argb);   // right
		aaBorderCorner(g, x + r, y + r, r, true, true, argb);
		aaBorderCorner(g, x + w - r, y + r, r, false, true, argb);
		aaBorderCorner(g, x + r, y + h - r, r, true, false, argb);
		aaBorderCorner(g, x + w - r, y + h - r, r, false, false, argb);
	}

	/**
	 * Smooth rounded fill with a clean border of thickness {@code t}: the border colour fills
	 * the full rect and the fill colour is drawn inset by {@code t}, so the border is a true
	 * ring with no translucent show-through.
	 */
	public static void smoothRoundedBordered(GuiGraphics g, int x, int y, int w, int h, int r,
			int fillArgb, int borderArgb, int t) {
		smoothRounded(g, x, y, w, h, r, borderArgb);
		smoothRounded(g, x + t, y + t, w - 2 * t, h - 2 * t, Math.max(0, r - t), fillArgb);
	}

	/**
	 * Rounded panel textured from a full-screen backdrop texture (the blurred world): same
	 * row decomposition as a rounded fill, but each row samples its own screen region. Local
	 * coords are drawn under the caller's pose transform; UVs are computed from the TRUE
	 * screen rect (screenX/screenY/scale), so the sample always sits exactly behind the
	 * panel. Pass screenX/screenY = 0 and scale = 1 when drawing at absolute screen coords.
	 */
	public static void backdropRounded(GuiGraphics g, net.minecraft.resources.Identifier tex,
			int x, int y, int w, int h, int r,
			float screenX, float screenY, float scale, float screenW, float screenH, boolean vFlip) {
		r = clampRadius(r, w, h);
		float invW = 1f / Math.max(1f, screenW);
		float invH = 1f / Math.max(1f, screenH);
		if (r == 0) {
			backdropRow(g, tex, x, y, w, h, screenX, screenY, scale, invW, invH, vFlip);
			return;
		}
		backdropRow(g, tex, x, y + r, w, h - 2 * r, screenX, screenY, scale, invW, invH, vFlip);
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			backdropRow(g, tex, x + in, y + i, w - 2 * in, 1, screenX, screenY, scale, invW, invH, vFlip);
			backdropRow(g, tex, x + in, y + h - 1 - i, w - 2 * in, 1, screenX, screenY, scale, invW, invH, vFlip);
		}
	}

	private static void backdropRow(GuiGraphics g, net.minecraft.resources.Identifier tex,
			int lx, int ly, int lw, int lh,
			float ox, float oy, float scale, float invW, float invH, boolean vFlip) {
		if (lw <= 0 || lh <= 0) {
			return;
		}
		float u0 = (ox + lx * scale) * invW;
		float u1 = (ox + (lx + lw) * scale) * invW;
		float vt = (oy + ly * scale) * invH;
		float vb = (oy + (ly + lh) * scale) * invH;
		float v0 = vFlip ? 1f - vt : vt;
		float v1 = vFlip ? 1f - vb : vb;
		g.blit(tex, lx, ly, lx + lw, ly + lh, u0, u1, v0, v1);
	}

	/**
	 * Smooth rounded VERTICAL gradient fill: gradient bands + corner masks tinted with the
	 * gradient color at their height (a close approximation at small radii).
	 */
	public static void smoothRoundedGradient(GuiGraphics g, int x, int y, int w, int h, int r, int top, int bottom) {
		r = clampRadius(r, w, h);
		if (w <= 0 || h <= 0) {
			return;
		}
		if (r == 0) {
			g.fillGradient(x, y, x + w, y + h, top, bottom);
			return;
		}
		if (!Textures.ensure()) {
			g.fillGradient(x, y, x + w, y + h, top, bottom);
			return;
		}
		int hh = Math.max(1, h);
		int atR = dev.clientify.client.util.Colors.lerp(top, bottom, (float) r / hh);
		int atHr = dev.clientify.client.util.Colors.lerp(top, bottom, (float) (h - r) / hh);
		g.fillGradient(x, y + r, x + w, y + h - r, atR, atHr);
		g.fillGradient(x + r, y, x + w - r, y + r, top, atR);
		g.fillGradient(x + r, y + h - r, x + w - r, y + h, atHr, bottom);
		int n = Textures.SIZE;
		int cTop = dev.clientify.client.util.Colors.lerp(top, bottom, (float) r * 0.5f / hh);
		int cBot = dev.clientify.client.util.Colors.lerp(top, bottom, (float) (h - r * 0.5f) / hh);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_TL, x, y, 0f, 0f, r, r, n, n, n, n, cTop);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_TR, x + w - r, y, 0f, 0f, r, r, n, n, n, n, cTop);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_BL, x, y + h - r, 0f, 0f, r, r, n, n, n, n, cBot);
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.CORNER_BR, x + w - r, y + h - r, 0f, 0f, r, r, n, n, n, n, cBot);
	}
}
