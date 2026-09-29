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
	/**
	 * The server's own rain and thunder levels, kept while ours are forced so they can be put back.
	 *
	 * <p>26.x has no raining flag to consult afterwards: the client's rain level IS its idea of the
	 * weather, and {@code Level.isRaining()} is only that level compared against 0.2. Forcing
	 * overwrites the one place the truth lived, so it is recorded instead - any level that differs
	 * from what we last wrote was written by the server, since nothing else touches it.
	 */
	private float serverRain;
	private float serverThunder;
	private float wroteRain = Float.NaN;
	private float wroteThunder = Float.NaN;
	/** The level those were read from. A new one starts from its own weather, not the last one's. */
	private Object forcedLevel;
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
			// Whatever is there now and is not what we wrote came from the server.
			float rain = mc.level.getRainLevel(1f);
			float thunder = mc.level.getThunderLevel(1f);
			// A different level has never been forced, so everything in it is the server's.
			boolean fresh = !wasForcing || mc.level != forcedLevel;
			forcedLevel = mc.level;
			if (fresh || rain != wroteRain) {
				serverRain = rain;
			}
			if (fresh || thunder != wroteThunder) {
				serverThunder = thunder;
			}
			Weather w = ((Settings) settings()).weather;
			wroteRain = w == Weather.CLEAR ? 0f : 1f;
			wroteThunder = w == Weather.THUNDER ? 1f : 0f;
			mc.level.setRainLevel(wroteRain);
			mc.level.setThunderLevel(wroteThunder);
		} else if (wasForcing && mc.level != null) {
			// Back to exactly what the server last said, thunder included.
			mc.level.setRainLevel(serverRain);
			mc.level.setThunderLevel(serverThunder);
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
