package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.FeatureModule;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Nametag tweaks (nametagtweaks-style), fair-play by construction: scale caps at vanilla
 * size (1.0), the through-walls toggle can only REMOVE vanilla's see-through pass, and the
 * own-nametag toggle shows your own name in third person (cosmetic). Background gets our
 * color system (no outline — world text).
 */
public class NametagsModule extends FeatureModule {
	public static class Settings extends ModuleSettings {
		public boolean hideAll = false;
		/** Capped at 1.0 — never larger than vanilla (no information advantage). */
		public float tagScale = 1f;
		public boolean tagBackground = true;
		/** Alpha byte is the plate opacity (vanilla ≈ #40000000). */
		public ColorSpec tagBgColor = new ColorSpec("#40000000");
		public ColorSpec tagTextColor = new ColorSpec("#FFFFFF");
		/**
		 * A drop shadow under the name. Off by default, and vanilla has none.
		 *
		 * <p>Worth knowing what it is for: with the plate on, a dark shadow on a dark translucent
		 * plate is close to invisible. It earns its keep with the background turned off, where the
		 * name is otherwise unreadable against a bright sky or a snow field.
		 */
		public boolean tagShadow = false;
		public boolean showOwn = false;

		public Settings() {
			enabled = false;
		}
	}

	/** Kept out of the row so the line breaks stay readable. */
	private static final String SHADOW_TIP =
			"Vanilla nametags have none. Most useful with the\n"
					+ "background off, where a name over bright sky or\n"
					+ "snow has nothing to sit against.";

	private static NametagsModule instance;

	public NametagsModule() {
		super("nametags", "Nametags");
		instance = this;
	}

	@Override
	public String description() {
		return "Tweaks nametags: scale, background, own name.";
	}

	@Override
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	private static Settings active() {
		NametagsModule m = instance;
		return m != null && m.isEnabled() && m.settings() instanceof Settings s ? s : null;
	}

	/** True when the mixin should replace vanilla's nametag submission entirely. */
	public static boolean takeOver() {
		return active() != null;
	}

	// ---- mixin hooks ----

	public static boolean hideAll() {
		Settings s = active();
		return s != null && s.hideAll;
	}

	public static float scaleFactor() {
		Settings s = active();
		return s == null ? 1f : Math.min(1f, s.tagScale);
	}

	/** The tag plate ARGB; vanilla's value passes through when the module is off. */
	public static int backgroundArgb(int vanilla) {
		Settings s = active();
		if (s == null) {
			return vanilla;
		}
		return s.tagBackground ? s.tagBgColor.argbAt(0, 1) : 0;
	}

	/** Nametag text color; vanilla's translucent white passes through when unset. */
	public static int textColor(int vanilla) {
		Settings s = active();
		if (s == null) {
			return vanilla;
		}
		// Keep vanilla's alpha, take our RGB.
		return (vanilla & 0xFF000000) | (s.tagTextColor.chrome() & 0xFFFFFF);
	}

	/** NameTagFeatureRendererMixin: true to have the font draw the tag text with its shadow. */
	public static boolean textShadow() {
		Settings s = active();
		return s != null && s.tagShadow;
	}

	/** True when {@code entity}'s own nametag should be forced on (third person only). */
	public static boolean showOwnFor(Entity entity) {
		Settings s = active();
		if (s == null || !s.showOwn) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		return entity == mc.getCameraEntity() && mc.options.getCameraType() != CameraType.FIRST_PERSON;
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.dualToggle("Hide All", () -> s.hideAll, v -> s.hideAll = v,
				"Show Own Tag", () -> s.showOwn, v -> s.showOwn = v));
		rows.add(screen.sliderRow("Tag Scale", 0.5f, 1f, 0.05f, () -> s.tagScale, v -> s.tagScale = v,
				"%.2f", () -> s.tagScale = 1f));
		screen.addColorRows(rows, "Text Color", () -> s.tagTextColor,
				() -> s.tagTextColor.copyFrom(d.tagTextColor));
		rows.add(screen.withTooltip(
				screen.toggle("Text Shadow", () -> s.tagShadow, v -> s.tagShadow = v,
						() -> s.tagShadow = d.tagShadow),
				SHADOW_TIP));
		rows.add(screen.toggle("Background", () -> s.tagBackground, v -> s.tagBackground = v,
				() -> s.tagBackground = true));
		if (s.tagBackground) {
			screen.addColorRows(rows, "Background Color", () -> s.tagBgColor,
					() -> s.tagBgColor.copyFrom(d.tagBgColor));
		}
	}
}
