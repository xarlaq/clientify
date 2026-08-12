package dev.clientify.client.modules;

import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.ModuleManager;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/** Gamma-override fullbright (LightTextureMixin). Toggle keybind, unbound by default. */
public class FullbrightModule extends HudModule {
	public static class Settings extends ModuleSettings {
		public float brightness = 15f;
	}

	private static FullbrightModule instance;
	private final KeyMapping key;

	public FullbrightModule() {
		super("fullbright", "Fullbright");
		instance = this;
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.clientify.fullbright", GLFW.GLFW_KEY_UNKNOWN, ClientifyClient.KEY_CATEGORY));
	}

	/** Queried by LightTextureMixin in place of the gamma option. */
	public static Double gammaOverride() {
		if (instance != null && instance.isEnabled() && instance.settings() instanceof Settings s) {
			return (double) s.brightness;
		}
		return null;
	}

	@Override
	public String description() {
		return "Lights up the whole world.";
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
	public void tick(Minecraft mc) {
		while (key.consumeClick()) {
			settings().enabled = !settings().enabled;
			ModuleManager.save();
		}
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
		rows.add(screen.sliderRow("Brightness", 3f, 15f, 1f, () -> s.brightness,
				v -> s.brightness = v, "%.0f", () -> s.brightness = 15f));
		// Bound to the real KeyMapping, so it stays in sync with Minecraft's Controls screen.
		rows.add(screen.keybindRow("Toggle Key", key, () -> {
			key.setKey(key.getDefaultKey());
			net.minecraft.client.KeyMapping.resetMapping();
		}));
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
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
	}
}
