package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.Templated;
import dev.clientify.client.hud.TextHudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Saturation display with three modes: TEXT (a regular text chip), OVERLAY (AppleSkin-style
 * tinted fill drawn over the vanilla hunger bar — fixed position, not draggable), and BAR
 * (a second, draggable hunger-style bar whose fill is your saturation).
 */
public class SaturationModule extends TextHudModule implements Templated {
	private static final String PLACEHOLDER = "%sat%";
	private static final Identifier FOOD_EMPTY = Identifier.withDefaultNamespace("hud/food_empty");
	private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
	private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
	/** Vanilla food bar geometry: 10 icons, 8 px pitch, 9 px wide = 81 px. */
	private static final int BAR_W = 81;
	private static final int BAR_H = 9;
	private static final int PAD = 2;

	public enum Mode {
		TEXT, OVERLAY, BAR;

		public String label() {
			return switch (this) {
				case TEXT -> "Text";
				case OVERLAY -> "Overlay";
				case BAR -> "Bar";
			};
		}
	}

	public static class Settings extends ModuleSettings {
		public String template = "%sat%";
		public Mode mode = Mode.TEXT;
		/** TEXT mode: 6.4 rather than 6. Off by default — a whole number is the calmer readout. */
		public boolean decimals = false;
		/** Overlay color (default reproduces AppleSkin's gold; white shows white). */
		public ColorSpec overlayColor = new ColorSpec("#FFD500");
		/** Icon tint for BAR mode (white = untinted vanilla sprites). */
		public ColorSpec barColor = new ColorSpec("#FFFFFF");

		public Settings() {
			offsetY = 39;
			background = false;
		}
	}

	public SaturationModule() {
		super("saturation", "Saturation");
	}

	@Override
	public String description() {
		return "Shows your hidden saturation as text, an overlay, or a bar.";
	}

	@Override
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	private transient boolean pendingBarReposition;

	private Mode mode() {
		return ((Settings) settings()).mode;
	}

	/** Switches mode; when arriving at BAR, queue a one-time move above the hunger bar. */
	private void selectMode(Mode newMode) {
		Settings s = (Settings) settings();
		if (newMode == Mode.BAR && s.mode != Mode.BAR) {
			pendingBarReposition = true;
		}
		s.mode = newMode;
	}

	private static float saturation(Minecraft mc) {
		return mc.player == null ? 0f : mc.player.getFoodData().getSaturationLevel();
	}

	/** OVERLAY and BAR only make sense where vanilla draws survival UI. */
	private static boolean survivalHud(Minecraft mc) {
		return mc.player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer()
				&& !(mc.player.getVehicle() instanceof LivingEntity);
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.cycleRow("Mode", () -> s.mode.label(),
				() -> selectMode(cycle(s.mode, -1)), () -> selectMode(cycle(s.mode, 1)),
				() -> selectMode(Mode.TEXT)));
		if (s.mode == Mode.OVERLAY) {
			screen.addColorRows(rows, "Outline Color", () -> s.overlayColor,
					() -> s.overlayColor.copyFrom(d.overlayColor));
		} else if (s.mode == Mode.BAR) {
			screen.addColorRows(rows, "Fill Color", () -> s.barColor, () -> s.barColor.copyFrom(d.barColor));
		} else {
			rows.add(screen.withTooltip(
					screen.toggle("Decimal", () -> s.decimals, v -> s.decimals = v,
							() -> s.decimals = d.decimals),
					DECIMAL_TIP));
		}
	}

	// ---- TEXT mode (TextHudModule + template) ----

	@Override
	protected String label(Minecraft mc) {
		return "Saturation";
	}

	/** Tooltip for the Decimal toggle, kept out of the row so the escapes stay readable. */
	private static final String DECIMAL_TIP =
			"Off rounds to a whole number. Saturation is eaten in\n"
					+ "fractions, so rounding can read 6 while the next bite\n"
					+ "takes you to 5 — the decimal is what tells you which.";

	@Override
	protected String value(Minecraft mc) {
		float sat = saturation(mc);
		return ((Settings) settings()).decimals
				? String.format(java.util.Locale.ROOT, "%.1f", sat)
				: String.valueOf(Math.round(sat));
	}

	@Override
	protected List<Seg> segments(Minecraft mc) {
		String template = ((Settings) settings()).template;
		int at = template.indexOf(PLACEHOLDER);
		if (at < 0) {
			return List.of(new Seg(template.isEmpty() ? "Saturation" : template, false));
		}
		List<Seg> segs = new ArrayList<>(3);
		if (at > 0) {
			segs.add(new Seg(template.substring(0, at), false));
		}
		segs.add(new Seg(value(mc), true));
		String after = template.substring(at + PLACEHOLDER.length());
		if (!after.isEmpty()) {
			segs.add(new Seg(after, false));
		}
		return segs;
	}

	@Override
	protected List<List<Seg>> lineSegments(Minecraft mc) {
		if (mode() != Mode.TEXT) {
			return List.of();
		}
		return super.lineSegments(mc);
	}

	@Override
	public String template() {
		return ((Settings) settings()).template;
	}

	@Override
	public void setTemplate(String template) {
		((Settings) settings()).template = template;
	}

	@Override
	public String placeholder() {
		return PLACEHOLDER;
	}

	@Override
	public String defaultTemplate() {
		return "%sat%";
	}

	// ---- sizing / dragging per mode ----

	@Override
	public float unscaledWidth(Minecraft mc) {
		return switch (mode()) {
			case TEXT -> super.unscaledWidth(mc);
			case OVERLAY -> 0;
			case BAR -> BAR_W + settings().extraW(PAD);
		};
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return switch (mode()) {
			case TEXT -> super.unscaledHeight(mc);
			case OVERLAY -> 0;
			case BAR -> BAR_H + settings().extraH(PAD);
		};
	}

	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		if (mode() == Mode.OVERLAY) {
			return List.of();
		}
		return super.draggables(mc, screenW, screenH);
	}

	// ---- rendering ----

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (pendingBarReposition && mode() == Mode.BAR) {
			// Park the bar so its icons sit directly above the vanilla hunger icons (right edge
			// at guiWidth/2 + 91, 2px above the hunger row), accounting for the chip padding.
			Settings s = (Settings) settings();
			float leftInset = s.insetX(PAD) * s.scale;
			float topInset = s.insetY(PAD) * s.scale;
			float x = g.guiWidth() / 2f + 91 - leftInset - BAR_W * s.scale;
			float y = g.guiHeight() - 39 - 2 - topInset - BAR_H * s.scale;
			setPosition(x, y, mc, g.guiWidth(), g.guiHeight());
			pendingBarReposition = false;
		}
		switch (mode()) {
			case TEXT -> super.render(g, deltaTracker);
			case OVERLAY -> {
				// The overlay sits on vanilla's hunger icons, so it has to follow them when the
				// interface module resizes the hotbar cluster.
				boolean scaled = GuiScaleModule.hotbarScaled();
				if (scaled) {
					GuiScaleModule.pushHotbarScale(g);
				}
				renderOverlay(g, mc);
				if (scaled) {
					g.pose().popMatrix();
				}
			}
			case BAR -> renderBar(g, mc);
		}
	}

	/** Which fill sprite icon {@code slot} (0..9) gets for {@code sat} points, or null. */
	private static Identifier fillSprite(float sat, int slot) {
		if (sat >= slot * 2 + 2) {
			return FOOD_FULL;
		}
		if (sat >= slot * 2 + 1) {
			return FOOD_HALF;
		}
		return null;
	}

	// ---- AppleSkin's saturation overlay art (Unlicense/public domain, squeek502/AppleSkin):
	// four 9x9 frames hugging the drumstick silhouette, picked by the icon's fraction. ----

	private static final String[][] OVERLAY_FRAMES = {
			{ // quarter (fraction <= 0.25)
					".........",
					".........",
					".........",
					".........",
					"......A..",
					"......A..",
					".....B.AA",
					"......B.A",
					"......BB."},
			{ // half (<= 0.5)
					"...C.....",
					"....D....",
					".....A...",
					"......A..",
					"......A..",
					"......A..",
					"....BB.AA",
					"......B.A",
					"......BB."},
			{ // three quarters (< 1)
					"..CC.....",
					"....D....",
					".....A...",
					"......A..",
					"......A..",
					"..B...A..",
					"...BBB.AA",
					"......B.A",
					"......BB."},
			{ // full (>= 1)
					"..CC.....",
					".D..D....",
					"A....A...",
					"A.....A..",
					".B....A..",
					"..B...A..",
					"...BBB.AA",
					"......B.A",
					"......BB."},
	};

	/**
	 * AppleSkin's shading normalized to white (luminance-preserving), so the tint color IS the
	 * displayed color: white tint = white outline, and the default gold tint reproduces
	 * AppleSkin's original gold art.
	 */
	private static int overlayPalette(char c) {
		return switch (c) {
			case 'A' -> 0xFFC1C1C1; // main tone
			case 'B' -> 0xFFA1A1A1; // dark shade
			case 'C' -> 0xFFFFFFFF; // bright highlight
			case 'D' -> 0xFFEBEBEB; // soft highlight
			default -> 0;
		};
	}

	private static final Identifier[] OVERLAY_IDS = {
			Identifier.fromNamespaceAndPath("clientify", "sat_overlay_0"),
			Identifier.fromNamespaceAndPath("clientify", "sat_overlay_1"),
			Identifier.fromNamespaceAndPath("clientify", "sat_overlay_2"),
			Identifier.fromNamespaceAndPath("clientify", "sat_overlay_3"),
	};
	private static boolean overlayTexturesReady;

	private static void ensureOverlayTextures(Minecraft mc) {
		if (overlayTexturesReady) {
			return;
		}
		for (int f = 0; f < 4; f++) {
			com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(9, 9, true);
			for (int y = 0; y < 9; y++) {
				for (int x = 0; x < 9; x++) {
					img.setPixel(x, y, overlayPalette(OVERLAY_FRAMES[f][y].charAt(x)));
				}
			}
			mc.getTextureManager().register(OVERLAY_IDS[f],
					new net.minecraft.client.renderer.texture.DynamicTexture(() -> "Clientify saturation overlay", img));
		}
		overlayTexturesReady = true;
	}

	/** AppleSkin's overlay: the gold outline frames drawn over the vanilla hunger icons. */
	private void renderOverlay(GuiGraphicsExtractor g, Minecraft mc) {
		if (!survivalHud(mc)) {
			return;
		}
		ensureOverlayTextures(mc);
		Settings s = (Settings) settings();
		float sat = saturation(mc);
		int right = g.guiWidth() / 2 + 91;
		int y = g.guiHeight() - 39;
		for (int slot = 0; slot < 10; slot++) {
			float fraction = sat / 2f - slot;
			if (fraction <= 0) {
				continue;
			}
			int frame = fraction >= 1 ? 3 : (fraction > 0.5f ? 2 : (fraction > 0.25f ? 1 : 0));
			int x = right - slot * 8 - 9;
			g.blit(RenderPipelines.GUI_TEXTURED, OVERLAY_IDS[frame], x, y, 0f, 0f, 9, 9, 9, 9, 9, 9,
					s.overlayColor.argbAt(slot, 10));
		}
	}

	/** A draggable second hunger bar whose fill is the saturation. */
	private void renderBar(GuiGraphicsExtractor g, Minecraft mc) {
		if (!survivalHud(mc)) {
			return;
		}
		Settings s = (Settings) settings();
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		drawChromeScreen(g, mc, Math.round(r.x()), Math.round(r.y()), Math.round(r.w()), Math.round(r.h()));
		float sat = saturation(mc);

		g.pose().pushMatrix();
		g.pose().translate(r.x() + s.insetX(PAD) * s.scale, r.y() + s.insetY(PAD) * s.scale);
		g.pose().scale(s.scale, s.scale);
		for (int slot = 0; slot < 10; slot++) {
			int x = BAR_W - slot * 8 - 9;
			g.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD_EMPTY, x, 0, 9, 9);
			Identifier sprite = fillSprite(sat, slot);
			if (sprite != null) {
				g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, 0, 9, 9, s.barColor.argbAt(slot, 10));
			}
		}
		g.pose().popMatrix();
	}
}
