package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Action bar as a draggable HUD element: vanilla overlay-message rendering is taken over
 * (GuiActionBarMixin) and drawn at this module's position with its scale, font, color
 * effects and chrome. Vanilla's fade drives the alpha.
 */
public class ActionBarModule extends HudModule {
	private static final int PAD = 3;

	public static class Settings extends ModuleSettings {
		public boolean hide = false;
		public boolean editorPreview = false;

		public Settings() {
			anchor = Anchor.BOTTOM_CENTER;
			// Vanilla translates to (guiWidth/2, guiHeight - 68) and draws the string at
			// (-width/2, -4), so its text top sits at guiHeight - 72. With no background the
			// rect is one line tall, which puts the text at rect.y.
			offsetX = 0;
			offsetY = -63;
			background = false;
		}
	}

	private static ActionBarModule instance;
	private transient Component current;

	public ActionBarModule() {
		super("actionbar", "Action Bar");
		instance = this;
	}

	@Override
	public String description() {
		return "Moves, scales and restyles the action bar text.";
	}

	@Override
	public boolean hasColorSection() {
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

	/** Active settings, or null when vanilla behavior should apply. */
	public static Settings active() {
		ActionBarModule m = instance;
		return m != null && m.isEnabled() && m.settings() instanceof Settings s ? s : null;
	}

	public static ActionBarModule get() {
		return instance;
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		rows.add(screen.dualToggle("Hide", () -> s.hide, v -> s.hide = v,
				"Show In Editor", () -> s.editorPreview, v -> s.editorPreview = v));
	}

	/** With the editor preview off the module leaves the editor entirely (no drag outline). */
	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		return ((Settings) settings()).editorPreview ? super.draggables(mc, screenW, screenH) : List.of();
	}

	/** Text width in the module's own font (screen space, already scaled). */
	private float textWidth(Minecraft mc, Component text) {
		return HudText.width(mc, settings(), text);
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		ModuleSettings s = settings();
		float w = current != null ? textWidth(mc, current) / s.scale : 80;
		return w + s.extraW(PAD);
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		ModuleSettings s = settings();
		return HudText.lineHeight(mc, s) / s.scale + s.extraH(PAD);
	}

	/** Called by GuiActionBarMixin in place of vanilla overlay-message rendering. */
	public void renderMessage(GuiGraphicsExtractor g, Component message, int alpha, boolean vanillaAnimated,
			int animatedColor) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		current = message;
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		drawChromeScreen(g, mc, Math.round(r.x()), Math.round(r.y()), Math.round(r.w()), Math.round(r.h()));

		// Center the drawn text inside the chip so any font measures correctly.
		float tw = HudText.width(mc, s, message);
		float tx = r.x() + (r.w() - tw) / 2f;
		float ty = r.y() + s.insetY(PAD) * s.scale;
		// Keep the server's own colors; vanilla's fade (and its rainbow mode) drive the rest.
		int base = vanillaAnimated ? animatedColor : ((alpha << 24) | 0xFFFFFF);
		HudText.drawComponent(g, mc, s, message, tx, ty, base, null);
	}

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		if (mc.screen instanceof HudEditorScreen && current == null && s.editorPreview) {
			renderMessage(g, Component.literal("Action Bar"), 255, false, 0);
			current = null;
		}
	}
}
