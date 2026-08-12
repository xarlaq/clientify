package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.BlurBackdrop;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Scoreboard sidebar as a draggable HUD element. The server's own text colors are preserved
 * (per character, so they survive the Clientify font too). Header and body have independent
 * backgrounds; the border wraps the whole panel.
 */
public class ScoreboardModule extends HudModule {
	private static final int PAD = 2;

	public static class Settings extends ModuleSettings {
		public boolean hide = false;
		public boolean hideScores = false;
		public boolean hideTitle = false;
		public int maxLines = 15;
		/** Header plate (vanilla getBackgroundColor(0.4)). */
		public boolean headerBackground = true;
		public ColorSpec headerColor = new ColorSpec("#66000000");
		/**
		 * Off by default: the score column keeps the server's own colours, like the name column
		 * already does. Servers put more than a score in there — ping, health, a status — and
		 * painting all of it one colour throws away what they were saying with it.
		 */
		public boolean customScoreColor = false;
		public ColorSpec scoreColor = new ColorSpec("#FF5555");
		/**
		 * Overrides every colour the server sent — the title, the names and the scores alike.
		 *
		 * <p>Custom Score Colour still wins for the score column when both are on: the more specific
		 * setting beats the more general one, so "everything white, scores red" is a thing you can
		 * ask for rather than a contradiction.
		 */
		public boolean customTextColor = false;
		public ColorSpec textColor = new ColorSpec("#FFFFFF");

		public Settings() {
			// Vanilla sidebar: right edge, vertically centered, vanilla body shade.
			anchor = Anchor.MIDDLE_RIGHT;
			offsetX = -1; // vanilla fills out to guiWidth - 1
			// Vanilla does NOT centre the sidebar: it hangs from guiHeight/2 + lines*3, so its top
			// climbs 6px per line while a middle anchor climbs 4.5. No fixed offset can match every
			// line count. This is exact at 8 lines - a common sidebar - and stays within about ten
			// pixels from one line to fifteen. Zero was as much as 25px low.
			offsetY = -14;
			background = true;
			bgColor = new ColorSpec("#4C000000");
			bgWidth = 4;
			bgHeight = 0;
		}
	}

	/** One prepared sidebar line (score already formatted). */
	public record Line(Component name, Component score, int scoreWidth) {
	}

	private static ScoreboardModule instance;
	private transient List<Line> lines = List.of();
	private transient Component title;
	private transient String expandedSection;

	public ScoreboardModule() {
		super("scoreboard", "Scoreboard");
		instance = this;
	}

	@Override
	public String description() {
		return "Movable sidebar with custom font, backgrounds and position.";
	}

	@Override
	public boolean hasColorSection() {
		return false;
	}

	@Override
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	/** Active settings, or null when vanilla behavior should apply. */
	public static Settings active() {
		ScoreboardModule m = instance;
		return m != null && m.isEnabled() && m.settings() instanceof Settings s ? s : null;
	}

	public static ScoreboardModule get() {
		return instance;
	}

	private void expand(String key) {
		expandedSection = key.equals(expandedSection) ? null : key;
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.dualToggle("Hide Scoreboard", () -> s.hide, v -> s.hide = v,
				"Hide Scores", () -> s.hideScores, v -> s.hideScores = v));
		rows.add(screen.sliderRow("Max Lines", 1f, 15f, 1f, () -> (float) s.maxLines,
				v -> s.maxLines = Math.round(v), "%.0f", () -> s.maxLines = 15));
		rows.add(screen.toggleGear("Header", () -> !s.hideTitle, v -> s.hideTitle = !v,
				() -> expand("header"), () -> "header".equals(expandedSection), () -> {
					s.hideTitle = false;
					s.headerBackground = true;
					s.headerColor.copyFrom(d.headerColor);
				}));
		if ("header".equals(expandedSection)) {
			screen.groupCard(rows, group -> {
				group.add(screen.toggle("Header Background", () -> s.headerBackground,
						v -> s.headerBackground = v, () -> s.headerBackground = true));
				screen.addColorRows(group, "Header Background Color", () -> s.headerColor,
						() -> s.headerColor.copyFrom(d.headerColor));
			});
		}
		rows.add(screen.withTooltip(
				screen.toggleGear("Custom Text Colour", () -> s.customTextColor,
						v -> s.customTextColor = v, () -> expand("text"),
						() -> "text".equals(expandedSection), () -> {
							s.customTextColor = d.customTextColor;
							s.textColor.copyFrom(d.textColor);
						}),
				"Overrides every colour the server sent — the title,\n"
						+ "the names and the scores alike. Custom Score Colour\n"
						+ "still wins for the score column when both are on."));
		if ("text".equals(expandedSection)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Text Colour",
					() -> s.textColor, () -> s.textColor.copyFrom(d.textColor)));
		}
		rows.add(screen.withTooltip(
				screen.toggleGear("Custom Score Colour", () -> s.customScoreColor,
						v -> s.customScoreColor = v, () -> expand("score"),
						() -> "score".equals(expandedSection), () -> {
							s.customScoreColor = d.customScoreColor;
							s.scoreColor.copyFrom(d.scoreColor);
						}),
				"Off keeps the server's own colours in the score column.\n"
						+ "Servers put ping, health and status there, and one\n"
						+ "colour over all of it throws that away."));
		if ("score".equals(expandedSection)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Score Colour",
					() -> s.scoreColor, () -> s.scoreColor.copyFrom(d.scoreColor)));
		}
	}

	/** Called by GuiScoreboardMixin with the prepared lines each frame. */
	public void updateContent(Component title, List<Line> lines) {
		this.title = title;
		this.lines = lines;
	}

	private float measure(Minecraft mc, ModuleSettings s, Component text) {
		return HudText.width(mc, s, text) / s.scale;
	}

	private static final String[] DEMO_ROWS = {"Kills", "Deaths", "Coins", "Rank", "Playtime", "Level"};

	private List<Line> demoLines(Minecraft mc) {
		List<Line> demo = new ArrayList<>();
		for (int i = 0; i < DEMO_ROWS.length; i++) {
			String score = Integer.toString((DEMO_ROWS.length - i) * 3);
			demo.add(new Line(Component.literal(DEMO_ROWS[i]), Component.literal(score), mc.font.width(score)));
		}
		return demo;
	}

	private boolean editorDemo(Minecraft mc) {
		return title == null && mc.screen instanceof HudEditorScreen;
	}

	/** Title/lines used for sizing: the live ones, else the editor demo — so the hitbox fits. */
	private Component boundsTitle(Minecraft mc) {
		return editorDemo(mc) ? Component.literal("Scoreboard") : title;
	}

	private List<Line> boundsLines(Minecraft mc) {
		return editorDemo(mc) ? demoLines(mc) : lines;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		ModuleSettings s = settings();
		Settings set = (Settings) s;
		Component t = boundsTitle(mc);
		float w = 0;
		if (t != null && !set.hideTitle) {
			w = Math.max(w, measure(mc, s, t));
		}
		float sep = HudText.width(mc, s, ": ") / s.scale;
		for (Line line : boundsLines(mc)) {
			float lw = measure(mc, s, line.name())
					+ (!set.hideScores && line.scoreWidth() > 0 ? sep + measure(mc, s, line.score()) : 0);
			w = Math.max(w, lw);
		}
		if (w <= 0) {
			w = 40;
		}
		return w + s.extraW(PAD);
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		ModuleSettings s = settings();
		float lh = HudText.lineHeight(mc, s) / s.scale;
		int count = Math.max(1, boundsLines(mc).size());
		int titleLine = ((Settings) settings()).hideTitle ? 0 : 1;
		return (count + titleLine) * lh + s.extraH(PAD);
	}

	/** Draws the sidebar: header plate, body plate, then the text; border wraps both. */
	public void renderSidebar(GuiGraphics g, Minecraft mc) {
		Settings s = (Settings) settings();
		if (s.hide || title == null) {
			return;
		}
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		float scale = s.scale;
		float lh = HudText.lineHeight(mc, s);
		float headerH = s.hideTitle ? 0 : lh + (PAD / 2f) * scale;
		int x = Math.round(r.x());
		int y = Math.round(r.y());
		int w = Math.round(r.w());
		int h = Math.round(r.h());
		int rad = Math.round((s.bgRounded ? Math.max(0, s.bgRadius) : 0) * scale);

		// Header plate rounds its TOP corners, the body its BOTTOM ones, so together they read
		// as one rounded panel with a divider rather than two separate rounded boxes.
		boolean hasHeader = !s.hideTitle && s.headerBackground;
		int bodyY = y + Math.round(headerH);
		int bodyH = h - Math.round(headerH);
		if (hasHeader) {
			plate(g, mc, s, x, y, w, Math.round(headerH), rad, s.headerColor.argbAt(0, 1),
					true, !s.background);
		}
		if (s.background) {
			plate(g, mc, s, x, bodyY, w, bodyH, rad, s.bgColor.argbAt(0, 1), !hasHeader, true);
		}
		// The border wraps the WHOLE panel (header + body), not just the body.
		if (s.border) {
			int t = Math.max(1, Math.round(s.borderThickness * scale));
			Draw.thickBorder(g, x - t, y - t, w + 2 * t, h + 2 * t, rad == 0 ? 0 : rad + t, t,
					s.borderColor.argbAt(0, 1));
		}

		float left = r.x() + s.insetX(PAD) * scale;
		float right = r.x() + r.w() - s.insetX(PAD) * scale;
		float ty = r.y() + (PAD / 2f) * scale;
		ColorSpec textOverride = s.customTextColor ? s.textColor : null;
		if (!s.hideTitle) {
			float titleW = HudText.width(mc, s, title);
			drawText(g, mc, s, title, r.x() + (r.w() - titleW) / 2f, ty, textOverride);
			ty = r.y() + headerH + (PAD / 2f) * scale;
		}
		for (Line line : lines) {
			drawText(g, mc, s, line.name(), left, ty, textOverride);
			if (!s.hideScores && line.scoreWidth() > 0) {
				float sw = HudText.width(mc, s, line.score());
				// The score column's own setting wins over the blanket one.
				drawText(g, mc, s, line.score(), right - sw, ty,
						s.customScoreColor ? s.scoreColor : textOverride);
			}
			ty += lh;
		}
	}

	/**
	 * One line of the sidebar. A null override keeps the server's own colours, with uncoloured
	 * characters falling back to white; anything else paints the whole run from the spec, so its
	 * chroma and gradient modes travel across the text rather than per character.
	 */
	private void drawText(GuiGraphics g, Minecraft mc, ModuleSettings s, Component text,
			float x, float y, ColorSpec override) {
		if (override == null) {
			HudText.drawComponent(g, mc, s, text, x, y, 0xFFFFFFFF, null);
			return;
		}
		String plain = text.getString();
		int len = plain.length();
		HudText.draw(g, mc, s, plain, x, y, i -> override.argbAt(i, len), len);
	}

	/**
	 * A background plate that can round only some corners: the rounded rect is drawn
	 * overhanging past the square edges and clipped, so those corners come out flat.
	 */
	/**
	 * The plates blur on {@code bgBlur} alone, and the header plate is drawn whenever there is a
	 * header — outside the {@code background} check the inherited version insists on. So a
	 * scoreboard with the body background off and a blurred header reported no interest in blur.
	 *
	 * <p>wantsBlur does not gate the capture; it decides whether the blur RADIUS is overridden for
	 * the frame. Reporting false while drawing a blurred plate gets the captured world unblurred —
	 * a sharp copy of the scene, which only looked right when another module wanted blur too.
	 */
	@Override
	public boolean wantsBlur() {
		Settings s = (Settings) settings();
		return s.bgBlur && (s.background || (!s.hideTitle && s.headerBackground));
	}

	private void plate(GuiGraphics g, Minecraft mc, Settings s, int x, int y, int w, int h,
			int radius, int color, boolean roundTop, boolean roundBottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int drawY = y - (roundTop ? 0 : radius);
		int drawH = h + (roundTop ? 0 : radius) + (roundBottom ? 0 : radius);
		boolean clip = radius > 0 && !(roundTop && roundBottom);
		if (clip) {
			g.enableScissor(x, y, x + w, y + h);
		}
		if (s.bgBlur && BlurBackdrop.prepare(mc)) {
			Draw.backdropRounded(g, BlurBackdrop.TEXTURE_ID, x, drawY, w, drawH, radius,
					0, 0, 1f, g.guiWidth(), g.guiHeight(), BlurBackdrop.vFlip());
		}
		Draw.smoothRounded(g, x, drawY, w, drawH, radius, color);
		if (clip) {
			g.disableScissor();
		}
	}

	@Override
	public void render(GuiGraphics g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (editorDemo(mc)) {
			updateContent(Component.literal("Scoreboard"), demoLines(mc));
			renderSidebar(g, mc);
			updateContent(null, List.of());
		}
	}
}
