package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Client-side weather override: forces the rain/thunder render levels every tick while
 * enabled (the client never lerps these itself — they only change on server game events,
 * so forcing them is stable). Purely visual; the server's real weather still applies.
 */
public class WeatherModule extends HudModule {
	public enum Weather {
		CLEAR, RAIN, SNOW, THUNDER;

		public String label() {
			return switch (this) {
				case CLEAR -> "Clear";
				case RAIN -> "Rain";
				case SNOW -> "Snow";
				case THUNDER -> "Thunder";
			};
		}
	}

	public static class Settings extends ModuleSettings {
		public Weather weather = Weather.CLEAR;

		public Settings() {
			enabled = false;
		}
	}

	private boolean wasForcing;
	private static WeatherModule instance;

	public WeatherModule() {
		super("weather", "Weather Changer");
		instance = this;
	}

	/** The forced weather, or null when inactive (read by WeatherEffectRendererMixin). */
	public static Weather forced() {
		WeatherModule m = instance;
		if (m == null || !m.isEnabled() || m.settings() == null) {
			return null;
		}
		return ((Settings) m.settings()).weather;
	}

	@Override
	public String description() {
		return "Overrides the weather on your screen only.";
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
		rows.add(screen.cycleRow("Weather", () -> s.weather.label(),
				() -> s.weather = cycle(s.weather, -1), () -> s.weather = cycle(s.weather, 1),
				() -> s.weather = Weather.CLEAR));
	}

	@Override
	public void tick(Minecraft mc) {
		boolean force = isEnabled() && mc.level != null && settings() != null;
		if (force) {
			Weather w = ((Settings) settings()).weather;
			mc.level.setRainLevel(w == Weather.CLEAR ? 0f : 1f);
			mc.level.setThunderLevel(w == Weather.THUNDER ? 1f : 0f);
		} else if (wasForcing && mc.level != null) {
			// Back to the server's truth (thunder level re-syncs on the next weather event).
			mc.level.setRainLevel(mc.level.getLevelData().isRaining() ? 1f : 0f);
			mc.level.setThunderLevel(0f);
		}
		wasForcing = force;
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
}
