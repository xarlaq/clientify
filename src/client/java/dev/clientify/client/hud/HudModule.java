package dev.clientify.client.hud;

import dev.clientify.client.config.ModuleSettings;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A movable HUD element. Position = 9-point anchor + offset (see ModuleSettings); elements
 * stay glued to their screen region when the window or GUI scale changes. Scale is applied
 * around the anchor-aligned corner via the pose transform.
 */
public abstract class HudModule {
	private final String id;
	private final String displayName;
	private ModuleSettings settings;

	protected HudModule(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	/** Filter-chip category in the mod list (HUD, SERVER, MECHANIC, …). */
	public String category() {
		return "HUD";
	}

	/** One-line description shown under the title in the module's settings (Lunar-style). */
	public String description() {
		return "";
	}

	/** On-screen corner radius of the module's body — the editor outline matches it. */
	public int outlineRadius() {
		return 0;
	}

	/** False for non-visual feature modules (fullbright, tooltips): no editor chip, no HUD render. */
	public boolean isHudElement() {
		return true;
	}

	/** False to hide the appearance rows (font/background/colors) in the settings screen. */
	public boolean hasAppearance() {
		return isHudElement();
	}

	/** False to hide the shared Color section (Split Colors + label/value) — modules that
	 * manage their own colors (coords per-part, armor main color) return false. */
	/**
	 * Whether the Appearance section offers the module-wide background and border.
	 *
	 * <p>False for a module that draws its own — keystrokes paints and outlines each key, and a
	 * second background behind the lot of them is a setting with nothing to do.
	 */
	public boolean hasBackgroundSection() {
		return true;
	}

	/**
	 * Whether this module wants the world behind it blurred this frame.
	 *
	 * <p>Asked rather than read off the settings, because a module that keeps its background
	 * somewhere other than {@code bgBlur} would otherwise never arm the capture — and would then
	 * appear to work only while some other module happened to be arming it.
	 */
	public boolean wantsBlur() {
		ModuleSettings s = settings();
		return s != null && s.background && s.bgBlur;
	}

	public boolean hasColorSection() {
		return true;
	}

	/** Once per client tick while the mod is loaded (keybinds, ping scheduling, …). */
	public void tick(Minecraft mc) {
	}

	/** Module-specific settings rows, shown in a "Module" section of the settings screen. */
	public void appendSettings(dev.clientify.client.gui.ModuleSettingsScreen screen,
			java.util.List<dev.clientify.client.gui.SettingsRowsScreen.Row> rows) {
	}

	/**
	 * An editable text field (label + get/set + default) shown in a "Text" settings section.
	 * Optional: {@code onRemove} shows a bin icon (delete this entry), {@code gearToggle} +
	 * {@code gearOpen} show a gear expanding per-entry rows from {@link #appendTextFieldGear}.
	 */
	public record TextField(String label, java.util.function.Supplier<String> get,
			java.util.function.Consumer<String> set, String defaultValue,
			Runnable onRemove, Runnable gearToggle, java.util.function.BooleanSupplier gearOpen,
			boolean gearOnly) {
		public TextField(String label, java.util.function.Supplier<String> get,
				java.util.function.Consumer<String> set, String defaultValue) {
			this(label, get, set, defaultValue, null, null, null, false);
		}

		public TextField(String label, java.util.function.Supplier<String> get,
				java.util.function.Consumer<String> set, String defaultValue,
				Runnable onRemove, Runnable gearToggle, java.util.function.BooleanSupplier gearOpen) {
			this(label, get, set, defaultValue, onRemove, gearToggle, gearOpen, false);
		}

		/** A field that only appears inside its owner's gear card. */
		public static TextField inGear(String label, java.util.function.Supplier<String> get,
				java.util.function.Consumer<String> set, String defaultValue) {
			return new TextField(label, get, set, defaultValue, null, null, null, true);
		}
	}

	/** Editable text fields (sprint state strings, custom text lines, …). Default: none. */
	public List<TextField> textFields() {
		return List.of();
	}

	/** Rows for text field {@code index}'s expanded gear dropdown (per-entry formatting). */
	public void appendTextFieldGear(dev.clientify.client.gui.ModuleSettingsScreen screen,
			java.util.List<dev.clientify.client.gui.SettingsRowsScreen.Row> rows, int index) {
	}

	/** The settings screen for this module — modules with a custom layout override this. */
	public net.minecraft.client.gui.screens.Screen settingsScreen(dev.clientify.client.gui.ModListScreen list) {
		return new dev.clientify.client.gui.ModuleSettingsScreen(list, this);
	}

	/** True to lay the text fields out two per row (compact lists like waypoints). */
	public boolean pairTextFields() {
		return false;
	}

	/** Optional icon drawn just before text field {@code index}'s box (item/block preview). */
	public net.minecraft.world.item.ItemStack textFieldIcon(int index) {
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	/** False to hide the top-level Scale slider (modules with per-part scales). */
	public boolean hasScaleSlider() {
		return isHudElement();
	}

	/**
	 * Chip background + border for modules that render in LOCAL space under a pose scale
	 * (the vanilla-font text path). Draws at (0,0)..(w,h) local (unscaled radius/thickness).
	 */
	protected void drawChromeLocal(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int w, int h) {
		ModuleSettings s = settings();
		int rad = s.bgRounded ? Math.max(0, s.bgRadius) : 0;
		int t = s.border ? Math.max(1, s.borderThickness) : 0;
		// Screen footprint of the chip (this method draws in local space under a pose scale).
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		// Overlap clipping is opt-in, so the usual path must not pay for it: no mask call and no
		// lambda to capture the draw in — this runs for every chip, every frame.
		if (!ChromeMask.enabled()) {
			paintChromeLocal(g, mc, s, w, h, rad, t, r);
			return;
		}
		ChromeMask.draw(g, r.x() - t * s.scale, r.y() - t * s.scale,
				(w + 2 * t) * s.scale, (h + 2 * t) * s.scale,
				rad == 0 ? 0 : (rad + t) * s.scale, () -> paintChromeLocal(g, mc, s, w, h, rad, t, r));
	}

	private void paintChromeLocal(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, ModuleSettings s,
			int w, int h, int rad, int t, Rect r) {
		if (s.background) {
			if (s.bgBlur && BlurBackdrop.prepare(mc)) {
				dev.clientify.client.util.Draw.backdropRounded(g, BlurBackdrop.TEXTURE_ID, 0, 0, w, h, rad,
						r.x(), r.y(), s.scale, g.guiWidth(), g.guiHeight(), BlurBackdrop.vFlip());
			}
			dev.clientify.client.util.Draw.smoothRounded(g, 0, 0, w, h, rad, s.bgColor.argbAt(0, 1));
		}
		if (s.border) {
			dev.clientify.client.util.Draw.thickBorder(g, -t, -t, w + 2 * t, h + 2 * t,
					rad == 0 ? 0 : rad + t, t, s.borderColor.argbAt(0, 1));
		}
	}

	/**
	 * Chip background + border in SCREEN space at (x,y,w,h) — radius/thickness scaled by the
	 * module scale. For modules that lay out in screen coords (icon modules using HudText).
	 */
	protected void drawChromeScreen(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int x, int y, int w, int h) {
		drawChromeScreen(g, mc, x, y, w, h, settings().scale);
	}

	/**
	 * Chip chrome at an explicit scale — for parts drawn at their own size (waypoint chips
	 * under zoom, separated coords/armor pieces), so radius and border thickness scale with
	 * the part rather than the module.
	 */
	protected void drawChromeScreen(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, int x, int y, int w, int h,
			float scale) {
		ModuleSettings s = settings();
		int rad = Math.round((s.bgRounded ? Math.max(0, s.bgRadius) : 0) * scale);
		int t = s.border ? Math.max(1, Math.round(s.borderThickness * scale)) : 0;
		if (!ChromeMask.enabled()) {
			paintChromeScreen(g, mc, s, x, y, w, h, rad, t);
			return;
		}
		ChromeMask.draw(g, x - t, y - t, w + 2 * t, h + 2 * t, rad == 0 ? 0 : rad + t,
				() -> paintChromeScreen(g, mc, s, x, y, w, h, rad, t));
	}

	private void paintChromeScreen(net.minecraft.client.gui.GuiGraphics g, Minecraft mc, ModuleSettings s,
			int x, int y, int w, int h, int rad, int t) {
		if (s.background) {
			if (s.bgBlur && BlurBackdrop.prepare(mc)) {
				dev.clientify.client.util.Draw.backdropRounded(g, BlurBackdrop.TEXTURE_ID, x, y, w, h, rad,
						0, 0, 1f, g.guiWidth(), g.guiHeight(), BlurBackdrop.vFlip());
			}
			dev.clientify.client.util.Draw.smoothRounded(g, x, y, w, h, rad, s.bgColor.argbAt(0, 1));
		}
		if (s.border) {
			dev.clientify.client.util.Draw.thickBorder(g, x - t, y - t, w + 2 * t, h + 2 * t,
					rad == 0 ? 0 : rad + t, t, s.borderColor.argbAt(0, 1));
		}
	}

	public ModuleSettings settings() {
		return settings;
	}

	public void setSettings(ModuleSettings settings) {
		this.settings = settings;
	}

	public boolean isEnabled() {
		return settings != null && settings.enabled;
	}

	public Class<? extends ModuleSettings> settingsClass() {
		return ModuleSettings.class;
	}

	public ModuleSettings createDefaultSettings() {
		return new ModuleSettings();
	}

	public abstract float unscaledWidth(Minecraft mc);

	public abstract float unscaledHeight(Minecraft mc);

	public abstract void render(GuiGraphics graphics, DeltaTracker deltaTracker);

	public record Rect(float x, float y, float w, float h) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	/** Cycles an enum value by {@code dir} (settings ‹ › rows). */
	protected static <E extends Enum<E>> E cycle(E value, int dir) {
		E[] values = value.getDeclaringClass().getEnumConstants();
		return values[(value.ordinal() + dir + values.length) % values.length];
	}

	public Rect bounds(Minecraft mc, float screenW, float screenH) {
		ModuleSettings s = settings;
		float w = unscaledWidth(mc) * s.scale;
		float h = unscaledHeight(mc) * s.scale;
		float x = s.anchor.fx * screenW - s.anchor.fx * w + s.offsetX;
		float y = s.anchor.fy * screenH - s.anchor.fy * h + s.offsetY;
		return new Rect(x, y, w, h);
	}

	/** Repositions the module so its top-left lands at (x,y), re-picking the nearest anchor. */
	public void setPosition(float x, float y, Minecraft mc, float screenW, float screenH) {
		ModuleSettings s = settings;
		float w = unscaledWidth(mc) * s.scale;
		float h = unscaledHeight(mc) * s.scale;
		s.anchor = ModuleSettings.Anchor.nearest(x + w / 2f, y + h / 2f, screenW, screenH);
		s.offsetX = x - (s.anchor.fx * screenW - s.anchor.fx * w);
		s.offsetY = y - (s.anchor.fy * screenH - s.anchor.fy * h);
	}

	// ---- editor drag targets (whole module by default; sub-part modules override) ----

	/** An editor drag target — either a whole module or a sub-part with its own position. */
	public interface Draggable {
		Rect bounds();

		void moveTo(float x, float y);

		/** Scroll-wheel scaling in the editor; targets with their own scale override this. */
		default void scaleBy(float delta) {
		}
	}

	/** Editor scroll-scaling clamp shared by modules and parts. */
	public static float clampScale(float v) {
		return Math.max(0.3f, Math.min(3f, v));
	}

	// ---- how far the scale goes ----
	//
	// One pair of numbers for the Scale slider and the editor's scroll wheel both, because a module
	// that answers differently depending on which one you reach for is a module with a bug.

	public float minScale() {
		return 0.5f;
	}

	public float maxScale() {
		return 3f;
	}

	/**
	 * Editor drag targets for this module. Default: the module as a whole. Modules with a
	 * "separated" mode (coords parts, armor pieces) override this to return one per part.
	 */
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		return List.of(new Draggable() {
			@Override
			public Rect bounds() {
				return HudModule.this.bounds(mc, screenW, screenH);
			}

			@Override
			public void moveTo(float x, float y) {
				setPosition(x, y, mc, screenW, screenH);
			}

			@Override
			public void scaleBy(float delta) {
				ModuleSettings s = settings();
				s.scale = Math.max(minScale(), Math.min(maxScale(), s.scale + delta));
			}
		});
	}

	/** Screen rect for a sub-part positioned by {@code p}, given its already-scaled size. */
	public static Rect partBounds(ModuleSettings.PartPos p, float w, float h, float screenW, float screenH) {
		float x = p.anchor.fx * screenW - p.anchor.fx * w + p.ox;
		float y = p.anchor.fy * screenH - p.anchor.fy * h + p.oy;
		return new Rect(x, y, w, h);
	}

	/** Repositions a sub-part so its top-left lands at (x,y), re-picking the nearest anchor. */
	public static void partMoveTo(ModuleSettings.PartPos p, float x, float y, float w, float h,
			float screenW, float screenH) {
		p.anchor = ModuleSettings.Anchor.nearest(x + w / 2f, y + h / 2f, screenW, screenH);
		p.ox = x - (p.anchor.fx * screenW - p.anchor.fx * w);
		p.oy = y - (p.anchor.fy * screenH - p.anchor.fy * h);
	}
}
