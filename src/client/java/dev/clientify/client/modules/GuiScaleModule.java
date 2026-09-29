package dev.clientify.client.modules;

import com.mojang.blaze3d.platform.Window;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.PanelScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

/**
 * Sizes the hotbar and the inventory independently of the game's one GUI Scale, which is all or
 * nothing: a scale small enough to keep menus readable leaves the hotbar tiny, and the other way
 * round. Both start at the game's own value and only depart from it when asked to.
 *
 * <p>The hotbar is scaled by wrapping vanilla's own HUD layers, all of the bottom cluster together
 * so the bars stay where they belong relative to the slots.
 *
 * <p>The inventory gets a canvas of its own instead. A screen lays itself out in the width and
 * height it is handed ({@code Screen.init}), so it is handed the canvas its own scale would give,
 * drawn under a matching transform, and the pointer is converted into that same space on the way
 * in ({@code ScreenMixin}, {@code MouseHandlerMixin}). Nothing else in the frame is touched — which
 * is the point: the earlier version changed the window's GUI scale outright, and that took the HUD
 * behind the screen with it, hotbar and all.
 *
 * <p>Two details keep it honest. The canvas is only reported to the screen while the screen itself
 * is drawing ({@code WindowMixin}), so the HUD never sees it; and GUI items are rasterised at the
 * window's scale into a shared atlas, so that raster is lifted to the larger of the two scales
 * ({@code GuiRendererMixin}) or the items in a scaled-up inventory come out soft.
 */
public class GuiScaleModule extends HudModule {
	/** Vanilla's bottom-of-screen cluster: the slots plus everything anchored around them. */
	private static final Identifier[] BOTTOM_CLUSTER = {
			VanillaHudElements.HOTBAR,
			VanillaHudElements.ARMOR_BAR,
			VanillaHudElements.HEALTH_BAR,
			VanillaHudElements.FOOD_BAR,
			VanillaHudElements.AIR_BAR,
			VanillaHudElements.MOUNT_HEALTH,
			VanillaHudElements.INFO_BAR,
			VanillaHudElements.EXPERIENCE_LEVEL,
			VanillaHudElements.HELD_ITEM_TOOLTIP,
	};

	public static class Settings extends ModuleSettings {
		public boolean customHotbar = false;
		public float hotbarScale = 1.0f;
		public boolean customInventory = false;
		/** A GUI Scale in the game's own terms; vanilla clamps it to what the window can show. */
		public int inventoryScale = 3;
		/** Off: container screens only. On: every menu, including pause and options. */
		public boolean allScreens = false;

		/** Clientify's own menus: dense with rows, and rarely wanting the size the HUD wants. */
		public boolean customMenu = false;
		public int menuScale = 3;
	}

	private static GuiScaleModule instance;
	/** Set while a screen with a canvas of its own is drawing; see {@link #canvasWidth}. */
	private static boolean drawingScaled;
	/** The scale that screen is being drawn at. */
	private static int drawingScale;
	/** The screen laid out last, and the scale it was laid out at, so a setting that changes
	 * under an open screen re-lays it out instead of leaving its clicks where they used to be. */
	private static Screen laidOutScreen;
	private static int laidOutScale;

	public GuiScaleModule() {
		super("guiscale", "GUI Scale");
		instance = this;
		for (Identifier id : BOTTOM_CLUSTER) {
			HudElementRegistry.replaceElement(id, original -> (graphics, delta) -> {
				float scale = hotbarScale();
				if (scale == 1f) {
					original.extractRenderState(graphics, delta);
					return;
				}
				pushHotbarScale(graphics);
				original.extractRenderState(graphics, delta);
				graphics.pose().popMatrix();
			});
		}
	}

	@Override
	public String description() {
		return "Scales the hotbar and the inventory on their own.";
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

	private static Settings active() {
		return instance != null && instance.isEnabled() && instance.settings() instanceof Settings s ? s : null;
	}

	// ---- hotbar ----

	/** 1 when the hotbar is left at the game's size. */
	public static float hotbarScale() {
		Settings s = active();
		return s != null && s.customHotbar ? s.hotbarScale : 1f;
	}

	/**
	 * Scales about the bottom centre of the screen, the point the whole cluster is arranged
	 * around, so it grows and shrinks in place instead of drifting off a corner. The caller pops.
	 */
	public static void pushHotbarScale(GuiGraphicsExtractor g) {
		float scale = hotbarScale();
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight();
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		g.pose().translate(-cx, -cy);
	}

	/** True when anything drawn onto the vanilla bars needs the same transform to line up. */
	public static boolean hotbarScaled() {
		return hotbarScale() != 1f;
	}

	// ---- inventory / screens ----

	/**
	 * The GUI Scale {@code screen} should get, or 0 to leave it at the game's own.
	 *
	 * <p>Clientify's own menus are never rescaled. The editor lays modules out against the canvas
	 * the HUD draws on, so giving it a different one would have it save positions the HUD never
	 * uses — and the rest of our menus are built to sit at the game's size.
	 */
	private static int targetScale(Screen screen) {
		Settings s = active();
		if (s == null || screen == null) {
			return 0;
		}
		if (!s.customInventory && !(screen instanceof PanelScreen)) {
			return 0;
		}
		if (screen instanceof HudEditorScreen) {
			// Never the editor: it lays modules out against the canvas the HUD draws on, and a
			// different one would have it save positions the HUD never uses.
			return 0;
		}
		if (screen instanceof PanelScreen) {
			return menuTarget(s);
		}
		if (!(screen instanceof AbstractContainerScreen<?>) && !s.allScreens) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		// Vanilla's own clamp, so a scale this window cannot fit degrades exactly as the game's
		// GUI Scale option would rather than shrinking the screen past what it can show.
		int want = window.calculateScale(Math.max(1, s.inventoryScale), mc.isEnforceUnicode());
		return want == window.getGuiScale() ? 0 : want;
	}

	/**
	 * The scale our own menus should get, or 0 for the game's own.
	 *
	 * <p>Through the same path the inventory uses, which is the point: that path already moves
	 * the layout size, the draw transform AND the pointer mapping together. Scaling the panel by
	 * hand would look right and click wrong.
	 */
	private static int menuTarget(Settings s) {
		if (!s.customMenu) {
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		int want = window.calculateScale(Math.max(1, s.menuScale), mc.isEnforceUnicode());
		return want == window.getGuiScale() ? 0 : want;
	}

	/** Vanilla's canvas arithmetic: whole logical pixels, rounded up (see Window.setGuiScale). */
	private static int canvas(int pixels, int scale) {
		double exact = pixels / (double) scale;
		int whole = (int) exact;
		return exact > whole ? whole + 1 : whole;
	}

	/** Screen.init/resize: the width the screen should lay itself out in. */
	public static int layoutWidth(Screen screen, int vanilla) {
		int target = targetScale(screen);
		return target == 0 ? vanilla : canvas(Minecraft.getInstance().getWindow().getWidth(), target);
	}

	/** Screen.init/resize: the height the screen should lay itself out in. */
	public static int layoutHeight(Screen screen, int vanilla) {
		int target = targetScale(screen);
		return target == 0 ? vanilla : canvas(Minecraft.getInstance().getWindow().getHeight(), target);
	}

	/**
	 * Starts drawing {@code screen}: returns the factor its drawing needs (1 to leave it alone) and
	 * arms the canvas the screen reads while it draws. Always paired with {@link #endScreenDraw}.
	 */
	public static float beginScreenDraw(Screen screen) {
		int target = targetScale(screen);
		drawingScaled = target != 0;
		drawingScale = target;
		scaledPictures.clear();
		pictureScale = target == 0 ? 1f : (float) target / Minecraft.getInstance().getWindow().getGuiScale();
		return pictureScale;
	}

	/** Ends it; true when {@link #beginScreenDraw} pushed a matrix that now needs popping. */
	public static boolean endScreenDraw() {
		boolean pushed = drawingScaled;
		drawingScaled = false;
		return pushed;
	}

	/**
	 * WindowMixin: the GUI canvas width. Inside a rescaled screen's own drawing that is the
	 * screen's canvas — anything it measures against the screen's edges then lands where the
	 * screen actually ends, tooltips included. Everywhere else it is the game's.
	 */
	public static int canvasWidth(int vanilla) {
		return drawingScaled ? canvas(Minecraft.getInstance().getWindow().getWidth(), drawingScale) : vanilla;
	}

	/** WindowMixin: the GUI canvas height; see {@link #canvasWidth}. */
	public static int canvasHeight(int vanilla) {
		return drawingScaled ? canvas(Minecraft.getInstance().getWindow().getHeight(), drawingScale) : vanilla;
	}

	/**
	 * MouseHandlerMixin: converts a pointer position into the open screen's own space, so clicks
	 * and hovers land on what is drawn. Every screen event goes through the two helpers this
	 * scales, and they are the only place the game turns pixels into GUI coordinates.
	 */
	public static double mouseFactor() {
		int target = targetScale(Minecraft.getInstance().screen);
		return target == 0 ? 1.0 : Minecraft.getInstance().getWindow().getGuiScale() / (double) target;
	}

	// ---- pictures in pictures ----
	//
	// The player preview, sign text, banner results, the enchanting book: each is rendered to its
	// own texture and blitted back afterwards, and that blit deliberately carries NO transform —
	// PictureInPictureRenderState.pose() is the identity. Its scissor, on the other hand, is taken
	// at submit time and does carry one. So inside a rescaled screen the two disagree: the picture
	// lands at unscaled coordinates and is then clipped away by a scissor around where it should
	// have been, which is why the player panel came out empty. Their coordinates are scaled here
	// instead, which is the same thing the transform would have done to them.

	/** Pictures submitted while a rescaled screen was drawing, by identity — they are records, and
	 * two equal ones from different sources must not share an answer. */
	private static final java.util.Set<Object> scaledPictures =
			java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
	/** The factor those pictures need. */
	private static float pictureScale = 1f;
	/** The factor for the picture being drawn right now. */
	private static float drawingPicture = 1f;

	/** Set for the one bounds read that decides where the picture just tagged is layered. */
	private static boolean pictureBoundsPending;

	/** GuiRenderStateMixin: notes a picture as belonging to the screen being rescaled. */
	public static void tagPictureInPicture(Object state) {
		boolean tagged = drawingScaled && scaledPictures.size() < 256;
		if (tagged) {
			scaledPictures.add(state);
		}
		pictureBoundsPending = tagged;
	}

	/**
	 * GuiRenderStateMixin: the box a picture is layered by, which has to be the box it will
	 * actually land in or it is stacked against the wrong neighbours. Read once, immediately after
	 * the picture is tagged, so every other element's bounds pass through untouched.
	 */
	public static net.minecraft.client.gui.navigation.ScreenRectangle scalePictureBounds(
			net.minecraft.client.gui.navigation.ScreenRectangle bounds) {
		if (!pictureBoundsPending) {
			return bounds;
		}
		pictureBoundsPending = false;
		if (bounds == null) {
			return null;
		}
		return new net.minecraft.client.gui.navigation.ScreenRectangle(
				Math.round(bounds.left() * pictureScale), Math.round(bounds.top() * pictureScale),
				Math.round(bounds.width() * pictureScale), Math.round(bounds.height() * pictureScale));
	}

	/** PictureInPictureRendererMixin: starts drawing one, whoever submitted it. */
	public static void beginPictureInPicture(Object state) {
		drawingPicture = scaledPictures.contains(state) ? pictureScale : 1f;
	}

	public static void endPictureInPicture() {
		drawingPicture = 1f;
	}

	/** A picture's coordinate, where the screen's transform would have put it. */
	public static int scalePicture(int coordinate) {
		return drawingPicture == 1f ? coordinate : Math.round(coordinate * drawingPicture);
	}

	/** Its model scale, so the texture it is rendered into keeps pace with the bigger blit. */
	public static float scalePicture(float scale) {
		return scale * drawingPicture;
	}

	/**
	 * GuiRendererMixin: the scale GUI items are rasterised at. Items go into one shared atlas at
	 * 16 pixels per scale step and are then blitted under whatever transform is in force, so an
	 * inventory drawn larger than the window's scale would magnify that raster. Rasterising at the
	 * larger of the two costs a bigger atlas and keeps them sharp.
	 */
	public static int itemAtlasScale(int windowScale) {
		int target = targetScale(Minecraft.getInstance().screen);
		return Math.max(windowScale, target);
	}

	// ---- settings ----

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.toggle("Custom Hotbar Size", () -> s.customHotbar, v -> s.customHotbar = v,
				() -> s.customHotbar = d.customHotbar));
		if (s.customHotbar) {
			rows.add(screen.sliderRow("Hotbar Scale", 0.5f, 2f, 0.05f, () -> s.hotbarScale,
					v -> s.hotbarScale = v, "%.2f", () -> s.hotbarScale = d.hotbarScale));
		}
		rows.add(screen.toggle("Custom Inventory Size", () -> s.customInventory,
				v -> s.customInventory = v, () -> s.customInventory = d.customInventory));
		if (s.customInventory) {
			rows.add(screen.sliderRow("Inventory Scale", 1f, 6f, 1f, () -> (float) s.inventoryScale,
					v -> s.inventoryScale = Math.round(v), "%.0f",
					() -> s.inventoryScale = d.inventoryScale));
			rows.add(screen.toggle("Apply To All Menus", () -> s.allScreens, v -> s.allScreens = v,
					() -> s.allScreens = d.allScreens));
		}
		rows.add(screen.withTooltip(
				screen.toggle("Custom Menu Size", () -> s.customMenu, v -> s.customMenu = v,
						() -> s.customMenu = d.customMenu),
				"Clientify's own panels. The HUD editor is left alone:\n"
						+ "it lays modules out on the canvas the HUD draws on."));
		if (s.customMenu) {
			rows.add(screen.sliderRow("Menu Scale", 1f, 6f, 1f, () -> (float) s.menuScale,
					v -> s.menuScale = Math.round(v), "%.0f", () -> s.menuScale = d.menuScale));
		}
	}

	/**
	 * A screen lays itself out once, at the scale in force when it opened. If the setting changes
	 * underneath it — a profile switch, another mod — it is told to lay out again, since otherwise
	 * it would keep taking clicks where its buttons used to be. Opening a screen is not a change:
	 * it laid itself out on the way in, and re-laying it out would clear anything typed into it.
	 */
	@Override
	public void tick(Minecraft mc) {
		Screen screen = mc.screen;
		int want = targetScale(screen);
		if (screen != laidOutScreen) {
			laidOutScreen = screen;
			laidOutScale = want;
		} else if (want != laidOutScale) {
			laidOutScale = want;
			if (screen != null) {
				Window window = mc.getWindow();
				// The layout hook turns these into the screen's own canvas.
				screen.resize(window.getGuiScaledWidth(), window.getGuiScaledHeight());
			}
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
}
