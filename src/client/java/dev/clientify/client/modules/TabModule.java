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
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Tab list tweaks (tabtweaks-style) as a draggable element. Instead of a chip behind the
 * list, the tab list's OWN fills are recolored: the panel behind the whole list and the
 * per-player row strips are separate options. PlayerTabOverlayMixin re-anchors vanilla's
 * rendering to this module's position and scale.
 */
public class TabModule extends HudModule {
	/** Vanilla's panel fill (Integer.MIN_VALUE) — used to tell the two fill kinds apart. */
	public static final int VANILLA_PANEL = Integer.MIN_VALUE;

	public enum PingMode {
		ICONS, NUMERIC, HIDDEN;

		public String label() {
			return switch (this) {
				case ICONS -> "Icons";
				case NUMERIC -> "Numeric";
				case HIDDEN -> "Hidden";
			};
		}
	}

	public static class Settings extends ModuleSettings {
		public boolean hideHeader = false;
		public boolean hideFooter = false;
		public boolean hideHeads = false;
		public boolean editorPreview = false;
		public PingMode ping = PingMode.ICONS;
		/** The per-player row strips (vanilla alternates ~#20FFFFFF). */
		public ColorSpec rowColor = new ColorSpec("#20FFFFFF");
		public boolean rowBlur = false;

		public Settings() {
			// Vanilla position: top-centered, 10px down.
			anchor = Anchor.TOP_CENTER;
			// y = 10 already matched vanilla; offsetX did not - the shared default of 5 put the
			// list 5px right of the centre vanilla draws it on.
			offsetX = 0;
			offsetY = 10;
			// The list panel uses the SHARED appearance fields (background/bgColor/bgRounded/
			// bgRadius/bgBlur/border/borderThickness/borderColor) so Apply To All reaches it.
			background = true;
			bgColor = new ColorSpec("#80000000");
		}
	}

	private static TabModule instance;

	public TabModule() {
		super("tab", "Tab List");
		instance = this;
	}

	@Override
	public String description() {
		return "Tweaks the tab list: header, heads, ping, colors.";
	}

	/** The tab list draws itself — its own fills are recolored instead of a chip. */
	@Override
	public boolean hasAppearance() {
		return false;
	}

	/**
	 * Two separate things blur here: the panel behind the whole list, and each player row. The
	 * inherited version only knew about the panel, so a tab list with Row Blur on and the panel
	 * background off reported no interest in blur.
	 *
	 * <p>wantsBlur does not gate the capture; it decides whether the blur RADIUS is overridden for
	 * the frame. Reporting false while drawing blur gets the captured world unblurred — a sharp
	 * copy of the scene, which only looked right when another module wanted blur too.
	 */
	@Override
	public boolean wantsBlur() {
		Settings s = (Settings) settings();
		return (s.background && s.bgBlur) || s.rowBlur;
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
		TabModule m = instance;
		return m != null && m.isEnabled() && m.settings() instanceof Settings s ? s : null;
	}

	public static TabModule get() {
		return instance;
	}

	/** Substitutes our colors for vanilla's tab-list fills. */
	public static int fillColor(int original) {
		Settings s = active();
		if (s == null) {
			return original;
		}
		return original == VANILLA_PANEL ? s.bgColor.argbAt(0, 1) : s.rowColor.argbAt(0, 1);
	}

	// The tab list has no single background quad — vanilla fills a header plate, a footer
	// plate and one strip per player. To style it as ONE panel we measure the union of those
	// fills and draw a single rounded/blurred/bordered panel behind the whole list, using the
	// previous frame's bounds (the list only changes size when players join or leave).
	private static int accX0;
	private static int accY0;
	private static int accX1;
	private static int accY1;
	private static boolean accAny;
	private static int panelX0;
	private static int panelY0;
	private static int panelX1;
	private static int panelY1;
	private static boolean panelKnown;
	// Where the tab list's local space sits on screen — the frosted backdrop samples the blur
	// texture in SCREEN space, so it needs the active pose transform (same as the FPS chip's
	// local path passes its rect origin and scale).
	private static float poseX;
	private static float poseY;
	private static float poseScale = 1f;

	/** Called by the mixin with the transform it pushed around vanilla's tab rendering. */
	public static void setTransform(float x, float y, float scale) {
		poseX = x;
		poseY = y;
		poseScale = scale;
	}

	/** Called at the start of the tab list render: draws the unified panel, resets measuring. */
	public static void beginFrame(GuiGraphics g) {
		Settings s = active();
		accAny = false;
		if (s == null || !panelKnown) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int pad = 2;
		int x = panelX0 - pad;
		int y = panelY0 - pad;
		int w = panelX1 - panelX0 + pad * 2;
		int h = panelY1 - panelY0 + pad * 2;
		if (w <= 0 || h <= 0) {
			return;
		}
		int radius = s.bgRounded ? Math.max(0, s.bgRadius) : 0;
		if (s.background) {
			if (s.bgBlur && BlurBackdrop.prepare(mc)) {
				Draw.backdropRounded(g, BlurBackdrop.TEXTURE_ID, x, y, w, h, radius,
						poseX, poseY, poseScale, g.guiWidth(), g.guiHeight(), BlurBackdrop.vFlip());
			}
			Draw.smoothRounded(g, x, y, w, h, radius, s.bgColor.argbAt(0, 1));
		}
		if (s.border) {
			int t = Math.max(1, s.borderThickness);
			Draw.thickBorder(g, x - t, y - t, w + 2 * t, h + 2 * t,
					radius == 0 ? 0 : radius + t, t, s.borderColor.argbAt(0, 1));
		}
	}

	/** Called at the end of the render: commits the measured bounds for the next frame. */
	public static void endFrame() {
		if (accAny) {
			panelX0 = accX0;
			panelY0 = accY0;
			panelX1 = accX1;
			panelY1 = accY1;
			panelKnown = true;
		}
	}

	/**
	 * Handles one of the tab list's own fills. Vanilla's plate fills are swallowed (the
	 * unified panel replaces them); player row strips keep drawing in their own color.
	 * Returns true when it handled the draw.
	 */
	public static boolean drawFill(GuiGraphics g, int x0, int y0, int x1, int y1, int original) {
		Settings s = active();
		if (s == null) {
			return false;
		}
		// Measure every fill so the panel can wrap the whole list next frame.
		if (!accAny) {
			accX0 = x0;
			accY0 = y0;
			accX1 = x1;
			accY1 = y1;
			accAny = true;
		} else {
			accX0 = Math.min(accX0, x0);
			accY0 = Math.min(accY0, y0);
			accX1 = Math.max(accX1, x1);
			accY1 = Math.max(accY1, y1);
		}
		if (original == VANILLA_PANEL) {
			return true; // replaced by the unified panel
		}
		int w = x1 - x0;
		int h = y1 - y0;
		if (w <= 0 || h <= 0) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (s.rowBlur && BlurBackdrop.prepare(mc)) {
			Draw.backdropRounded(g, BlurBackdrop.TEXTURE_ID, x0, y0, w, h, 0,
					poseX, poseY, poseScale, g.guiWidth(), g.guiHeight(), BlurBackdrop.vFlip());
		}
		g.fill(x0, y0, x1, y1, s.rowColor.argbAt(0, 1));
		return true;
	}

	/** Numeric ping color, tabtweaks-style ranges. */
	public static int pingColor(int latency) {
		if (latency < 0) {
			return 0xFF808080;
		}
		if (latency < 75) {
			return 0xFF00FF40;
		}
		if (latency < 145) {
			return 0xFFB0FF3C;
		}
		if (latency < 200) {
			return 0xFFFFE23C;
		}
		if (latency < 300) {
			return 0xFFFFA03C;
		}
		if (latency < 400) {
			return 0xFFFF5A3C;
		}
		return 0xFFFF2020;
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.dualToggle("Hide Header", () -> s.hideHeader, v -> s.hideHeader = v,
				"Hide Footer", () -> s.hideFooter, v -> s.hideFooter = v));
		rows.add(screen.dualToggle("Hide Heads", () -> s.hideHeads, v -> s.hideHeads = v,
				"Show In Editor", () -> s.editorPreview, v -> s.editorPreview = v));
		rows.add(screen.cycleRow("Ping Display", () -> s.ping.label(),
				() -> s.ping = cycle(s.ping, -1), () -> s.ping = cycle(s.ping, 1),
				() -> s.ping = PingMode.ICONS));
		rows.add(screen.toggle("List Background", () -> s.background, v -> s.background = v,
				() -> s.background = true));
		screen.addColorRows(rows, "List Background Color", () -> s.bgColor,
				() -> s.bgColor.copyFrom(d.bgColor));
		rows.add(screen.dualToggle("Rounded List", () -> s.bgRounded, v -> s.bgRounded = v,
				"List Blur", () -> s.bgBlur, v -> s.bgBlur = v));
		if (s.bgRounded) {
			rows.add(screen.sliderRow("Corner Radius", 1f, 16f, 1f, () -> (float) s.bgRadius,
					v -> s.bgRadius = Math.round(v), "%.0f", () -> s.bgRadius = 6));
		}
		rows.add(screen.toggle("List Border", () -> s.border, v -> s.border = v,
				() -> s.border = false));
		if (s.border) {
			rows.add(screen.sliderRow("Border Thickness", 1f, 5f, 1f, () -> (float) s.borderThickness,
					v -> s.borderThickness = Math.round(v), "%.0f", () -> s.borderThickness = 1));
			screen.addColorRows(rows, "Border Color", () -> s.borderColor,
					() -> s.borderColor.copyFrom(d.borderColor));
		}
		screen.addColorRows(rows, "Player Rows", () -> s.rowColor, () -> s.rowColor.copyFrom(d.rowColor));
		rows.add(screen.blurToggle("Player Row Blur", () -> s.rowBlur, v -> s.rowBlur = v,
				() -> s.rowBlur = false));
	}

	/** With the editor preview off the module leaves the editor entirely (no drag outline). */
	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		return ((Settings) settings()).editorPreview ? super.draggables(mc, screenW, screenH) : List.of();
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		return 220;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return 70;
	}

	@Override
	public void render(GuiGraphics g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		if (mc.screen instanceof HudEditorScreen && s.editorPreview) {
			Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
			g.fill(Math.round(r.x()), Math.round(r.y()), Math.round(r.x() + r.w()),
					Math.round(r.y() + r.h()), 0x30FFFFFF);
			HudText.draw(g, mc, s, "Tab List", r.x() + 4, r.y() + 4, 0xB2FFFFFF);
		}
	}
}
