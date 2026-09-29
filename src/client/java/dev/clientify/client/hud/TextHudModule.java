package dev.clientify.client.hud;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.MenuFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A text HUD chip of one or more LINES built from label/value segments with per-character
 * ColorSpec effects. Font is switchable: CLIENTIFY = the pixel-perfect Roboto atlas (crisp at
 * any module scale), MINECRAFT = the vanilla font (pose-scaled, the classic look). Background
 * can be square or rounded with a custom radius, plus an optional effect-colored border.
 * A module with no content shows its name as a placeholder while the editor is open.
 */
public abstract class TextHudModule extends HudModule {
	protected static final int PAD_X = 5;
	protected static final int PAD_Y = 3;
	protected static final int LINE_GAP = 2;
	private static final float BASE_ASCENT = 7f; // GUI px at module scale 1 (≈ vanilla text)
	/** The Clientify atlas font runs slightly larger than vanilla at the same slider value. */
	private static final float CLIENTIFY_FONT_FACTOR = 1.35f;
	/**
	 * Slack the vanilla font leaves past its ink: 1px of letter spacing right of the last glyph,
	 * and 1px under the line (glyphs are 8px tall in a 9px line). Measuring by advance and then
	 * padding both sides equally therefore parks the text up and to the left inside its chip, so
	 * the content size drops it. The text itself stays on whole pixels — only the chip shrinks.
	 */
	private static final int TRAILING = 1;

	protected TextHudModule(String id, String displayName) {
		super(id, displayName);
	}

	/** A run of characters colored as either label or value. */
	public record Seg(String text, boolean isValue) {
	}

	/** Static part, e.g. "FPS". */
	protected abstract String label(Minecraft mc);

	/** Live part, e.g. "240". */
	protected abstract String value(Minecraft mc);

	/** Default "value label" order (Lunar style); template modules override this. */
	protected List<Seg> segments(Minecraft mc) {
		List<Seg> segs = new ArrayList<>(2);
		segs.add(new Seg(value(mc), true));
		segs.add(new Seg(" " + label(mc), false));
		return segs;
	}

	/** Multi-line modules (coords) override this; default is the single {@link #segments} line. */
	protected List<List<Seg>> lineSegments(Minecraft mc) {
		List<Seg> segs = segments(mc);
		return segs.isEmpty() ? List.of() : List.of(segs);
	}

	/**
	 * Text used for WIDTH measurement of a segment. Modules with a jittery value (FPS, ping)
	 * override this to reserve a static width; the drawn text is centered in the reserve.
	 */
	protected String measureText(Seg seg) {
		return seg.text();
	}

	// Built once per render pass: laying a chip out asks for its size two or three times before
	// drawing it, and each answer used to rebuild the segments and re-measure every one of them.
	private int cacheToken = -1;
	private List<List<Seg>> cachedLines;
	private float cachedWidth;
	private float cachedHeight;

	private void ensureLaidOut(Minecraft mc) {
		int token = HudFrame.token();
		if (token == cacheToken && cachedLines != null) {
			return;
		}
		cachedLines = buildLines(mc);
		cachedWidth = measureWidth(mc, cachedLines);
		cachedHeight = measureHeight(mc, cachedLines);
		cacheToken = token;
	}

	/** Lines, substituting a name placeholder while empty in the editor (keeps it draggable). */
	private List<List<Seg>> effectiveLines(Minecraft mc) {
		ensureLaidOut(mc);
		return cachedLines;
	}

	private List<List<Seg>> buildLines(Minecraft mc) {
		List<List<Seg>> lines = lineSegments(mc);
		if (lines.isEmpty() && mc.screen instanceof HudEditorScreen) {
			return List.of(List.of(new Seg(displayName(), false)));
		}
		return lines;
	}

	private float ascent() {
		return BASE_ASCENT * settings().scale * CLIENTIFY_FONT_FACTOR;
	}

	private ModuleSettings.ColorSpec specFor(Seg seg) {
		ModuleSettings s = settings();
		return seg.isValue() && s.splitColors ? s.valueColor : s.labelColor;
	}

	private int radius() {
		ModuleSettings s = settings();
		return s.bgRounded ? Math.max(0, s.bgRadius) : 0;
	}

	@Override
	public int outlineRadius() {
		return Math.round(radius() * settings().scale);
	}

	/** Measured width of one line in the current font, in the font's native units. */
	private float lineWidth(Minecraft mc, List<Seg> line, boolean measured) {
		ModuleSettings s = settings();
		float w = 0;
		for (Seg seg : line) {
			String text = measured ? measureText(seg) : seg.text();
			w += switch (s.font) {
				case MINECRAFT -> mc.font.width(text);
				case SMALL_CAPS -> SmallCaps.width(mc.font, text);
				case CLIENTIFY -> MenuFont.width(text, ascent(), 0f);
			};
		}
		return w;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		ensureLaidOut(mc);
		return cachedWidth;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		ensureLaidOut(mc);
		return cachedHeight;
	}

	private float measureWidth(Minecraft mc, List<List<Seg>> lines) {
		ModuleSettings s = settings();
		if (lines.isEmpty()) {
			return 0;
		}
		float max = 0;
		for (List<Seg> line : lines) {
			max = Math.max(max, lineWidth(mc, line, true));
		}
		if (s.font.vanilla()) {
			return s.extraW(PAD_X) + max - TRAILING;
		}
		return s.extraW(PAD_X) + max / s.scale;
	}

	private float measureHeight(Minecraft mc, List<List<Seg>> lines) {
		ModuleSettings s = settings();
		int n = lines.size();
		if (n == 0) {
			return 0;
		}
		if (s.font.vanilla()) {
			return s.extraH(PAD_Y) + n * mc.font.lineHeight + (n - 1) * LINE_GAP - TRAILING;
		}
		return s.extraH(PAD_Y) + n * (MenuFont.lineHeight(ascent()) / s.scale) + (n - 1) * LINE_GAP;
	}

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		ModuleSettings s = settings();
		List<List<Seg>> lines = effectiveLines(mc);
		if (lines.isEmpty()) {
			return;
		}
		int total = 0;
		for (List<Seg> line : lines) {
			for (Seg seg : line) {
				total += seg.text().length();
			}
		}

		if (s.font.vanilla()) {
			renderVanilla(g, mc, s, lines, total);
			return;
		}

		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		int x = Math.round(r.x());
		int y = Math.round(r.y());
		int w = Math.round(r.w());
		int h = Math.round(r.h());
		drawChromeScreen(g, mc, x, y, w, h, s.scale);

		float lineAdvance = MenuFont.lineHeight(ascent()) + LINE_GAP * s.scale;
		float ty = y + s.insetY(PAD_Y) * s.scale;
		int charBase = 0;
		for (List<Seg> line : lines) {
			float drawnW = lineWidth(mc, line, false);
			float measuredW = lineWidth(mc, line, true);
			float tx = x + s.insetX(PAD_X) * s.scale + (measuredW - drawnW) / 2f;
			for (Seg seg : line) {
				ModuleSettings.ColorSpec spec = specFor(seg);
				int base = charBase;
				int totalChars = total;
				MenuFont.draw(g, seg.text(), tx, ty, i -> spec.argbAt(base + i, totalChars),
						ascent(), s.textShadow, 0f);
				tx += MenuFont.width(seg.text(), ascent(), 0f);
				charBase += seg.text().length();
			}
			ty += lineAdvance;
		}
	}

	/** Vanilla-font path: everything drawn in unscaled space under a pose scale. */
	private void renderVanilla(GuiGraphicsExtractor g, Minecraft mc, ModuleSettings s, List<List<Seg>> lines, int total) {
		Font font = mc.font;
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		g.pose().pushMatrix();
		g.pose().translate(r.x(), r.y());
		g.pose().scale(s.scale, s.scale);

		int w = Math.round(unscaledWidth(mc));
		int h = Math.round(unscaledHeight(mc));
		drawChromeLocal(g, mc, w, h);

		int ty = s.insetYi(PAD_Y);
		int charBase = 0;
		for (List<Seg> line : lines) {
			int drawnW = Math.round(lineWidth(mc, line, false));
			int measuredW = Math.round(lineWidth(mc, line, true));
			int x = s.insetXi(PAD_X) + (measuredW - drawnW) / 2;
			for (Seg seg : line) {
				ModuleSettings.ColorSpec spec = specFor(seg);
				String text = seg.text();
				int base = charBase;
				if (s.font == ModuleSettings.FontMode.SMALL_CAPS) {
					SmallCaps.draw(g, font, text, x, ty, i -> spec.argbAt(base + i, total), s.textShadow);
					x += Math.round(SmallCaps.width(font, text));
				} else {
					for (int i = 0; i < text.length(); i++) {
						String ch = String.valueOf(text.charAt(i));
						g.text(font, ch, x, ty, spec.argbAt(base + i, total), s.textShadow);
						x += font.width(ch);
					}
				}
				charBase += text.length();
			}
			ty += font.lineHeight + LINE_GAP;
		}
		g.pose().popMatrix();
	}
}
