package dev.clientify.client.util;

/** ARGB color helpers: hex parsing, lerp, chroma, animated wave. All colors are ARGB ints. */
public final class Colors {
	private Colors() {
	}

	public static final int WHITE = 0xFFFFFFFF;
	public static final int ACCENT = 0xFFF35D12;

	/** Continuous seconds — never wraps with a discontinuity. */
	public static double timeSeconds() {
		return System.currentTimeMillis() / 1000.0;
	}

	/** Parses "#RRGGBB" or "#AARRGGBB" (leading '#' optional). Falls back on bad input. */
	public static int parse(String hex, int fallback) {
		if (hex == null) {
			return fallback;
		}
		String s = hex.startsWith("#") ? hex.substring(1) : hex;
		try {
			if (s.length() == 6) {
				return 0xFF000000 | Integer.parseInt(s, 16);
			} else if (s.length() == 8) {
				return (int) Long.parseLong(s, 16);
			}
		} catch (NumberFormatException ignored) {
		}
		return fallback;
	}

	public static String format(int argb) {
		int a = argb >>> 24;
		if (a == 0xFF) {
			return String.format("#%06X", argb & 0xFFFFFF);
		}
		return String.format("#%08X", argb);
	}

	public static int withAlpha(int rgb, float alpha) {
		int a = Math.round(Math.min(1f, Math.max(0f, alpha)) * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	public static int lerp(int a, int b, float t) {
		t = Math.min(1f, Math.max(0f, t));
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24)
				| (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	/**
	 * Smooth looping chroma. Continuous time (no millis-modulo hard cut).
	 * {@code phase} is in hue units [0,1) — 0 for SHIFT, per-char offsets for WAVE.
	 */
	public static int chroma(float speed, float phase) {
		// One full hue cycle every ~4s at speed 1
		double cycles = timeSeconds() * 0.25 * Math.max(0.05f, speed);
		float hue = (float) (cycles - Math.floor(cycles)) + phase;
		hue = hue - (float) Math.floor(hue);
		return hsv(hue, 0.80f, 1f);
	}

	/** Traveling-wave interpolation factor in [0,1]: full period ~2.5s at speed 1. */
	public static float waveT(float speed, float phase) {
		return (float) (0.5 + 0.5 * Math.sin(timeSeconds() * Math.max(0.05f, speed) * (Math.PI * 2.0 / 2.5)
				+ phase * Math.PI * 2.0));
	}

	/** h, s, v in [0,1] -> opaque ARGB. */
	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		s = Math.min(1f, Math.max(0f, s));
		v = Math.min(1f, Math.max(0f, v));
		float r, g, b;
		int i = (int) (h * 6f);
		float f = h * 6f - i;
		float p = v * (1f - s);
		float q = v * (1f - f * s);
		float t = v * (1f - (1f - f) * s);
		switch (Math.floorMod(i, 6)) {
			case 0 -> { r = v; g = t; b = p; }
			case 1 -> { r = q; g = v; b = p; }
			case 2 -> { r = p; g = v; b = t; }
			case 3 -> { r = p; g = q; b = v; }
			case 4 -> { r = t; g = p; b = v; }
			default -> { r = v; g = p; b = q; }
		}
		return 0xFF000000 | (Math.round(r * 255f) << 16) | (Math.round(g * 255f) << 8) | Math.round(b * 255f);
	}

	/** RGB [0,255] -> HSV [0,1] each. Returns float[]{h,s,v}. */
	public static float[] rgbToHsv(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h;
		if (d == 0) {
			h = 0;
		} else if (max == r) {
			h = ((g - b) / d) % 6f;
		} else if (max == g) {
			h = (b - r) / d + 2f;
		} else {
			h = (r - g) / d + 4f;
		}
		h /= 6f;
		if (h < 0) {
			h += 1f;
		}
		float s = max == 0 ? 0 : d / max;
		return new float[]{h, s, max};
	}
}
