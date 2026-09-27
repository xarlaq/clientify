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
 * Client-side time override: ClientLevelDataMixin rewrites the day time the renderer reads
 * (sky, sun/moon, lighting) while enabled. The day counter from the server is preserved so
 * the moon phase stays truthful; the server's real time is untouched.
 */
public class TimeModule extends HudModule {
	public enum Preset {
		SUNRISE(23000), DAY(1000), NOON(6000), SUNSET(12000), NIGHT(13000), MIDNIGHT(18000), CUSTOM(-1);

		public final long time;

		Preset(long time) {
			this.time = time;
		}

		public String label() {
			return switch (this) {
				case SUNRISE -> "Sunrise";
				case DAY -> "Day";
				case NOON -> "Noon";
				case SUNSET -> "Sunset";
				case NIGHT -> "Night";
				case MIDNIGHT -> "Midnight";
				case CUSTOM -> "Custom";
			};
		}
	}

	public static class Settings extends ModuleSettings {
		public Preset preset = Preset.CUSTOM;
		public float customTime = 6000;

		public Settings() {
			enabled = false;
		}
	}

	private static TimeModule instance;

	public TimeModule() {
		super("time", "Time Changer");
		instance = this;
	}

	@Override
	public String description() {
		return "Overrides the time of day on your screen only.";
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
		rows.add(screen.cycleRow("Time", () -> s.preset.label(),
				() -> s.preset = cycle(s.preset, -1), () -> s.preset = cycle(s.preset, 1),
				() -> s.preset = Preset.CUSTOM));
		if (s.preset == Preset.CUSTOM) {
			rows.add(screen.sliderRowIcons("Ticks", 0f, 24000f, 100f, () -> s.customTime,
					v -> s.customTime = v, "%.0f", () -> s.customTime = 6000f,
					new net.minecraft.resources.Identifier[] {
							dev.clientify.client.gui.Textures.SUN,
							dev.clientify.client.gui.Textures.MOON,
							dev.clientify.client.gui.Textures.SUN}));
		}
	}

	/** Called by ClientLevelDataMixin; keeps the server's day counter, swaps the time of day. */
	/**
	 * The overworld day clock's client instance, as the clock manager last handed it out.
	 *
	 * <p>Kept here rather than in either mixin because mixin classes cannot refer to each other's
	 * statics. Compared by identity only; a stale instance from a previous level is never equal to
	 * the live one, so it is harmless until the next hand-out replaces it.
	 */
	private static Object dayClock;

	public static void dayClock(Object instance) {
		dayClock = instance;
	}

	public static boolean isDayClock(Object instance) {
		return instance == dayClock;
	}

	public static long overrideDayTime(long original) {
		TimeModule m = instance;
		if (m == null || !m.isEnabled() || m.settings() == null) {
			return original;
		}
		Settings s = (Settings) m.settings();
		long timeOfDay = s.preset == Preset.CUSTOM ? (long) s.customTime : s.preset.time;
		return original - Math.floorMod(original, 24000L) + Math.floorMod(timeOfDay, 24000L);
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
