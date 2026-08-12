package dev.clientify.client.config;

import dev.clientify.client.util.Colors;

/**
 * Settings every HUD module shares. Serialized to clientify.json verbatim by GSON, so all
 * fields are public and defaulted at declaration (absent JSON keys keep the default).
 */
public class ModuleSettings {
	public boolean enabled = false;

	public Anchor anchor = Anchor.TOP_LEFT;
	public float offsetX = 5;
	public float offsetY = 5;
	public float scale = 1.0f;

	public boolean textShadow = true;
	public FontMode font = FontMode.MINECRAFT;

	public boolean background = true;
	/** Alpha byte of {@code a} is the chip opacity (#AARRGGBB). */
	public ColorSpec bgColor = new ColorSpec("#8C101014");
	/** Frosted backdrop behind the chip (world blurred through vanilla's blur chain). */
	public boolean bgBlur = false;
	public boolean bgRounded = false;
	public int bgRadius = 6;
	/** Extra background size beyond the auto-fit, in GUI px (text stays centered). */
	public int bgWidth = 6;
	public int bgHeight = 2;
	public boolean border = false;
	public int borderThickness = 1;
	public ColorSpec borderColor = new ColorSpec("#F35D12");

	/** When off, the whole text uses {@link #labelColor} (one color for everything). */
	public boolean splitColors = false;
	public ColorSpec labelColor = new ColorSpec("#FFFFFF");
	public ColorSpec valueColor = new ColorSpec("#F35D12");

	public enum FontMode {
		CLIENTIFY, MINECRAFT, SMALL_CAPS;

		/** True for the modes drawn with Minecraft's own glyphs rather than the bundled atlas. */
		public boolean vanilla() {
			return this != CLIENTIFY;
		}

		public String label() {
			return switch (this) {
				case CLIENTIFY -> "Clientify";
				case MINECRAFT -> "Minecraft";
				case SMALL_CAPS -> "Small Caps";
			};
		}
	}

	// ---- chip padding ----
	//
	// Every module sizes itself as content + a fixed pad of its own + the user's Extra Width /
	// Extra Height. That pad exists to keep the text off the edge of the chip, so when there is
	// no chip it is just dead space that stops the module sitting flush in a screen corner.
	// These collapse the whole lot to zero once nothing is drawn behind the text, which makes a
	// background-off module measure exactly its content.
	//
	// A border counts as something to pad: with the line drawn hard against the glyphs it reads
	// as a box squashing the text, so padding stays while a border is on.

	/** Whether anything is drawn behind the content and therefore needs room around it. */
	public boolean padsContent() {
		return background || border;
	}

	/** Total room the chip adds across, in unscaled px. */
	public static float extra(boolean padded, int pad, int slack) {
		return padded ? pad * 2 + slack : 0f;
	}

	/** Room on one side — half of {@link #extra}. */
	public static float inset(boolean padded, int pad, int slack) {
		return padded ? pad + slack / 2f : 0f;
	}

	/** {@link #inset} for the unscaled integer paths, keeping their existing truncation. */
	public static int insetInt(boolean padded, int pad, int slack) {
		return padded ? pad + slack / 2 : 0;
	}

	public float extraW(int pad) {
		return extra(padsContent(), pad, bgWidth);
	}

	public float extraH(int pad) {
		return extra(padsContent(), pad, bgHeight);
	}

	public float insetX(int pad) {
		return inset(padsContent(), pad, bgWidth);
	}

	public float insetY(int pad) {
		return inset(padsContent(), pad, bgHeight);
	}

	public int insetXi(int pad) {
		return insetInt(padsContent(), pad, bgWidth);
	}

	public int insetYi(int pad) {
		return insetInt(padsContent(), pad, bgHeight);
	}

	/**
	 * Restores where the module sits from a freshly defaulted copy, leaving every other setting
	 * alone — the header's reset-position button.
	 *
	 * <p>Scale stays: it is how big the module is, not where it is, and losing a carefully sized
	 * module to a mis-click on a position button would be a nasty surprise. Modules whose real
	 * position lives in {@link PartPos} parts or per-entry positions override this, otherwise the
	 * button would appear to do nothing for them.
	 */
	public void applyPositionFrom(ModuleSettings o) {
		anchor = o.anchor;
		offsetX = o.offsetX;
		offsetY = o.offsetY;
	}

	/** Copies the appearance (not enabled/position/scale) from a template — APPLY TO ALL. */
	public void applyAppearanceFrom(ModuleSettings o) {
		textShadow = o.textShadow;
		font = o.font;
		background = o.background;
		bgColor.copyFrom(o.bgColor);
		bgBlur = o.bgBlur;
		bgRounded = o.bgRounded;
		bgRadius = o.bgRadius;
		bgWidth = o.bgWidth;
		bgHeight = o.bgHeight;
		border = o.border;
		borderThickness = o.borderThickness;
		borderColor.copyFrom(o.borderColor);
		// splitColors is deliberately NOT copied. Whether a module shows one colour or two is a
		// property of that module, not of the template, and stamping it across everything turned
		// single-colour modules into two-colour ones (and back) as a side effect of applying a
		// palette. Both colours are still written, so whichever mode a module is in gets its own.
		labelColor.copyFrom(o.labelColor);
		valueColor.copyFrom(o.valueColor);
	}

	/**
	 * A text color with an effect: STATIC, CHROMA (hue cycle — SHIFT moves the whole text
	 * together, WAVE travels across the characters), GRADIENT (A→B across the text), or
	 * GRADIENT_WAVE (A→B traveling back and forth across the text).
	 */
	public static class ColorSpec {
		public Mode mode = Mode.STATIC;
		public ChromaType chromaType = ChromaType.WAVE;
		public String a = "#FFFFFF";
		public String b = "#F35D12";
		public float speed = 1.0f;
		/**
		 * How far the WAVE effects travel across the text: 1.0 = one full A→B span
		 * (or half the hue wheel for chroma) across the whole text.
		 */
		public float spread = 1.0f;

		public ColorSpec() {
		}

		public ColorSpec(String a) {
			this.a = a;
		}

		// The two hex strings, already parsed, remembered against the string they came from.
		//
		// colorAt is asked once PER CHARACTER of every string the HUD draws, and argbAt asked
		// twice over on top of that. Colors.parse takes a substring and runs it through parseInt
		// inside a try, so leaving it uncached meant hundreds of throwaway strings a frame for a
		// pair of values that change only when somebody edits them.
		//
		// Compared by identity on purpose: a string that has not been reassigned is the same
		// object, and a reassignment -- from the picker, from Gson, from copyFrom -- brings a new
		// one. Transient, so none of it is written to the config.
		private transient String parsedAFrom;
		private transient int parsedA;
		private transient String parsedBFrom;
		private transient int parsedB;

		private int a() {
			if (a != parsedAFrom) {
				parsedA = Colors.parse(a, 0xFFFFFFFF);
				parsedAFrom = a;
			}
			return parsedA;
		}

		private int b() {
			if (b != parsedBFrom) {
				parsedB = Colors.parse(b, 0xFFF35D12);
				parsedBFrom = b;
			}
			return parsedB;
		}

		/** Color for character {@code index} of {@code total} rendered characters. */
		public int colorAt(int index, int total) {
			float phase = total <= 1 ? 0f : (float) index / (total - 1);
			return switch (mode) {
				case STATIC -> a();
				case CHROMA -> Colors.chroma(speed,
						chromaType == ChromaType.WAVE ? phase * 0.5f * spread : 0f);
				case GRADIENT -> Colors.lerp(a(), b(), phase);
				case GRADIENT_WAVE -> Colors.lerp(a(), b(),
						Colors.waveT(speed, phase * 0.5f * spread));
			};
		}

		/** Like {@link #colorAt} but the alpha byte always comes from {@code a} (opacity survives effects). */
		public int argbAt(int index, int total) {
			int alpha = a() & 0xFF000000;
			return alpha | (colorAt(index, total) & 0xFFFFFF);
		}

		/**
		 * Single evaluated chrome color (buttons, borders, flat panels). A flat surface has
		 * one sample point, so the WAVE variants get distinct temporal shapes instead:
		 * chroma WAVE ping-pongs through half the hue wheel (vs SHIFT's continuous rotation)
		 * and GRADIENT_WAVE oscillates between A and B. GRADIENT settles on the midpoint.
		 */
		public int chrome() {
			int alpha = a() & 0xFF000000;
			int rgb;
			if (mode == Mode.GRADIENT) {
				rgb = Colors.lerp(a(), b(), 0.5f);
			} else if (mode == Mode.CHROMA && chromaType == ChromaType.WAVE) {
				rgb = Colors.hsv(Colors.waveT(speed, 0f) * 0.5f, 0.8f, 1f);
			} else {
				rgb = colorAt(0, 1);
			}
			return alpha | (rgb & 0xFFFFFF);
		}

		public void copyFrom(ColorSpec other) {
			this.mode = other.mode;
			this.chromaType = other.chromaType;
			this.a = other.a;
			this.b = other.b;
			this.speed = other.speed;
			this.spread = other.spread;
		}

		public enum Mode {
			STATIC, CHROMA, GRADIENT, GRADIENT_WAVE;

			public String label() {
				return switch (this) {
					case STATIC -> "Static";
					case CHROMA -> "Chroma";
					case GRADIENT -> "Gradient";
					case GRADIENT_WAVE -> "Gradient Wave";
				};
			}
		}

		public enum ChromaType {
			SHIFT, WAVE
		}
	}

	/**
	 * 9-point screen anchor. A module stores which screen region it lives in plus a pixel
	 * offset, so it stays glued to that region when the window or GUI scale changes.
	 */
	/** Independent position for a module sub-part (separated coords / armor pieces). */
	public static class PartPos {
		public Anchor anchor = Anchor.TOP_LEFT;
		public float ox = 5;
		public float oy = 5;
		/** Per-part scale factor on top of the module scale (editor scroll wheel). */
		public float scale = 1f;

		public PartPos() {
		}

		public PartPos(Anchor anchor, float ox, float oy) {
			this.anchor = anchor;
			this.ox = ox;
			this.oy = oy;
		}

		/** Where this part sits, without its scale — see {@link ModuleSettings#applyPositionFrom}. */
		public void copyPositionFrom(PartPos o) {
			anchor = o.anchor;
			ox = o.ox;
			oy = o.oy;
		}
	}

	public enum Anchor {
		TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
		MIDDLE_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1f, 0.5f),
		BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

		public final float fx;
		public final float fy;

		Anchor(float fx, float fy) {
			this.fx = fx;
			this.fy = fy;
		}

		/** Nearest anchor for an element centred at (cx, cy) on a w×h screen (thirds). */
		public static Anchor nearest(float cx, float cy, float w, float h) {
			int ix = cx < w / 3f ? 0 : (cx < w * 2f / 3f ? 1 : 2);
			int iy = cy < h / 3f ? 0 : (cy < h * 2f / 3f ? 1 : 2);
			return values()[iy * 3 + ix];
		}
	}
}
