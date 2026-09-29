package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.util.Colors;
import dev.clientify.client.util.HurtColorAccess;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * hitcolor+-style custom damage flash: rewrites the hurt rows of the entity overlay texture
 * whenever the wanted color changes (color effects animate per tick).
 *
 * <p>Overlay alpha is INVERTED in vanilla's shader — mix(overlay, base, alpha) — so the
 * "strength" slider maps to alpha = 1 - strength. Vanilla red ≈ 30% strength.
 *
 * <p>Armor flash: vanilla armor never flashes (hardcoded NO_OVERLAY); the toggle routes the
 * wearer's hurt overlay into the equipment layer via the armor-layer mixins.
 */
public class HitColorModule extends HudModule {
	public static class Settings extends ModuleSettings {
		public ColorSpec color = new ColorSpec("#FF0000");
		/** Perceived flash strength; vanilla ≈ 0.3. */
		public float strength = 0.3f;
		public boolean armorFlash = false;

		public Settings() {
			enabled = false;
		}
	}

	private static HitColorModule instance;
	private static final ThreadLocal<Integer> ARMOR_OVERLAY = new ThreadLocal<>();

	private int appliedColor = HurtColorAccess.VANILLA_HURT_COLOR;

	public HitColorModule() {
		super("hitcolor", "Hit Color");
		instance = this;
	}

	@Override
	public String description() {
		return "Recolors the red damage flash on entities.";
	}

	@Override
	public String category() {
		return "MECHANIC";
	}

	@Override
	public boolean isHudElement() {
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

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		screen.addColorRows(rows, "Flash Color", () -> s.color, () -> s.color.copyFrom(d.color));
		rows.add(screen.sliderRow("Strength", 10f, 100f, 5f, () -> s.strength * 100f, v -> s.strength = v / 100f,
				"%.0f%%", () -> s.strength = 0.3f));
		rows.add(screen.toggle("Flash Armor", () -> s.armorFlash, v -> s.armorFlash = v,
				() -> s.armorFlash = false));
	}

	@Override
	public void tick(Minecraft mc) {
		Settings s = (Settings) settings();
		// Texture alpha is the BASE weight: higher slider = stronger flash = lower alpha.
		int wanted = isEnabled()
				? Colors.withAlpha(s.color.chrome(), 1f - s.strength)
				: HurtColorAccess.VANILLA_HURT_COLOR;
		if (wanted != appliedColor && mc.gameRenderer != null) {
			((HurtColorAccess) mc.gameRenderer.overlayTexture()).clientify$setHurtColor(wanted);
			appliedColor = wanted;
		}
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		return 0;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return 0;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
	}

	// ---- armor flash plumbing (called from the armor-layer mixins) ----

	public static boolean armorFlashEnabled() {
		return instance != null && instance.isEnabled()
				&& instance.settings() instanceof Settings s && s.armorFlash;
	}

	public static void setArmorOverlay(int packedOverlay) {
		ARMOR_OVERLAY.set(packedOverlay);
	}

	public static void clearArmorOverlay() {
		ARMOR_OVERLAY.remove();
	}

	public static int overrideArmorOverlay(int original) {
		Integer override = ARMOR_OVERLAY.get();
		return override != null ? override : original;
	}
}
