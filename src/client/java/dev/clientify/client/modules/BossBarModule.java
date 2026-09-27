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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Boss bars as a draggable element: BossHealthOverlayMixin re-anchors vanilla's rendering
 * to this module's position/scale and applies the hide / recolor options.
 */
public class BossBarModule extends HudModule {
	public static class Settings extends ModuleSettings {
		public boolean hide = false;
		public boolean hideBar = false;
		public boolean hideText = false;
		public boolean editorPreview = true;
		public boolean customColor = false;
		public ColorSpec barColor = new ColorSpec("#F35D12");
		public int maxBars = 8;

		public Settings() {
			anchor = Anchor.TOP_CENTER;
			// Vanilla draws the bar at (guiWidth/2 - 91, 12) and the name 9 above it.
			// BossHealthOverlayMixin lands the bar at rect.y + 10, so the rect starts at 2.
			// offsetX has to be stated: the shared default is 5, which is right for a corner
			// module and 5px off centre for one vanilla centres.
			offsetX = 0;
			offsetY = 2;
		}
	}

	private static BossBarModule instance;

	public BossBarModule() {
		super("bossbar", "Boss Bar");
		instance = this;
	}

	@Override
	public String description() {
		return "Moves, scales, recolors or hides boss bars.";
	}

	/** Vanilla draws the bars themselves — no chip background/outline here. */
	@Override
	public boolean hasAppearance() {
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
		BossBarModule m = instance;
		return m != null && m.isEnabled() && m.settings() instanceof Settings s ? s : null;
	}

	public static BossBarModule get() {
		return instance;
	}

	/** Kept for the mixin's call site; boss bars have no chip chrome of their own. */
	public void drawBackdrop(GuiGraphicsExtractor g, Minecraft mc) {
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.dualToggle("Hide All", () -> s.hide, v -> s.hide = v,
				"Show In Editor", () -> s.editorPreview, v -> s.editorPreview = v));
		rows.add(screen.dualToggle("Hide Bar", () -> s.hideBar, v -> s.hideBar = v,
				"Hide Names", () -> s.hideText, v -> s.hideText = v));
		rows.add(screen.sliderRow("Max Bars", 1f, 12f, 1f, () -> (float) s.maxBars,
				v -> s.maxBars = Math.round(v), "%.0f", () -> s.maxBars = 8));
		rows.add(screen.toggleGear("Custom Bar Color", () -> s.customColor, v -> s.customColor = v,
				() -> expand("color"), () -> "color".equals(expanded),
				() -> s.customColor = d.customColor));
		if ("color".equals(expanded)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Colour", () -> s.barColor,
					() -> s.barColor.copyFrom(d.barColor)));
		}
	}

	/** Which dropdown is open; a state of the screen, not of the settings. */
	private transient String expanded;

	private void expand(String key) {
		expanded = key.equals(expanded) ? null : key;
	}

	/** Tint for the bar sprites, or -1 (no tint) when the custom color is off. */
	public static int barTint() {
		Settings s = active();
		return s != null && s.customColor ? s.barColor.chrome() : -1;
	}

	public static boolean hideBar() {
		Settings s = active();
		return s != null && s.hideBar;
	}

	/** True when bar #{@code index} (0-based) exceeds the configured limit. */
	public static boolean overLimit(int index) {
		Settings s = active();
		return s != null && index >= s.maxBars;
	}

	public static boolean hideText() {
		Settings s = active();
		return s != null && s.hideText;
	}

	/** With the editor preview off the module leaves the editor entirely (no drag outline). */
	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		return ((Settings) settings()).editorPreview ? super.draggables(mc, screenW, screenH) : List.of();
	}

	private static final Identifier BAR_BG = Identifier.withDefaultNamespace("boss_bar/pink_background");
	private static final Identifier BAR_FILL = Identifier.withDefaultNamespace("boss_bar/pink_progress");

	@Override
	public float unscaledWidth(Minecraft mc) {
		return 182;
	}

	/** One bar's visible content: name (9) above the 5px bar, so the outline hugs it. */
	@Override
	public float unscaledHeight(Minecraft mc) {
		return 16;
	}

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		if (mc.gui.screen() instanceof HudEditorScreen && s.editorPreview) {
			// A real-looking bar so it can be placed without a boss present. The offsets match
			// BossHealthOverlayMixin so the template sits where the real bar will.
			Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
			float scale = s.scale;
			int tint = s.customColor ? s.barColor.chrome() : -1;
			HudText.draw(g, mc, s, "Boss Bar", r.x() + (r.w() - HudText.width(mc, s, "Boss Bar")) / 2f,
					r.y() + scale, 0xFFFFFFFF);
			int bx = Math.round(r.x());
			int by = Math.round(r.y() + 10 * scale);
			int bw = Math.round(182 * scale);
			int bh = Math.round(5 * scale);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BG, bx, by, bw, bh, tint);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FILL, bw, bh, 0, 0, bx, by,
					Math.round(bw * 0.7f), bh, tint);
		}
	}
}
