package dev.clientify.client.gui;

import dev.clientify.client.util.Colors;
import dev.clientify.client.util.Draw;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Clientify's dark palette + small chrome helpers. Dark theme only; the theme colors
 * (accent, panel, cards) come from {@link dev.clientify.client.config.GlobalSettings} as
 * full ColorSpecs, so the menu itself can run chroma/gradient effects. Every surface
 * carries some transparency — only text and icons are fully opaque.
 */
public final class Ui {
	public static final int ACCENT = 0xFFF35D12;       // brand default (fallback)
	public static final int TEXT = 0xFFEDEDF2;
	public static final int TEXT_DIM = 0xFF9A9AA5;
	public static final int TRACK = 0x30FFFFFF;
	public static final int HAIRLINE = 0x12FFFFFF;     // 7% hairline

	// Module state (Lunar semantics): green = enabled, muted red = disabled.
	public static final int GOOD = 0xFF7CC88B;         // enabled gear / green signal
	public static final int GOOD_BORDER = 0xB33FA65A;
	public static final int BAD_BORDER = 0x808A4444;

	private Ui() {
	}

	private static dev.clientify.client.config.GlobalSettings g() {
		return dev.clientify.client.config.ClientifyConfig.global();
	}

	/** Action/brand color (buttons, active chips, logo). Alpha comes from the user's hex. */
	public static int accent() {
		return g().accentColor.chrome();
	}

	/** Accent at reduced opacity for idle primary surfaces. */
	public static int accentDim() {
		int c = accent();
		int a = Math.round(((c >>> 24) & 0xFF) * 0.85f);
		return (a << 24) | (c & 0xFFFFFF);
	}

	public static int accentSoft() {
		return 0x40000000 | (accent() & 0xFFFFFF);
	}

	public static int panel() {
		return g().panelColor.chrome();
	}

	public static int card() {
		return 0x10FFFFFF;
	}

	public static int cardHover() {
		return 0x1EFFFFFF;
	}

	/** Body text: pixel-perfect glyph-atlas font (falls back to vanilla internally). */
	public static void str(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		MenuFont.draw(g, s, x, y, color);
	}

	/** Body text with a subtle shadow (HUD chips over the world). */
	public static void strShadow(GuiGraphicsExtractor g, String s, float x, float y, int color) {
		MenuFont.draw(g, s, x, y, color, MenuFont.Size.BODY, true);
	}

	/** Title text (larger ascent, same font). */
	public static void title(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		MenuFont.draw(g, s, x, y, color, MenuFont.Size.TITLE, false);
	}

	/** On-screen width of {@code s} in the body font. */
	public static int sw(String s) {
		return MenuFont.width(s, MenuFont.Size.BODY);
	}

	/** On-screen width of {@code s} in the title font. */
	public static int swTitle(String s) {
		return MenuFont.width(s, MenuFont.Size.TITLE);
	}

	/** Letterspaced uppercase micro text (Lunar-style labels: tabs, chips, buttons). */
	public static void caps(GuiGraphicsExtractor g, String s, int x, int y, int color, float tracking) {
		MenuFont.draw(g, s.toUpperCase(java.util.Locale.ROOT), x, y, color, MenuFont.Size.BODY, false, tracking);
	}

	/** Width of {@link #caps} text. */
	public static int capsW(String s, float tracking) {
		return MenuFont.width(s.toUpperCase(java.util.Locale.ROOT), MenuFont.Size.BODY, tracking);
	}

	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
		if (hovered) {
			Draw.smoothRoundedBordered(g, x, y, w, h, Draw.R_SMALL, cardHover(), accentSoft(), 1);
		} else {
			Draw.smoothRounded(g, x, y, w, h, Draw.R_SMALL, card());
		}
	}

	/** Small pill switch (Lunar-style toggle), drawn at its natural 22×11 size. */
	public static void pill(GuiGraphicsExtractor g, int x, int y, boolean on) {
		int w = 22;
		int h = 11;
		Draw.smoothRounded(g, x, y, w, h, h / 2, on ? accent() : TRACK);
		int knob = h - 4;
		int knobX = on ? x + w - knob - 2 : x + 2;
		Draw.smoothRounded(g, knobX, y + 2, knob, knob, knob / 2, 0xFFF4F4F6);
	}
}
