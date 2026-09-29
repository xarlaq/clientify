package dev.clientify.client.modules;

import com.mojang.blaze3d.platform.NativeImage;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Custom crosshair replacing vanilla's (GuiCrosshairMixin swaps the sprite draw, keeping all
 * vanilla gating: first-person, spectator rules, F3 3D-crosshair, attack indicator). Styles:
 * five bundled presets plus a 15×15 draw-your-own pixel grid. Color runs the full effect
 * system; Vanilla Blend keeps vanilla's invert blending so the crosshair stays visible on
 * any background (the classic look), or turn it off for pure custom colors.
 */
public class CrosshairModule extends HudModule {
	private static final int GRID = 15;
	private static final Identifier CUSTOM_ID = Identifier.fromNamespaceAndPath("clientify", "crosshair_custom");
	private static final Identifier[] PRESETS = {
			Identifier.fromNamespaceAndPath("clientify", "textures/crosshair/preset1.png"),
			Identifier.fromNamespaceAndPath("clientify", "textures/crosshair/preset2.png"),
			Identifier.fromNamespaceAndPath("clientify", "textures/crosshair/preset3.png"),
			Identifier.fromNamespaceAndPath("clientify", "textures/crosshair/preset4.png"),
			Identifier.fromNamespaceAndPath("clientify", "textures/crosshair/preset5.png"),
	};
	private static final int[] PRESET_SIZES = {15, 30, 60, 30, 32};

	public enum Style {
		PRESET_1, PRESET_2, PRESET_3, PRESET_4, PRESET_5, CUSTOM;

		public String label() {
			return this == CUSTOM ? "Custom" : "Preset " + (ordinal() + 1);
		}
	}

	public enum Mirror {
		NONE, HORIZONTAL, VERTICAL, BOTH;

		public String label() {
			return switch (this) {
				case NONE -> "Off";
				case HORIZONTAL -> "Horizontal";
				case VERTICAL -> "Vertical";
				case BOTH -> "Both";
			};
		}
	}

	/** Selectable drawing-canvas resolutions (editor cells shrink to fit the panel). */
	private static final int[] GRID_SIZES = {15, 32, 48, 64};

	public static class Settings extends ModuleSettings {
		public Style style = Style.PRESET_1;
		public float size = 15;
		/** Vanilla's invert blending (visible on any background) vs plain alpha blending. */
		public boolean vanillaBlend = true;
		public ColorSpec color = new ColorSpec("#FFFFFF");
		public int gridSize = 15;
		public Mirror mirror = Mirror.NONE;
		public List<String> pixels = defaultPixels(15);
		// Hit indicator: the crosshair recolors when a targetable entity is in reach.
		public boolean highlightPlayers = false;
		public ColorSpec playerColor = new ColorSpec("#FF4A4A");
		public boolean highlightHostiles = false;
		public ColorSpec hostileColor = new ColorSpec("#FFD500");
		public boolean highlightPassives = false;
		public ColorSpec passiveColor = new ColorSpec("#5CFF5C");

		public Settings() {
			enabled = false;
		}
	}

	/** Default drawing: a simple vanilla-like cross, scaled to the canvas. */
	private static List<String> defaultPixels(int n) {
		int c = n / 2;
		int arm0 = Math.round(n * 0.2f);
		int arm1 = n - 1 - arm0;
		List<String> rows = new ArrayList<>(n);
		for (int y = 0; y < n; y++) {
			StringBuilder row = new StringBuilder(n);
			for (int x = 0; x < n; x++) {
				boolean on = (x == c && y >= arm0 && y <= arm1) || (y == c && x >= arm0 && x <= arm1);
				row.append(on ? '1' : '0');
			}
			rows.add(row.toString());
		}
		return rows;
	}

	/** Rescales the existing drawing to the new canvas resolution (nearest neighbor). */
	private static void resizeGrid(Settings s, int newSize) {
		int old = s.gridSize;
		if (newSize == old) {
			return;
		}
		List<String> out = new ArrayList<>(newSize);
		for (int y = 0; y < newSize; y++) {
			StringBuilder row = new StringBuilder(newSize);
			int sy = y * old / newSize;
			for (int x = 0; x < newSize; x++) {
				int sx = x * old / newSize;
				boolean on = sy < s.pixels.size() && sx < s.pixels.get(sy).length()
						&& s.pixels.get(sy).charAt(sx) == '1';
				row.append(on ? '1' : '0');
			}
			out.add(row.toString());
		}
		s.pixels = out;
		s.gridSize = newSize;
	}

	private static CrosshairModule instance;
	private DynamicTexture customTex;
	private String appliedPixels;
	private int appliedSize;
	private transient String expandedSection;

	public CrosshairModule() {
		super("crosshair", "Custom Crosshair");
		instance = this;
	}

	public static boolean replacesVanilla() {
		return instance != null && instance.isEnabled();
	}

	/** Called by GuiCrosshairMixin in place of the vanilla crosshair sprite draw. */
	public static void renderCurrent(GuiGraphicsExtractor g) {
		if (instance != null) {
			instance.renderCrosshair(g);
		}
	}

	@Override
	public String description() {
		return "Replaces the crosshair: presets or draw your own.";
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

	// ---- settings UI ----

	private boolean pixelAt(Settings s, int x, int y) {
		if (y >= s.pixels.size()) {
			return false;
		}
		String row = s.pixels.get(y);
		return x < row.length() && row.charAt(x) == '1';
	}

	private void setPixel(Settings s, int x, int y, boolean on) {
		while (s.pixels.size() < s.gridSize) {
			s.pixels.add("0".repeat(s.gridSize));
		}
		StringBuilder row = new StringBuilder(s.pixels.get(y));
		while (row.length() < s.gridSize) {
			row.append('0');
		}
		row.setCharAt(x, on ? '1' : '0');
		s.pixels.set(y, row.toString());
	}

	/** Paints a cell plus its mirror twins per the mirror mode. */
	private void paintPixel(Settings s, int x, int y, boolean on) {
		int n = s.gridSize;
		setPixel(s, x, y, on);
		if (s.mirror == Mirror.HORIZONTAL || s.mirror == Mirror.BOTH) {
			setPixel(s, n - 1 - x, y, on);
		}
		if (s.mirror == Mirror.VERTICAL || s.mirror == Mirror.BOTH) {
			setPixel(s, x, n - 1 - y, on);
		}
		if (s.mirror == Mirror.BOTH) {
			setPixel(s, n - 1 - x, n - 1 - y, on);
		}
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Minecraft mc = Minecraft.getInstance();
		// Preset picker: the five bundled crosshairs as images + a pencil tile to draw your own.
		rows.add(screen.tiles(Style.values().length, 24, 4, () -> s.style.ordinal(), (g, i, x, y, size, hover, sel) -> {
			if (i < PRESETS.length) {
				int texSize = PRESET_SIZES[i];
				g.blit(RenderPipelines.GUI_TEXTURED, PRESETS[i], x + 3, y + 3, 0f, 0f, size - 6, size - 6,
						texSize, texSize, texSize, texSize, -1);
			} else if (dev.clientify.client.gui.Textures.ensure()) {
				int n = dev.clientify.client.gui.Textures.SIZE;
				g.blit(RenderPipelines.GUI_TEXTURED, dev.clientify.client.gui.Textures.PENCIL,
						x + 4, y + 4, 0f, 0f, size - 8, size - 8, n, n, n, n, -1);
			}
		}, i -> s.style = Style.values()[i]));
		rows.add(screen.sliderRow("Size", 5f, 45f, 1f, () -> s.size, v -> s.size = v,
				"%.0f", () -> s.size = 15f));
		rows.add(screen.toggle("Vanilla Blend", () -> s.vanillaBlend, v -> s.vanillaBlend = v,
				() -> s.vanillaBlend = true));
		screen.addColorRows(rows, "Color", () -> s.color, () -> s.color.copyFrom(new ColorSpec("#FFFFFF")));
		if (s.style == Style.CUSTOM) {
			rows.add(screen.cycleRow("Canvas Size", () -> s.gridSize + "×" + s.gridSize,
					() -> resizeGrid(s, cycleSize(s.gridSize, -1)),
					() -> resizeGrid(s, cycleSize(s.gridSize, 1)),
					() -> resizeGrid(s, 15)));
			rows.add(screen.cycleRow("Mirror", () -> s.mirror.label(),
					() -> s.mirror = cycle(s.mirror, -1), () -> s.mirror = cycle(s.mirror, 1),
					() -> s.mirror = Mirror.NONE));
			rows.add(screen.pixelGrid(s.gridSize, (x, y) -> pixelAt(s, x, y),
					(x, y, on) -> paintPixel(s, x, y, on)));
			rows.add(screen.button("Clear Pixels", () -> {
				s.pixels.clear();
				for (int i = 0; i < s.gridSize; i++) {
					s.pixels.add("0".repeat(s.gridSize));
				}
			}));
			rows.add(screen.button("Reset To Cross", () -> s.pixels = defaultPixels(s.gridSize)));
		}

		Settings d = new Settings();
		rows.add(screen.section("Hit Indicator"));
		rows.add(screen.toggleGear("Highlight Players", () -> s.highlightPlayers,
				v -> s.highlightPlayers = v, () -> expand("players"),
				() -> "players".equals(expandedSection), () -> s.highlightPlayers = false));
		if ("players".equals(expandedSection)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Player Color",
					() -> s.playerColor, () -> s.playerColor.copyFrom(d.playerColor)));
		}
		rows.add(screen.toggleGear("Highlight Hostiles", () -> s.highlightHostiles,
				v -> s.highlightHostiles = v, () -> expand("hostiles"),
				() -> "hostiles".equals(expandedSection), () -> s.highlightHostiles = false));
		if ("hostiles".equals(expandedSection)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Hostile Color",
					() -> s.hostileColor, () -> s.hostileColor.copyFrom(d.hostileColor)));
		}
		rows.add(screen.toggleGear("Highlight Passives", () -> s.highlightPassives,
				v -> s.highlightPassives = v, () -> expand("passives"),
				() -> "passives".equals(expandedSection), () -> s.highlightPassives = false));
		if ("passives".equals(expandedSection)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Passive Color",
					() -> s.passiveColor, () -> s.passiveColor.copyFrom(d.passiveColor)));
		}
	}

	private void expand(String key) {
		expandedSection = key.equals(expandedSection) ? null : key;
	}

	/**
	 * The hit-indicator tint for the entity currently under the crosshair, or null when none
	 * applies. Fully-invisible players are never highlighted — that would be an ESP-style
	 * advantage — unless they are wearing armor or holding an item, which makes them visible
	 * anyway.
	 */
	private Integer highlightTint(Minecraft mc, Settings s) {
		Entity target = mc.crosshairPickEntity;
		if (target == null) {
			return null;
		}
		if (target instanceof Player player) {
			if (!s.highlightPlayers || !playerVisible(player)) {
				return null;
			}
			return s.playerColor.chrome();
		}
		if (target instanceof Enemy) {
			return s.highlightHostiles ? s.hostileColor.chrome() : null;
		}
		if (target instanceof LivingEntity living) {
			if (!s.highlightPassives || living.isInvisible()) {
				return null;
			}
			return s.passiveColor.chrome();
		}
		return null;
	}

	/** A player is highlightable only when you could actually see them. */
	private static boolean playerVisible(Player player) {
		if (!player.isInvisible()) {
			return true;
		}
		// Armor and held items still render on an invisible player.
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static int cycleSize(int current, int dir) {
		int at = 0;
		for (int i = 0; i < GRID_SIZES.length; i++) {
			if (GRID_SIZES[i] == current) {
				at = i;
			}
		}
		return GRID_SIZES[(at + dir + GRID_SIZES.length) % GRID_SIZES.length];
	}

	// ---- rendering (called from the mixin inside vanilla's crosshair pass) ----

	/** (Re)uploads the custom drawing into the dynamic texture when it changed. */
	private void ensureCustomTexture(Minecraft mc, Settings s) {
		int n = s.gridSize;
		String key = String.join("", s.pixels);
		if (customTex != null && appliedSize == n && key.equals(appliedPixels)) {
			return;
		}
		if (customTex == null || appliedSize != n) {
			customTex = new DynamicTexture(() -> "Clientify custom crosshair", new NativeImage(n, n, true));
			mc.getTextureManager().register(CUSTOM_ID, customTex);
			appliedSize = n;
		}
		NativeImage img = customTex.getPixels();
		if (img == null) {
			return;
		}
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				img.setPixel(x, y, pixelAt(s, x, y) ? 0xFFFFFFFF : 0);
			}
		}
		customTex.upload();
		appliedPixels = key;
	}

	private void renderCrosshair(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		int size = Math.max(1, Math.round(s.size));
		int x = (g.guiWidth() - size) / 2;
		int y = (g.guiHeight() - size) / 2;
		Integer highlight = highlightTint(mc, s);
		int tint = highlight != null ? highlight : s.color.chrome();
		var pipeline = s.vanillaBlend ? RenderPipelines.CROSSHAIR : RenderPipelines.GUI_TEXTURED;

		Identifier tex;
		int texSize;
		if (s.style == Style.CUSTOM) {
			ensureCustomTexture(mc, s);
			tex = CUSTOM_ID;
			texSize = s.gridSize;
		} else {
			tex = PRESETS[s.style.ordinal()];
			texSize = PRESET_SIZES[s.style.ordinal()];
		}
		g.blit(pipeline, tex, x, y, 0f, 0f, size, size, texSize, texSize, texSize, texSize, tint);
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
