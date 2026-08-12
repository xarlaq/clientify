package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Title tweaks: vanilla title rendering is taken over (GuiTitleMixin) and drawn inside this
 * draggable HUD element — move it, scale it, use the Clientify font, put our chip
 * background/outline behind it, recolor it, or disable titles entirely. Colors run per
 * character in BOTH fonts, so gradients and waves work; vanilla's fade drives the alpha.
 */
public class TitleModule extends HudModule {
	private static final int PAD = 4;
	/** Vanilla title/subtitle scale factors. */
	private static final float TITLE_SCALE = 4f;
	private static final float SUB_SCALE = 2f;

	public static class Settings extends ModuleSettings {
		public boolean disableTitles = false;
		public boolean hideSubtitle = false;
		public boolean editorPreview = false;
		public float opacity = 1f;
		public ColorSpec titleColor = new ColorSpec("#FFFFFF");
		public ColorSpec subtitleColor = new ColorSpec("#FFFFFF");

		public Settings() {
			anchor = Anchor.CENTER;
			// Same 5px as the other centred modules: the shared offsetX default is for corners.
			offsetX = 0;
			background = false;
		}
	}

	private static TitleModule instance;
	private transient Component currentTitle;
	private transient Component currentSubtitle;

	public TitleModule() {
		super("title", "Title");
		instance = this;
	}

	@Override
	public String description() {
		return "Moves, scales and restyles server titles.";
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
		TitleModule m = instance;
		return m != null && m.isEnabled() && m.settings() instanceof Settings s ? s : null;
	}

	public static TitleModule get() {
		return instance;
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.dualToggle("Disable Titles", () -> s.disableTitles, v -> s.disableTitles = v,
				"Hide Subtitle", () -> s.hideSubtitle, v -> s.hideSubtitle = v));
		rows.add(screen.toggle("Show In Editor", () -> s.editorPreview, v -> s.editorPreview = v,
				() -> s.editorPreview = false));
		rows.add(screen.sliderRow("Opacity", 10f, 100f, 5f, () -> s.opacity * 100f, v -> s.opacity = v / 100f,
				"%.0f%%", () -> s.opacity = 1f));
		screen.addColorRows(rows, "Title Color", () -> s.titleColor, () -> s.titleColor.copyFrom(d.titleColor));
		screen.addColorRows(rows, "Subtitle Color", () -> s.subtitleColor,
				() -> s.subtitleColor.copyFrom(d.subtitleColor));
	}

	/**
	 * With the editor preview off the module leaves the editor entirely (no drag outline).
	 * A live title still shows and stays positioned by its saved anchor.
	 */
	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		return ((Settings) settings()).editorPreview ? super.draggables(mc, screenW, screenH) : List.of();
	}

	/** Text width at an extra font multiplier, in screen pixels. */
	private float widthAt(Minecraft mc, ModuleSettings s, String text, float extra) {
		return HudText.width(mc, s, text, s.scale * extra);
	}

	private float lineHeightAt(Minecraft mc, ModuleSettings s, float extra) {
		return HudText.lineHeight(mc, s, s.scale * extra);
	}

	/** Title used for sizing: the live one, else the editor preview. Keeps the hitbox honest. */
	private Component boundsTitle(Minecraft mc) {
		if (currentTitle != null) {
			return currentTitle;
		}
		return mc.screen instanceof HudEditorScreen && ((Settings) settings()).editorPreview
				? Component.literal("Title Preview") : null;
	}

	private Component boundsSubtitle(Minecraft mc) {
		Settings s = (Settings) settings();
		if (s.hideSubtitle) {
			return null;
		}
		if (currentTitle != null) {
			return currentSubtitle;
		}
		return mc.screen instanceof HudEditorScreen && s.editorPreview
				? Component.literal("Subtitle Preview") : null;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		ModuleSettings s = settings();
		Component t = boundsTitle(mc);
		Component sub = boundsSubtitle(mc);
		float w = 0;
		if (t != null) {
			w = Math.max(w, widthAt(mc, s, t.getString(), TITLE_SCALE) / s.scale);
		}
		if (sub != null) {
			w = Math.max(w, widthAt(mc, s, sub.getString(), SUB_SCALE) / s.scale);
		}
		if (w <= 0) {
			w = 20;
		}
		return w + s.extraW(PAD);
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		ModuleSettings s = settings();
		float h = boundsTitle(mc) != null ? lineHeightAt(mc, s, TITLE_SCALE) / s.scale : 0;
		if (boundsSubtitle(mc) != null) {
			h += lineHeightAt(mc, s, SUB_SCALE) / s.scale + 4;
		}
		if (h <= 0) {
			h = 12;
		}
		return h + s.extraH(PAD);
	}

	/** Called by GuiTitleMixin in place of vanilla title rendering. */
	public void renderTitle(GuiGraphics g, Component title, Component subtitle, int alpha) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		currentTitle = title;
		currentSubtitle = s.hideSubtitle ? null : subtitle;
		int a = (int) (alpha * s.opacity);
		if (a <= 8) {
			return;
		}
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		drawChromeScreen(g, mc, Math.round(r.x()), Math.round(r.y()), Math.round(r.w()), Math.round(r.h()));

		float ty = r.y() + s.insetY(PAD) * s.scale;
		drawLine(g, mc, s, title, r, ty, TITLE_SCALE, s.titleColor, a);
		if (currentSubtitle != null) {
			ty += lineHeightAt(mc, s, TITLE_SCALE) + 4 * s.scale;
			drawLine(g, mc, s, currentSubtitle, r, ty, SUB_SCALE, s.subtitleColor, a);
		}
	}

	/**
	 * One centered line at an extra font multiplier. The server's own colors win; anything it
	 * left uncolored takes our ColorSpec (per character, so gradients/waves still work).
	 */
	private void drawLine(GuiGraphics g, Minecraft mc, Settings s, Component text, Rect r, float y,
			float extra, ColorSpec spec, int alpha) {
		float scale = s.scale * extra;
		float w = HudText.width(mc, s, text, scale);
		int len = text.getString().length();
		int base = (alpha << 24) | 0xFFFFFF;
		HudText.drawComponent(g, mc, s, text, r.x() + (r.w() - w) / 2f, y, base,
				i -> (alpha << 24) | (spec.argbAt(i, len) & 0xFFFFFF), scale);
	}

	@Override
	public void render(GuiGraphics g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		if (mc.screen instanceof HudEditorScreen && currentTitle == null && s.editorPreview) {
			renderTitle(g, Component.literal("Title Preview"), Component.literal("Subtitle Preview"), 255);
			currentTitle = null;
			currentSubtitle = null;
		}
	}
}
