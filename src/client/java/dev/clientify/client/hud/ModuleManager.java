package dev.clientify.client.hud;

import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ClientifyConfig;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.modules.ArmorHudModule;
import dev.clientify.client.modules.CoordsModule;
import dev.clientify.client.modules.EffectsHudModule;
import dev.clientify.client.modules.FpsModule;
import dev.clientify.client.modules.ActionBarModule;
import dev.clientify.client.modules.BossBarModule;
import dev.clientify.client.modules.CrosshairModule;
import dev.clientify.client.modules.CustomTextModule;
import dev.clientify.client.modules.NametagsModule;
import dev.clientify.client.modules.TitleModule;
import dev.clientify.client.modules.FullbrightModule;
import dev.clientify.client.modules.GuiScaleModule;
import dev.clientify.client.modules.HitColorModule;
import dev.clientify.client.modules.HitboxModule;
import dev.clientify.client.modules.ItemCounterModule;
import dev.clientify.client.modules.PingModule;
import dev.clientify.client.modules.ReachModule;
import dev.clientify.client.modules.SaturationModule;
import dev.clientify.client.modules.ScoreboardModule;
import dev.clientify.client.modules.ShulkerTooltipModule;
import dev.clientify.client.modules.TabModule;
import dev.clientify.client.modules.SprintModule;
import dev.clientify.client.modules.TimeModule;
import dev.clientify.client.modules.WaypointsModule;
import dev.clientify.client.modules.WeatherModule;
import dev.clientify.client.modules.ZoomModule;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ModuleManager {
	private static final Map<String, HudModule> MODULES = new LinkedHashMap<>();

	private ModuleManager() {
	}

	public static void init() {
		register(new FpsModule());
		register(new PingModule());
		register(new ReachModule());
		register(new CoordsModule());
		register(new SprintModule());
		register(new EffectsHudModule());
		register(new ArmorHudModule());
		register(new FullbrightModule());
		register(new GuiScaleModule());
		register(new ShulkerTooltipModule());
		register(new HitboxModule());
		register(new HitColorModule());
		register(new SaturationModule());
		register(new CustomTextModule());
		register(new ItemCounterModule());
		register(new WeatherModule());
		register(new TimeModule());
		register(new CrosshairModule());
		register(new ZoomModule());
		register(new NametagsModule());
		register(new TitleModule());
		register(new ActionBarModule());
		register(new BossBarModule());
		register(new ScoreboardModule());
		register(new TabModule());
		register(new WaypointsModule());
		register(new dev.clientify.client.modules.AttackIndicatorModule());
		register(new dev.clientify.client.modules.OverlayModule());
		register(new dev.clientify.client.modules.TotemModule());
		register(new dev.clientify.client.modules.KeystrokesModule());

		ClientifyConfig.load(MODULES.values());
		warnAboutMissingIcons();

		// Before the first vanilla layer: the overlap mask has to start its frame ahead of the
		// modules that render from vanilla positions (boss bar, scoreboard, tab list), which draw
		// scattered through the HUD rather than in the pass below.
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS,
				ClientifyClient.id("frame_start"), (graphics, deltaTracker) -> {
					HudFrame.begin();
					ChromeMask.beginFrame(ClientifyConfig.global().flatBackgrounds);
				});

		// Before CHAT: above the main HUD (hotbar/bars/titles), below chat + tab list.
		// Inherits chat's render condition (hidden with F1), which is what we want.
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				ClientifyClient.id("modules"), ModuleManager::renderHud);
	}

	private static void register(HudModule module) {
		MODULES.put(module.id(), module);
	}

	/**
	 * A module with no icon silently falls back to its initial, which is how "armorhud" spent a
	 * release showing an A — the icon map had it under "armor". Nothing breaks, so say it out loud.
	 */
	private static void warnAboutMissingIcons() {
		java.util.List<String> missing = new java.util.ArrayList<>();
		for (HudModule module : MODULES.values()) {
			if (!dev.clientify.client.gui.ModuleIcons.has(module.id())) {
				missing.add(module.id());
			}
		}
		if (!missing.isEmpty()) {
			ClientifyClient.LOGGER.warn("No menu icon for {} — add it to ModuleIcons and the atlas",
					String.join(", ", missing));
		}
	}

	public static Collection<HudModule> all() {
		return MODULES.values();
	}

	private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		// Also starts a pass here, not only in frame_start: the HUD can be hidden (F1) in ways
		// that skip that element, and a token that never advances would freeze cached content.
		HudFrame.begin();
		BlurBackdrop.newFrame(); // next blurred chip re-captures the world
		// The editor renders modules itself (above its dim layer, with outlines).
		if (Minecraft.getInstance().gui.screen() instanceof HudEditorScreen) {
			return;
		}
		for (HudModule module : MODULES.values()) {
			if (module.isEnabled() && module.isHudElement()) {
				module.render(graphics, deltaTracker);
			}
		}
	}

	public static void tick(Minecraft mc) {
		for (HudModule module : MODULES.values()) {
			module.tick(mc);
		}
	}

	public static void save() {
		ClientifyConfig.save(MODULES.values());
	}

	// ---- profiles (Lunar-style: each profile is a full copy of all module settings) ----

	public static java.util.List<String> profiles() {
		return ClientifyConfig.profileNames();
	}

	public static String activeProfile() {
		return ClientifyConfig.activeProfile();
	}

	public static void switchProfile(String name) {
		ClientifyConfig.switchProfile(MODULES.values(), name);
	}

	public static String createProfile() {
		return ClientifyConfig.createProfile(MODULES.values());
	}

	public static void renameProfile(String oldName, String newName) {
		ClientifyConfig.renameProfile(MODULES.values(), oldName, newName);
	}

	public static void resetProfile(String name) {
		ClientifyConfig.resetProfile(MODULES.values(), name);
	}

	public static void deleteProfile(String name) {
		ClientifyConfig.deleteProfile(MODULES.values(), name);
	}
}
