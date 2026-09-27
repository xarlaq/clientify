package dev.clientify.client.modules;

import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import java.util.List;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * OptiFine/Zoomify-style hold-to-zoom (C by default): FOV divide via GameRendererMixin,
 * scroll adjusts the factor while zooming (MouseHandlerMixin), optional smooth transition
 * and cinematic camera.
 */
public class ZoomModule extends HudModule {
	public static class Settings extends ModuleSettings {
		public float factor = 4f;
		public boolean smooth = true;
		public boolean scrollAdjust = true;
		public boolean cinematicCamera = false;
		/**
		 * Whether the held item zooms along with the view. Vanilla draws the hand at a fixed 70°
		 * whatever your FOV setting is, so leaving this off makes zooming look like dragging the
		 * FOV slider; turning it on makes it look down a scope. Zoomify's "Affect Hand FOV".
		 */
		public boolean zoomHand = false;

		public Settings() {
			enabled = true;
		}
	}

	private static ZoomModule instance;
	private final KeyMapping key;

	private static float progress; // 0 = no zoom, 1 = fully zoomed
	private static float liveFactor = 4f;
	private boolean wasZooming;
	private boolean savedSmoothCamera;

	public ZoomModule() {
		super("zoom", "Zoom");
		instance = this;
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.clientify.zoom", InputConstants.KEY_C, ClientifyClient.KEY_CATEGORY));
	}

	@Override
	public String description() {
		return "Hold a key to zoom in; scroll to adjust.";
	}

	@Override
	public String category() {
		return "MECHANIC";
	}

	@Override
	public boolean isHudElement() {
		return false;
	}

	private boolean zooming() {
		return isEnabled() && key.isDown();
	}

	/**
	 * Steps the smooth-zoom ramp, once per frame, from renderLevel's head.
	 *
	 * <p>This deliberately does NOT live in the FOV hook. Vanilla asks for the world FOV a variable
	 * number of times per frame — {@code projectPointToScreen} and {@code projectHorizonToScreen}
	 * both call it for the locator bar — so stepping the ramp there made the zoom-in speed depend
	 * on how much happened to be on screen.
	 */
	public static void advanceFrame() {
		if (instance == null || instance.settings() == null) {
			return;
		}
		Settings s = (Settings) instance.settings();
		boolean zooming = instance.zooming();
		if (!zooming && progress <= 0.001f) {
			liveFactor = s.factor;
			progress = 0f;
			return;
		}
		float target = zooming ? 1f : 0f;
		progress = s.smooth ? progress + (target - progress) * 0.25f : target;
	}

	/** Current zoom magnification (1 = not zoomed) — waypoints scale their chips by this. */
	public static float magnification() {
		if (instance == null || instance.settings() == null || progress <= 0.001f) {
			return 1f;
		}
		return 1f + (liveFactor - 1f) * progress;
	}

	/** GameRendererMixin: the world FOV. Pure — reads the ramp, never steps it. */
	public static float modifyFov(float fov) {
		float mag = magnification();
		return mag == 1f ? fov : fov / mag;
	}

	/**
	 * GameRendererMixin: the FOV the held item is drawn at.
	 *
	 * <p>Vanilla pins this at 70° whatever your FOV setting says, which is why the hand keeps its
	 * size while the world zooms — the same as dragging the FOV slider. Left alone by default.
	 *
	 * <p>With Zoom Hand on the hand is magnified too. Measured at 2x and 4x, that carries it off
	 * the edge of the screen: the hand sits well off the view axis, so narrowing the frustum about
	 * the centre takes it outside. That is not a bug in the hook — it is what magnifying an
	 * off-centre object does, and it is what Zoomify's affectHandFov gives you by default. The
	 * screen overlays drawn in the same pass (fire, water) follow it, as they do there too.
	 */
	public static float modifyHandFov(float fov) {
		if (instance == null || instance.settings() == null
				|| !((Settings) instance.settings()).zoomHand) {
			return fov;
		}
		float mag = magnification();
		return mag == 1f ? fov : fov / mag;
	}

	/** MouseHandlerMixin: true = scroll consumed (adjusting zoom, don't switch hotbar slot). */
	public static boolean handleScroll(double vDelta) {
		if (instance == null || instance.settings() == null || !instance.zooming()) {
			return false;
		}
		if (!((Settings) instance.settings()).scrollAdjust) {
			return false;
		}
		liveFactor = Math.max(2f, Math.min(10f, liveFactor + (float) vDelta));
		return true;
	}

	@Override
	public void tick(Minecraft mc) {
		Settings s = (Settings) settings();
		boolean zooming = zooming();
		if (s.cinematicCamera) {
			if (zooming && !wasZooming) {
				savedSmoothCamera = mc.options.smoothCamera;
				mc.options.smoothCamera = true;
			} else if (!zooming && wasZooming) {
				mc.options.smoothCamera = savedSmoothCamera;
			}
		}
		wasZooming = zooming;
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
		rows.add(screen.sliderRow("Zoom Factor", 2f, 10f, 0.5f, () -> s.factor, v -> s.factor = v,
				"%.1fx", () -> s.factor = 4f));
		rows.add(screen.toggle("Smooth Zoom", () -> s.smooth, v -> s.smooth = v, () -> s.smooth = true));
		rows.add(screen.withTooltip(
				screen.toggle("Zoom Hand", () -> s.zoomHand, v -> s.zoomHand = v,
						() -> s.zoomHand = false),
				"Off: your hand stays exactly where it is while the\n"
						+ "view zooms in — the same as lowering your FOV.\n"
						+ "On: the hand is magnified along with the view,\n"
						+ "which from 2x up carries it off the edge of the\n"
						+ "screen. This is Zoomify's Affect Hand FOV, and\n"
						+ "is how Zoomify behaves out of the box."));
		rows.add(screen.toggle("Scroll To Adjust", () -> s.scrollAdjust, v -> s.scrollAdjust = v,
				() -> s.scrollAdjust = true));
		rows.add(screen.toggle("Cinematic Camera", () -> s.cinematicCamera, v -> s.cinematicCamera = v,
				() -> s.cinematicCamera = false));
		// Bound to the real KeyMapping, so it stays in sync with Minecraft's Controls screen.
		rows.add(screen.keybindRow("Zoom Key", key, () -> {
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
