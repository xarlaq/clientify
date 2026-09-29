package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import dev.clientify.client.util.Colors;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Potion effects with timers, rendered in screen space so the module font and wave/gradient
 * color effects both work. Vertical or horizontal; background None / Per-Icon (HUD slot) /
 * Full (the vanilla inventory effect strip, which nine-slices cleanly). Roman / number / no
 * amplifier; optional blink and red color-shift as an effect nears expiry.
 */
public class EffectsHudModule extends HudModule {
	public enum Mode {
		VERTICAL, HORIZONTAL
	}

	public enum VanillaBg {
		NONE, SLOT, FULL
	}

	public enum LevelStyle {
		ROMAN, NUMBER, NONE
	}

	/** Where the timer/name sits relative to the effect icon, as in the armor HUD. */
	public enum TextPos {
		RIGHT, LEFT, ABOVE, UNDER;

		public String label() {
			return switch (this) {
				case RIGHT -> "Right";
				case LEFT -> "Left";
				case ABOVE -> "Above";
				case UNDER -> "Under";
			};
		}
	}

	private static final Identifier SLOT_BG = Identifier.withDefaultNamespace("hud/effect_background");
	private static final Identifier INV_BG = Identifier.withDefaultNamespace("container/inventory/effect_background");
	private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
	private static final int PAD = 3;
	private static final int ICON = 18;

	public static class Settings extends ModuleSettings {
		public Mode mode = Mode.VERTICAL;
		public VanillaBg vanillaBg = VanillaBg.NONE;
		public LevelStyle levels = LevelStyle.ROMAN;
		public TextPos textPos = TextPos.RIGHT;
		public boolean blink = false;
		public float blinkStart = 5f;
		public boolean colorChange = false;
		public float colorChangeStart = 5f;
		/** Suppress vanilla's own top-right potion icons while this module is on. */
		public boolean hideVanilla = true;

		public Settings() {
			anchor = Anchor.TOP_RIGHT;
			offsetX = -5;
			offsetY = 22;
			background = false;
		}
	}

	private static EffectsHudModule instance;

	/** True when vanilla's own potion icons should be suppressed (read by GuiEffectsMixin). */
	public static boolean hideVanillaActive() {
		EffectsHudModule m = instance;
		return m != null && m.isEnabled() && m.settings() instanceof Settings s && s.hideVanilla;
	}

	public EffectsHudModule() {
		super("effects", "Effects HUD");
		instance = this;
	}

	@Override
	public String description() {
		return "Shows your active potion effects with timers.";
	}

	@Override
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		// List stacks effects with their names; Compact is a row of icons with just timers.
		rows.add(screen.cycleRow("Layout",
				() -> s.mode == Mode.VERTICAL ? "List" : "Compact",
				() -> s.mode = cycle(s.mode, -1), () -> s.mode = cycle(s.mode, 1),
				() -> s.mode = Mode.VERTICAL));
		rows.add(screen.toggle("Hide Vanilla Indicator", () -> s.hideVanilla, v -> s.hideVanilla = v,
				() -> s.hideVanilla = true));
		rows.add(screen.cycleRow("Vanilla Background",
				() -> switch (s.vanillaBg) {
					case NONE -> "None";
					case SLOT -> "Per Icon";
					case FULL -> "Full";
				},
				() -> s.vanillaBg = cycle(s.vanillaBg, -1), () -> s.vanillaBg = cycle(s.vanillaBg, 1),
				() -> s.vanillaBg = VanillaBg.NONE));
		rows.add(screen.cycleRow("Text Position", () -> s.textPos.label(),
				() -> s.textPos = cycle(s.textPos, -1), () -> s.textPos = cycle(s.textPos, 1),
				() -> s.textPos = TextPos.RIGHT));
		rows.add(screen.cycleRow("Amplifier",
				() -> switch (s.levels) {
					case ROMAN -> "Roman";
					case NUMBER -> "Number";
					case NONE -> "None";
				},
				() -> s.levels = cycle(s.levels, -1), () -> s.levels = cycle(s.levels, 1),
				() -> s.levels = LevelStyle.ROMAN));
		rows.add(screen.toggle("Blink When Low", () -> s.blink, v -> s.blink = v, () -> s.blink = false));
		if (s.blink) {
			rows.add(screen.sliderRow("Blink At", 1f, 30f, 1f, () -> s.blinkStart,
					v -> s.blinkStart = v, "%.0fs", () -> s.blinkStart = 5f));
		}
		rows.add(screen.toggle("Color Shift When Low", () -> s.colorChange, v -> s.colorChange = v,
				() -> s.colorChange = false));
		if (s.colorChange) {
			rows.add(screen.sliderRow("Color Shift At", 1f, 30f, 1f, () -> s.colorChangeStart,
					v -> s.colorChangeStart = v, "%.0fs", () -> s.colorChangeStart = 5f));
		}
	}

	// ---- geometry per background mode (unscaled) ----

	/** The square that holds the icon: a 24 slot frame for SLOT, otherwise the bare icon. */
	private int iconCell(Settings s) {
		return s.vanillaBg == VanillaBg.SLOT ? 24 : ICON;
	}

	/** Inset needed to center the 18px icon inside its cell. */
	private float cellInset(Settings s) {
		return (iconCell(s) - ICON) / 2f;
	}

	private int textGap(Settings s) {
		// The full strip's icon frame wants more breathing room before the text.
		return s.vanillaBg == VanillaBg.FULL ? 8 : 4;
	}

	/** Uniform inset the full-strip background keeps around its content (its border art). */
	private int stripPadX(Settings s) {
		return s.vanillaBg == VanillaBg.FULL ? 7 : 0;
	}

	/** 7 so an icon-height block comes out 32 tall, matching the vanilla inventory strip. */
	private int stripPadY(Settings s) {
		return s.vanillaBg == VanillaBg.FULL ? 7 : 0;
	}

	private int rowGap(Settings s) {
		return s.vanillaBg == VanillaBg.FULL ? 1 : 2;
	}

	// ---- effect data ----

	// Sorting the effects and sizing their labels is the same answer for the whole pass, and the
	// layout asks for it five times before anything is drawn.
	private int effectsToken = -1;
	private int blockToken = -1;
	private List<MobEffectInstance> cachedEffects;
	private float[] cachedBlock;

	private List<MobEffectInstance> effects(Minecraft mc) {
		int token = dev.clientify.client.hud.HudFrame.token();
		if (token != effectsToken || cachedEffects == null) {
			cachedEffects = mc.player == null ? List.of()
					: mc.player.getActiveEffects().stream().sorted(Comparator.reverseOrder()).toList();
			if (cachedEffects.isEmpty() && mc.screen instanceof HudEditorScreen) {
				cachedEffects = SAMPLE;
			}
			effectsToken = token;
		}
		return cachedEffects;
	}

	/**
	 * What the editor shows when you have no potions running. Real effects rather than a "no
	 * effects" label, so the chip is the size and shape it will actually be — which is the whole
	 * reason to be looking at it in the editor. Durations are long enough not to trip the
	 * blink-when-low warning while you are placing it.
	 */
	private static final List<MobEffectInstance> SAMPLE = List.of(
			new MobEffectInstance(net.minecraft.world.effect.MobEffects.STRENGTH, 3600, 1),
			new MobEffectInstance(net.minecraft.world.effect.MobEffects.SPEED, 2400, 1),
			new MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 1200, 0));

	private String name(MobEffectInstance e, Settings s) {
		String name = e.getEffect().value().getDisplayName().getString();
		int amp = e.getAmplifier();
		if (s.levels != LevelStyle.NONE && amp > 0) {
			name += " " + (s.levels == LevelStyle.ROMAN && amp < ROMAN.length
					? ROMAN[amp] : Integer.toString(amp + 1));
		}
		return name;
	}

	private String time(MobEffectInstance e) {
		if (e.isInfiniteDuration()) {
			return "∞";
		}
		int seconds = e.getDuration() / 20;
		return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
	}

	private float blinkAlpha(MobEffectInstance e, Settings s) {
		if (!s.blink || e.isInfiniteDuration() || e.getDuration() / 20f > s.blinkStart) {
			return 1f;
		}
		return 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI * 3));
	}

	private float redShift(MobEffectInstance e, Settings s) {
		if (!s.colorChange || e.isInfiniteDuration()) {
			return 0f;
		}
		float remaining = e.getDuration() / 20f;
		if (remaining > s.colorChangeStart) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, 1f - remaining / Math.max(0.5f, s.colorChangeStart)));
	}

	private static int applyFx(int argb, float alphaMul, float redShift) {
		int c = Colors.lerp(argb, 0xFFFF3333, redShift);
		int a = Math.round(((c >>> 24) & 0xFF) * alphaMul);
		return (a << 24) | (c & 0xFFFFFF);
	}

	private float textW(Minecraft mc, Settings s, String text) {
		return HudText.width(mc, s, text) / s.scale;
	}

	private float lineH(Minecraft mc, Settings s) {
		return HudText.lineHeight(mc, s) / s.scale;
	}

	/** Vertical rows show the name and the timer; horizontal cells show just the timer. */
	private String[] linesFor(MobEffectInstance e, Settings s) {
		return s.mode == Mode.HORIZONTAL
				? new String[] {time(e)}
				: new String[] {name(e, s), time(e)};
	}

	/** Unscaled {width, height} of one effect's icon + text block, honoring the text position. */
	private float[] blockSize(Minecraft mc, Settings s, MobEffectInstance e) {
		String[] lines = linesFor(e, s);
		float tw = 0;
		for (String line : lines) {
			tw = Math.max(tw, textW(mc, s, line));
		}
		float th = lines.length * lineH(mc, s) + (lines.length - 1);
		int cell = iconCell(s);
		float gap = tw > 0 ? textGap(s) : 0;
		float contentW;
		float contentH;
		switch (s.textPos) {
			case RIGHT, LEFT -> {
				contentW = cell + gap + tw;
				contentH = Math.max(cell, th);
			}
			default -> { // ABOVE, UNDER
				contentW = Math.max(cell, tw);
				contentH = cell + gap + th;
			}
		}
		return new float[] {contentW + stripPadX(s) * 2, contentH + stripPadY(s) * 2};
	}

	/** The largest block, so every row/cell shares one size and the backgrounds line up. */
	private float[] maxBlock(Minecraft mc, Settings s, List<MobEffectInstance> effects) {
		int token = dev.clientify.client.hud.HudFrame.token();
		if (cachedBlock != null && token == blockToken) {
			return cachedBlock; // same pass: every label already measured
		}
		float w = 0;
		float h = 0;
		for (MobEffectInstance e : effects) {
			float[] size = blockSize(mc, s, e);
			w = Math.max(w, size[0]);
			h = Math.max(h, size[1]);
		}
		cachedBlock = new float[] {w, h};
		blockToken = token;
		return cachedBlock;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		Settings s = (Settings) settings();
		List<MobEffectInstance> effects = effects(mc);
		if (effects.isEmpty()) {
			return 0;
		}
		float[] block = maxBlock(mc, s, effects);
		if (s.mode == Mode.HORIZONTAL) {
			return effects.size() * (block[0] + rowGap(s)) - rowGap(s) + s.extraW(PAD);
		}
		return block[0] + s.extraW(PAD);
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		Settings s = (Settings) settings();
		List<MobEffectInstance> effects = effects(mc);
		if (effects.isEmpty()) {
			return 0;
		}
		float[] block = maxBlock(mc, s, effects);
		if (s.mode == Mode.HORIZONTAL) {
			return block[1] + s.extraH(PAD);
		}
		return effects.size() * (block[1] + rowGap(s)) - rowGap(s) + s.extraH(PAD);
	}

	/**
	 * One effect: background, icon and text laid out for the chosen text position. The full
	 * strip covers the WHOLE block (so a timer under the icon sits inside it); the per-icon
	 * slot is a fixed square that only ever frames the icon.
	 */
	private void drawEffectBlock(GuiGraphicsExtractor g, Minecraft mc, Settings s, MobEffectInstance e,
			float ox, float oy, float blockW, float blockH, float scale, float aMul, float red) {
		int tint = ARGB.white(aMul);
		// The full strip wraps the WHOLE block; content is then inset within it.
		if (s.vanillaBg == VanillaBg.FULL) {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, INV_BG, Math.round(ox), Math.round(oy),
					Math.round(blockW), Math.round(blockH), tint);
		}

		String[] lines = linesFor(e, s);
		float lh = HudText.lineHeight(mc, s);
		float textH = lines.length * lh + (lines.length - 1) * scale;
		float textW = 0;
		for (String line : lines) {
			textW = Math.max(textW, HudText.width(mc, s, line));
		}
		float cellPx = iconCell(s) * scale;
		float gapPx = textW > 0 ? textGap(s) * scale : 0;

		// Content area = block minus the strip's uniform border inset; lay out inside it.
		float cx = ox + stripPadX(s) * scale;
		float cy = oy + stripPadY(s) * scale;
		float cw = blockW - stripPadX(s) * 2 * scale;
		float ch = blockH - stripPadY(s) * 2 * scale;

		float iconX;
		float iconY;
		float textLeft;
		float textTop;
		switch (s.textPos) {
			case LEFT -> {
				// Held against the icon, not against the left edge of the block.
				//
				// textW is the widest line of THIS effect while cw is the block, which every row
				// shares — so starting the text at cx and right-aligning inside its own textW gave
				// each row a different right edge, and only the longest one reached its icon.
				textLeft = cx + cw - cellPx - gapPx - textW;
				textTop = cy + (ch - textH) / 2f;
				iconX = cx + cw - cellPx;
				iconY = cy + (ch - cellPx) / 2f;
			}
			case ABOVE -> {
				textLeft = cx + (cw - textW) / 2f;
				textTop = cy;
				iconX = cx + (cw - cellPx) / 2f;
				iconY = cy + textH + gapPx;
			}
			case UNDER -> {
				iconX = cx + (cw - cellPx) / 2f;
				iconY = cy;
				textLeft = cx + (cw - textW) / 2f;
				textTop = cy + cellPx + gapPx;
			}
			default -> { // RIGHT
				iconX = cx;
				iconY = cy + (ch - cellPx) / 2f;
				textLeft = cx + cellPx + gapPx;
				textTop = cy + (ch - textH) / 2f;
			}
		}

		if (s.vanillaBg == VanillaBg.SLOT) {
			int slot = Math.round(24 * scale);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BG, Math.round(iconX), Math.round(iconY),
					slot, slot, tint);
		}
		int iconPx = Math.round(ICON * scale);
		float in = cellInset(s) * scale;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()),
				Math.round(iconX + in), Math.round(iconY + in), iconPx, iconPx, tint);

		float ty = textTop;
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			// The name (first line of a vertical row) uses the label color; timers the value.
			ModuleSettings.ColorSpec spec = i == 0 && lines.length > 1
					? s.labelColor
					: (s.splitColors ? s.valueColor : s.labelColor);
			float lw = HudText.width(mc, s, line);
			float lx = switch (s.textPos) {
				case LEFT -> textLeft + (textW - lw);            // right-aligned against the icon
				case ABOVE, UNDER -> textLeft + (textW - lw) / 2f; // centered under/over it
				default -> textLeft;
			};
			int len = line.length();
			HudText.draw(g, mc, s, line, lx, ty,
					j -> applyFx(spec.argbAt(j, len), aMul, red), len);
			ty += lh + scale;
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		List<MobEffectInstance> effects = effects(mc);

		int w = (int) unscaledWidth(mc);
		int h = (int) unscaledHeight(mc);
		if (w <= 0 || h <= 0) {
			return;
		}
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		float scale = s.scale;
		int rx = Math.round(r.x());
		int ry = Math.round(r.y());
		drawChromeScreen(g, mc, rx, ry, Math.round(r.w()), Math.round(r.h()));

		float ox = rx + s.insetX(PAD) * scale;
		float oy = ry + s.insetY(PAD) * scale;

		// Every block shares the largest size so the backgrounds form a tidy column/strip.
		float[] block = maxBlock(mc, s, effects);
		float blockW = block[0] * scale;
		float blockH = block[1] * scale;
		float step = (s.mode == Mode.HORIZONTAL ? block[0] : block[1]) + rowGap(s);
		float cursor = 0;
		for (MobEffectInstance e : effects) {
			float aMul = blinkAlpha(e, s);
			float red = redShift(e, s);
			float bx = ox + (s.mode == Mode.HORIZONTAL ? cursor * scale : 0);
			float by = oy + (s.mode == Mode.HORIZONTAL ? 0 : cursor * scale);
			drawEffectBlock(g, mc, s, e, bx, by, blockW, blockH, scale, aMul, red);
			cursor += step;
		}
	}
}
