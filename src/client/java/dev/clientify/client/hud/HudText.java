package dev.clientify.client.hud;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.MenuFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws HUD text in SCREEN space, honoring a module's font (Clientify atlas or vanilla) and
 * applying a {@link ColorSpec} PER CHARACTER so wave/gradient effects actually animate. Used
 * by the icon modules (armor, effects) which mix sprites with text and can't sit inside the
 * pose-scaled path {@link TextHudModule} uses.
 *
 * <p>Positions/sizes are in screen pixels already multiplied by the module scale; call
 * {@link #width}/{@link #lineHeight} to lay out, then {@link #draw} at the final coords.
 *
 * <p>Every method has an overload taking an explicit {@code scale}. Modules that draw parts at
 * their own size (armor pieces, coordinate rows, title vs subtitle, zoomed waypoint chips)
 * MUST use those — the settings object is persisted config, so using it as a scratch variable
 * risks writing a temporary value to disk if a draw call throws mid-render.
 */
public final class HudText {
	public static final float BASE_ASCENT = 7f;        // GUI px at scale 1 (≈ vanilla)
	public static final float CLIENTIFY_FACTOR = 1.35f; // Clientify atlas runs a touch larger

	private HudText() {
	}

	private static float ascent(float scale) {
		return BASE_ASCENT * scale * CLIENTIFY_FACTOR;
	}

	/** Screen-space width of {@code text} in this module's font at its scale. */
	public static float width(Minecraft mc, ModuleSettings s, String text) {
		return width(mc, s, text, s.scale);
	}

	/** Screen-space width at an explicit scale (parts sized independently of the module). */
	public static float width(Minecraft mc, ModuleSettings s, String text, float scale) {
		if (s.font == ModuleSettings.FontMode.SMALL_CAPS) {
			return SmallCaps.width(mc.font, text) * scale;
		}
		if (s.font == ModuleSettings.FontMode.MINECRAFT) {
			return mc.font.width(text) * scale;
		}
		return MenuFont.width(text, ascent(scale), 0f);
	}

	/**
	 * Slack the font leaves past its ink, in screen px: the vanilla font reserves 1px of letter
	 * spacing right of the last glyph and 1px under the line, so a box measured by advance and
	 * padded evenly puts its text up and to the left. Subtract this from a content size to get
	 * even padding without nudging the glyphs off whole pixels. The Clientify atlas has none.
	 */
	public static float trailing(ModuleSettings s, float scale) {
		return s.font.vanilla() ? scale : 0f;
	}

	/** Screen-space line height in this module's font at its scale. */
	public static float lineHeight(Minecraft mc, ModuleSettings s) {
		return lineHeight(mc, s, s.scale);
	}

	/** Screen-space line height at an explicit scale. */
	public static float lineHeight(Minecraft mc, ModuleSettings s, float scale) {
		if (s.font.vanilla()) {
			return mc.font.lineHeight * scale; // small caps share the vanilla line box
		}
		return MenuFont.lineHeight(ascent(scale));
	}

	/**
	 * Draws {@code text} at screen (x,y) using a single flat color. With the vanilla font this
	 * draws the whole string in one call, so the drop shadow renders exactly like vanilla's
	 * (per-character drawing gives each glyph its own shadow pass, which reads wrong).
	 */
	public static void draw(GuiGraphics g, Minecraft mc, ModuleSettings s, String text, float x, float y, int color) {
		draw(g, mc, s, text, x, y, color, s.scale);
	}

	/** Flat-colored draw at an explicit scale. */
	public static void draw(GuiGraphics g, Minecraft mc, ModuleSettings s, String text, float x, float y,
			int color, float scale) {
		if (text.isEmpty()) {
			return;
		}
		if (s.font == ModuleSettings.FontMode.SMALL_CAPS) {
			draw(g, mc, s, text, x, y, i -> color, text.length(), scale);
			return;
		}
		if (s.font == ModuleSettings.FontMode.MINECRAFT) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale, scale);
			g.drawString(mc.font, text, 0, 0, color, s.textShadow);
			g.pose().popMatrix();
			return;
		}
		draw(g, mc, s, text, x, y, i -> color, text.length(), scale);
	}

	/**
	 * Draws {@code text} at screen (x,y), coloring character {@code i} of the whole string via
	 * {@code spec} across [0, total) (so a wave spans the whole run).
	 */
	public static void draw(GuiGraphics g, Minecraft mc, ModuleSettings s, String text, float x, float y,
			ColorSpec spec, int charBase, int total) {
		draw(g, mc, s, text, x, y, i -> spec.argbAt(charBase + i, total), text.length(), s.scale);
	}

	/** Per-character spec draw at an explicit scale. */
	public static void draw(GuiGraphics g, Minecraft mc, ModuleSettings s, String text, float x, float y,
			ColorSpec spec, int charBase, int total, float scale) {
		draw(g, mc, s, text, x, y, i -> spec.argbAt(charBase + i, total), text.length(), scale);
	}

	/** A Component flattened to plain chars plus the per-character color the server gave it. */
	public record Styled(String text, int[] colors) {
	}

	/**
	 * Flattens a {@link net.minecraft.network.chat.Component} keeping each character's own
	 * color, so server formatting (team colors, §-codes) survives even in the Clientify font.
	 * Characters with no explicit color fall back to {@code defaultColor}.
	 */
	public static Styled styled(net.minecraft.network.chat.Component text, int defaultColor) {
		StringBuilder sb = new StringBuilder();
		java.util.List<Integer> colors = new java.util.ArrayList<>();
		int alpha = defaultColor & 0xFF000000;
		text.getVisualOrderText().accept((index, style, codePoint) -> {
			int color = defaultColor;
			if (style.getColor() != null) {
				color = alpha | (style.getColor().getValue() & 0xFFFFFF);
			}
			int before = sb.length();
			sb.appendCodePoint(codePoint);
			for (int i = before; i < sb.length(); i++) {
				colors.add(color);
			}
			return true;
		});
		int[] out = new int[colors.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = colors.get(i);
		}
		return new Styled(sb.toString(), out);
	}

	/** Width of a Component in this module's font (screen space), as {@link #drawComponent} draws it. */
	public static float width(Minecraft mc, ModuleSettings s, net.minecraft.network.chat.Component text) {
		return width(mc, s, text, s.scale, null);
	}

	/** Width of a Component at an explicit scale, as {@link #drawComponent} draws it. */
	public static float width(Minecraft mc, ModuleSettings s, net.minecraft.network.chat.Component text,
			float scale) {
		return width(mc, s, text, scale, null);
	}

	/**
	 * Width of a Component as {@link #drawComponent} will draw it with this colour override.
	 *
	 * <p>In the Minecraft font the Component goes to vanilla whole - bold, italics and server fonts
	 * included - so it has to be measured whole too. Measuring its plain string instead put a bold
	 * title or subtitle off centre by the bold letters' extra width: 50px at GUI scale 2 for a
	 * 26-letter subtitle. A per-character override draws the plain string, and is measured as one.
	 */
	public static float width(Minecraft mc, ModuleSettings s, net.minecraft.network.chat.Component text,
			float scale, java.util.function.IntUnaryOperator override) {
		Styled st = styled(text, 0xFFFFFFFF);
		if (drawsWhole(s, override, st.colors().length)) {
			return mc.font.width(text) * scale;
		}
		return width(mc, s, st.text(), scale);
	}

	/**
	 * True when {@link #drawComponent} hands the Component to vanilla whole; false when it has to
	 * draw it a character at a time. One rule for both, so the measure and the drawing agree.
	 */
	private static boolean drawsWhole(ModuleSettings s, java.util.function.IntUnaryOperator override,
			int length) {
		boolean varyingOverride = override != null && length > 1
				&& override.applyAsInt(0) != override.applyAsInt(length - 1);
		return s.font == ModuleSettings.FontMode.MINECRAFT && !varyingOverride;
	}

	/**
	 * Draws a Component preserving the server's per-character colors. {@code override}, when
	 * non-null, replaces the color of characters the server did NOT color itself.
	 */
	public static void drawComponent(GuiGraphics g, Minecraft mc, ModuleSettings s,
			net.minecraft.network.chat.Component text, float x, float y, int defaultColor,
			java.util.function.IntUnaryOperator override) {
		drawComponent(g, mc, s, text, x, y, defaultColor, override, s.scale);
	}

	/** Component draw preserving server colors, at an explicit scale. */
	public static void drawComponent(GuiGraphics g, Minecraft mc, ModuleSettings s,
			net.minecraft.network.chat.Component text, float x, float y, int defaultColor,
			java.util.function.IntUnaryOperator override, float scale) {
		Styled st = styled(text, defaultColor);
		int[] colors = st.colors();
		// A per-character override (gradient/wave) is the only case that needs manual drawing.
		if (drawsWhole(s, override, colors.length)) {
			// Vanilla renders the Component's own color spans AND the drop shadow itself.
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale, scale);
			int base = override != null ? override.applyAsInt(0) : defaultColor;
			g.drawString(mc.font, text, 0, 0, base, s.textShadow);
			g.pose().popMatrix();
			return;
		}
		draw(g, mc, s, st.text(), x, y, i -> {
			if (i >= colors.length) {
				return defaultColor;
			}
			// Server-colored characters keep their color; the rest take our override.
			return colors[i] != defaultColor || override == null ? colors[i] : override.applyAsInt(i);
		}, st.text().length(), scale);
	}

	/**
	 * Whether every character comes out the same colour.
	 *
	 * <p>Sampled at the ends and the middle rather than asked for every character: the effects that
	 * vary do it smoothly across the string, so no two of those three agree. A static colour, and a
	 * chroma shift that moves the whole string together, answer the same at all three.
	 */
	private static boolean uniform(java.util.function.IntUnaryOperator colorByIndex, int length,
			int first) {
		if (length <= 1) {
			return true;
		}
		return first == colorByIndex.applyAsInt(length - 1)
				&& first == colorByIndex.applyAsInt(length / 2);
	}

	/** Draws {@code text}, coloring by an arbitrary index→ARGB function (length = text.length()). */
	public static void draw(GuiGraphics g, Minecraft mc, ModuleSettings s, String text, float x, float y,
			java.util.function.IntUnaryOperator colorByIndex, int ignoredLen) {
		draw(g, mc, s, text, x, y, colorByIndex, ignoredLen, s.scale);
	}

	/** Index→ARGB draw at an explicit scale. */
	public static void draw(GuiGraphics g, Minecraft mc, ModuleSettings s, String text, float x, float y,
			java.util.function.IntUnaryOperator colorByIndex, int ignoredLen, float scale) {
		if (text.isEmpty()) {
			return;
		}
		if (s.font == ModuleSettings.FontMode.SMALL_CAPS) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale, scale);
			SmallCaps.draw(g, mc.font, text, 0, 0, colorByIndex, s.textShadow);
			g.pose().popMatrix();
			return;
		}
		if (s.font == ModuleSettings.FontMode.MINECRAFT) {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.pose().scale(scale, scale);
			int first = colorByIndex.applyAsInt(0);
			if (uniform(colorByIndex, text.length(), first)) {
				// One colour the whole way along, which is what a static colour means and what
				// most text is. Vanilla draws the string in one go, so it measures and shadows it
				// as one rather than a character at a time.
				g.drawString(mc.font, text, 0, 0, first, s.textShadow);
			} else {
				int lx = 0;
				for (int i = 0; i < text.length(); i++) {
					String ch = String.valueOf(text.charAt(i));
					g.drawString(mc.font, ch, lx, 0, colorByIndex.applyAsInt(i), s.textShadow);
					lx += mc.font.width(ch);
				}
			}
			g.pose().popMatrix();
		} else {
			MenuFont.draw(g, text, x, y, colorByIndex, ascent(scale), s.textShadow, 0f);
		}
	}
}
