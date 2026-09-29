package dev.clientify.client.modules;

import com.mojang.blaze3d.platform.NativeImage;
import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.util.Colors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The attack cooldown indicator, on your terms: vanilla's crosshair pips, vanilla's hotbar bar, or
 * one you draw yourself and place where you like — plus a sound when the swing comes back, and
 * colours that work on all three.
 *
 * <h2>What "ready" means</h2>
 *
 * <p>The bar filling up is not the whole story. A swing counts as a full-strength hit — sweep,
 * crit, and the sprint knockback bonus — when {@code Player.attack} sees a strength above 0.9, and
 * it reads that strength HALF A TICK AHEAD of what the HUD is showing you
 * ({@code getAttackStrengthScale(0.5F)} against the indicator's {@code 0.0F}). So the hit lands
 * before the bar looks full, and by how much depends on the weapon, because half a tick is a
 * bigger slice of a short cooldown:
 *
 * <pre>
 * sword    (attack speed 1.6, 12.5 tick cooldown)  0.9 - 0.5/12.5 = 86%
 * pickaxe  (1.2, 16.7 ticks)                                        87%
 * trident  (1.1, 18.2 ticks)                                        87%
 * axe      (1.0, 20 ticks)                                          88%
 * </pre>
 *
 * <p>A tick of network slack can carry that lower still — the server may have ticked once more
 * before it processes the swing, which for a sword is 0.9 - 1.5/12.5 = 78%. So the practical
 * window for a sword is roughly 78-86%, and the 84% figure PvP players quote sits inside it.
 *
 * <p>The slider therefore defaults to 100 — the honest reading of the cooldown, and what someone
 * who has not thought about any of this expects — and ticks 86 as the recommended value: the
 * lowest a sword can be swung at and still land a guaranteed full-strength, crit-capable hit.
 */
public class AttackIndicatorModule extends HudModule {
	/** Vanilla's own attack-strength threshold for a full-power swing (Player.attack). */
	private static final float STRONG_ATTACK = 0.9f;
	/** How far ahead of the HUD the attack itself reads the cooldown, in ticks. */
	private static final float ATTACK_LOOKAHEAD = 0.5f;
	/**
	 * The recommended Ready At, ticked on the slider.
	 *
	 * <p>A sword swings on a 12.5 tick cooldown and the attack reads the cooldown half a tick ahead
	 * of the HUD, so {@code 0.9 - 0.5/12.5 = 0.86} is the lowest the bar can read and still
	 * guarantee a full-strength, crit-capable hit. Slower weapons sit a little higher, which is what
	 * the note under the slider reports for whatever is actually in hand.
	 */
	private static final float SWORD_FULL_STRENGTH = 86f;
	/** Vanilla only shows the in-range mark for weapons with a cooldown worth watching. */
	private static final float MIN_DELAY_TICKS = 5f;

	private static final Identifier LOADING_TEX = ClientifyClient.id("attack_indicator_loading");
	private static final Identifier READY_TEX = ClientifyClient.id("attack_indicator_ready");
	// Vanilla's own indicator art. Drawing the vanilla styles from these keeps them pixel-identical
	// to the game's, and a resource pack that restyles them restyles ours too.
	private static final Identifier BACKGROUND_SPRITE =
			Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
	private static final Identifier PROGRESS_SPRITE =
			Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");
	private static final Identifier FULL_SPRITE =
			Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
	private static final Identifier HOTBAR_BACKGROUND =
			Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_background");
	private static final Identifier HOTBAR_PROGRESS =
			Identifier.withDefaultNamespace("hud/hotbar_attack_indicator_progress");
	/** Canvas resolutions the drawing editor offers, as with the custom crosshair. */
	private static final int[] GRID_SIZES = {16, 32, 48};

	public enum Mode {
		VANILLA_CROSSHAIR, VANILLA_BAR, CUSTOM;

		public String label() {
			return switch (this) {
				case VANILLA_CROSSHAIR -> "Vanilla Crosshair";
				case VANILLA_BAR -> "Vanilla Bar";
				case CUSTOM -> "Custom";
			};
		}
	}

	/** Which way the charge fills. AUTO is whatever the chosen style does in vanilla. */
	public enum Fill {
		AUTO, LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM, BOTTOM_TO_TOP;

		public String label() {
			return switch (this) {
				case AUTO -> "Default";
				case LEFT_TO_RIGHT -> "Left To Right";
				case RIGHT_TO_LEFT -> "Right To Left";
				case TOP_TO_BOTTOM -> "Top To Bottom";
				case BOTTOM_TO_TOP -> "Bottom To Top";
			};
		}
	}

	public enum ColorMode {
		GRADIENT, THRESHOLD;

		public String label() {
			return this == GRADIENT ? "Low To High" : "Ready Or Not";
		}
	}

	public enum Ding {
		PLING, BELL, EXPERIENCE, CLICK;

		public String label() {
			return switch (this) {
				case PLING -> "Pling";
				case BELL -> "Bell";
				case EXPERIENCE -> "Chime";
				case CLICK -> "Click";
			};
		}

		public SoundEvent sound() {
			return switch (this) {
				case PLING -> SoundEvents.NOTE_BLOCK_PLING.value();
				case BELL -> SoundEvents.NOTE_BLOCK_BELL.value();
				case EXPERIENCE -> SoundEvents.EXPERIENCE_ORB_PICKUP;
				case CLICK -> SoundEvents.UI_BUTTON_CLICK.value();
			};
		}
	}

	public static class Settings extends ModuleSettings {
		public Mode mode = Mode.VANILLA_CROSSHAIR;
		/** Vanilla's invert blending, so the indicator shows up on any background. */
		public boolean vanillaBlend = true;
		/** Draw the charging shape; off leaves only the in-range one. */
		public boolean showLoading = true;
		/** Draw the shape that says the swing would land on what you are looking at. */
		public boolean showReady = true;
		/** Keep it up at full charge instead of hiding it the way vanilla does. */
		public boolean alwaysShow = false;
		/** Off takes it out of the HUD editor, so it cannot be picked up by accident. */
		public boolean showInEditor = true;
		/** Which edge the charge grows from; Default follows the style. */
		public Fill fill = Fill.AUTO;

		/** Colours apply to the drawn indicator AND tint vanilla's own art. */
		public boolean dynamicColor = true;
		public ColorMode colorMode = ColorMode.GRADIENT;
		public ColorSpec lowColor = new ColorSpec("#FFFF0000");
		public ColorSpec highColor = new ColorSpec("#FF00FF00");

		public boolean playSound = false;
		public Ding sound = Ding.PLING;
		public float soundVolume = 1f;

		/**
		 * Where the swing counts as ready, in percent — for the sound, the colour, and where the
		 * bar reads full. 86 is a sword's guaranteed full-strength point; see the class notes.
		 */
		public float readyPercent = 100f;
		/**
		 * The bar reaches full AT that mark rather than at the end of the cooldown, so a full bar
		 * means "swing now" instead of "the timer finished". The last stretch of a vanilla cooldown
		 * is time in which the hit already lands at full strength, and watching it is a habit worth
		 * losing. Off shows the literal cooldown.
		 */
		public boolean fullAtReady = true;

		// The two drawings: one that fills as the swing recharges, one for a target in reach. Each
		// has its own canvas, since a bar and a mark do not want the same resolution.
		public int loadingGrid = 16;
		public int readyGrid = 16;
		public List<String> loadingPixels = defaultBar(16);
		public List<String> readyPixels = defaultMark(16);

		// Which weapons get an indicator at all.
		public boolean forSwords = true;
		public boolean forAxes = true;
		public boolean forTridents = true;
		public boolean forPickaxes = true;
		public boolean forShovels = true;
		/** True by default: switching the module on should not take anything away. */
		public boolean forOther = true;

		public Settings() {
			enabled = false;
			positionFor(this); // vanilla's own slot for the starting style
		}
	}

	/**
	 * Vanilla's own two shapes, pixel for pixel, so a fresh drawing starts as the indicator the
	 * player already knows and is edited from there. Lifted from the game's
	 * crosshair_attack_indicator_progress (the 16x4 bar) and _full (the same bar with the mark
	 * under it), placed at the top of a 16x16 canvas where vanilla draws them.
	 */
	private static final String[] VANILLA_BAR_ART = {
			"0001000000000000",
			"1111111111111111",
			"1111111111111100",
			"0001000000000000"};
	/**
	 * Only the mark, not the bar above it. Vanilla's _full sprite is the bar AND the mark in one
	 * image, because vanilla swaps one sprite for the other; here they are two layers, so the
	 * in-range drawing is just the part that says "in range" and the bar goes on drawing itself
	 * underneath. Same rows as vanilla's, so the two still stack into vanilla's picture.
	 */
	private static final String[] VANILLA_MARK_ART = {
			"0000000000000000",
			"0000000000000000",
			"0000000000000000",
			"0000000000000000",
			"0000000010000000",
			"0000000111000000",
			"0000000010000000"};

	/** The 16-wide art on an n-wide canvas, each pixel repeated whole so it never goes soft. */
	private static List<String> art(String[] rows, int n) {
		int step = Math.max(1, n / 16);
		List<String> out = new ArrayList<>(n);
		for (int y = 0; y < n; y++) {
			int sy = y / step;
			String row = sy < rows.length ? rows[sy] : "";
			StringBuilder sb = new StringBuilder(n);
			for (int x = 0; x < n; x++) {
				int sx = x / step;
				sb.append(sx < row.length() && row.charAt(sx) == '1' ? '1' : '0');
			}
			out.add(sb.toString());
		}
		return out;
	}

	private static List<String> defaultBar(int n) {
		return art(VANILLA_BAR_ART, n);
	}

	private static List<String> defaultMark(int n) {
		return art(VANILLA_MARK_ART, n);
	}

	private static AttackIndicatorModule instance;
	/** Cooldown last tick, so the sound fires once as it crosses rather than every tick after. */
	private float lastProgress = 1f;
	private DynamicTexture loadingTex;
	private DynamicTexture readyTex;
	private String appliedLoading;
	private String appliedReady;
	private int appliedLoadingGrid;
	private int appliedReadyGrid;
	/** Which drawing's dropdown is open; not saved, it is a state of the screen. */
	private transient String expanded;

	public AttackIndicatorModule() {
		super("attackindicator", "Attack Indicator");
		instance = this;
	}

	@Override
	public String description() {
		return "Restyles the attack cooldown indicator.";
	}

	@Override
	public String category() {
		return "MECHANIC";
	}

	/** Every style is drawn by us, so every style has a position of its own. */
	@Override
	public boolean isHudElement() {
		return true;
	}

	@Override
	public boolean hasAppearance() {
		return false; // its own colour rows, and a background would fight the drawing
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

	private static Settings active() {
		return instance != null && instance.isEnabled() && instance.settings() instanceof Settings s ? s : null;
	}

	// ---- what the game should draw ----

	/**
	 * GuiAttackIndicatorMixin: the indicator setting vanilla should act on. Chosen where vanilla
	 * READS the option rather than by writing it, so the value in the options screen stays the
	 * user's own. The drawn mode answers OFF and paints its own from the HUD pass.
	 */
	public static Object vanillaStatus(Object vanilla) {
		// Switched on, the module owns the indicator outright: it draws all three styles itself,
		// so vanilla's own must stand down or the two would sit on top of each other.
		return active() == null ? vanilla : AttackIndicatorStatus.OFF;
	}

	/** True when the held item is one the user asked to see an indicator for. */
	private static boolean appliesToHeldItem(Minecraft mc, Settings s) {
		if (mc.player == null) {
			return false;
		}
		ItemStack held = mc.player.getMainHandItem();
		if (held.is(ItemTags.SWORDS)) {
			return s.forSwords;
		}
		if (held.is(ItemTags.AXES)) {
			return s.forAxes;
		}
		if (held.is(Items.TRIDENT)) {
			return s.forTridents;
		}
		if (held.is(ItemTags.PICKAXES)) {
			return s.forPickaxes;
		}
		if (held.is(ItemTags.SHOVELS)) {
			return s.forShovels;
		}
		return s.forOther;
	}

	/** True once the swing is worth taking — the raw cooldown against the chosen mark. */
	private static boolean isReady(Settings s, float raw) {
		return raw >= s.readyPercent / 100f;
	}

	/**
	 * The charge as the indicator SHOWS it. With Full At Ready the mark becomes the end of the
	 * bar, so the fill runs out exactly when the swing is worth taking.
	 */
	private static float shownProgress(Settings s, float raw) {
		if (!s.fullAtReady) {
			return Math.min(1f, raw);
		}
		float mark = Math.max(0.05f, s.readyPercent / 100f);
		return Math.min(1f, raw / mark);
	}

	/** Colour: a ramp along what is shown, or a straight swap the moment the swing is ready. */
	private static int barColor(Settings s, float shown, boolean ready) {
		int low = s.lowColor.chrome();
		int high = s.highColor.chrome();
		if (s.colorMode == ColorMode.THRESHOLD) {
			return ready ? high : low;
		}
		return Colors.lerp(low, high, Math.min(1f, shown));
	}

	/** Vanilla's test for the full-strength mark: something alive, in reach, worth swinging at. */
	private static boolean targetInRange(Minecraft mc) {
		return mc.crosshairPickEntity instanceof LivingEntity living && living.isAlive()
				&& mc.player.getCurrentItemAttackStrengthDelay() > MIN_DELAY_TICKS;
	}

	// ---- the drawn indicator (a HUD element, so the editor can move it) ----

	/** The box the current style occupies — vanilla's own footprints, or the drawings'. */
	private static int boxSize(Settings s) {
		return switch (s.mode) {
			case VANILLA_CROSSHAIR -> 16;
			case VANILLA_BAR -> 18;
			case CUSTOM -> Math.max(s.loadingGrid, s.readyGrid);
		};
	}

	/**
	 * Exactly where vanilla draws this style, worked back from its own numbers.
	 *
	 * <p>Crosshair: vanilla puts the 16x16 box at (w/2 - 8, h/2 - 7 + 16), so from the centre
	 * anchor that is no shift across and 17 down. Bar: the 18x18 sits at (w/2 + 91 + 6, h - 20),
	 * beside the hotbar on the main-hand side, so from the bottom centre that is 106 across and 2
	 * up. Used for the fresh default and for the Reset Position button.
	 */
	private static void positionFor(Settings s) {
		if (s.mode == Mode.VANILLA_BAR) {
			s.anchor = ModuleSettings.Anchor.BOTTOM_CENTER;
			s.offsetX = 106;
			s.offsetY = -2;
		} else {
			s.anchor = ModuleSettings.Anchor.CENTER;
			s.offsetX = 0;
			s.offsetY = 17;
		}
	}

	/** True while the indicator is still sitting exactly where vanilla would have drawn it. */
	private static boolean atVanillaSpot(Settings s) {
		Settings probe = new Settings();
		probe.mode = s.mode;
		positionFor(probe);
		return s.anchor == probe.anchor && s.offsetX == probe.offsetX && s.offsetY == probe.offsetY;
	}

	/**
	 * Switching style moves it to that style's vanilla spot — but only from the old style's spot.
	 * Anywhere the user put it themselves is left alone.
	 */
	private static void changeMode(Settings s, int dir) {
		boolean unmoved = atVanillaSpot(s);
		s.mode = cycle(s.mode, dir);
		if (unmoved) {
			positionFor(s);
		}
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		return settings() instanceof Settings s ? boxSize(s) : 0;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return settings() instanceof Settings s ? boxSize(s) : 0;
	}

	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		Settings s = (Settings) settings();
		return s != null && s.showInEditor ? super.draggables(mc, screenW, screenH) : List.of();
	}

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		if (s == null || mc.player == null) {
			return;
		}
		boolean editor = mc.screen instanceof HudEditorScreen;
		if (!editor) {
			// Drawing this ourselves means vanilla's own gate no longer applies, so it is
			// re-checked here: no indicator from a third-person camera.
			if (!mc.options.getCameraType().isFirstPerson() || !appliesToHeldItem(mc, s)) {
				return;
			}
		}

		// In the editor there is nothing to charge, so it shows a part-charged sample: without one
		// the chip would be invisible and there would be nothing to pick up and move.
		float raw = editor ? 0.6f : mc.player.getAttackStrengthScale(0f);
		boolean ready = isReady(s, raw);
		float progress = shownProgress(s, raw);
		// Charged means the indicator has nothing left to say — which is the ready mark when the
		// bar ends there, and the end of the cooldown when it does not.
		boolean charged = s.fullAtReady ? ready : raw >= 1f;
		boolean inRange = !editor && charged && targetInRange(mc);
		if (!editor && charged && !inRange && !s.alwaysShow) {
			return;
		}

		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		var pipeline = s.vanillaBlend ? RenderPipelines.CROSSHAIR : RenderPipelines.GUI_TEXTURED;
		if (s.mode != Mode.CUSTOM) {
			renderVanillaStyle(g, s, r, pipeline, progress, ready, inRange);
			return;
		}
		ensureTextures(mc, s);
		// Each drawing is centred in the shared box, so the two line up whatever size they are.
		float unit = r.w() / boxSize(s);

		// The two drawings are LAYERS, not alternatives: the charging one keeps drawing (full, by
		// then) and the in-range one goes over it. That is how vanilla's single full sprite is put
		// together, and it means the in-range drawing only has to be the part that says "in range".
		if (s.showLoading) {
			int size = Math.round(s.loadingGrid * unit);
			int x = Math.round(r.x() + (r.w() - size) / 2f);
			int y = Math.round(r.y() + (r.h() - size) / 2f);
			// The whole drawing, clipped to the charged part — so the same art fills from any edge.
			clipToCharge(g, s, x, y, size, size, progress);
			g.blit(pipeline, LOADING_TEX, x, y, 0f, 0f, size, size, s.loadingGrid, s.loadingGrid,
					s.loadingGrid, s.loadingGrid, tint(s, progress, ready));
			g.disableScissor();
		}
		if (inRange && s.showReady) {
			int size = Math.round(s.readyGrid * unit);
			int x = Math.round(r.x() + (r.w() - size) / 2f);
			int y = Math.round(r.y() + (r.h() - size) / 2f);
			g.blit(pipeline, READY_TEX, x, y, 0f, 0f, size, size, s.readyGrid, s.readyGrid,
					s.readyGrid, s.readyGrid, tint(s, 1f, true));
		}
	}

	private static int tint(Settings s, float shown, boolean ready) {
		return s.dynamicColor ? barColor(s, shown, ready) : 0xFFFFFFFF;
	}

	/**
	 * Only a drawing of your own gets a say in which way it fills. Vanilla's two keep the direction
	 * their art was made for — the hotbar bar grows upward, the crosshair one across — and turning
	 * either sideways just looks like a bug.
	 */
	private static Fill fillOf(Settings s) {
		if (s.mode == Mode.VANILLA_BAR) {
			return Fill.BOTTOM_TO_TOP;
		}
		if (s.mode != Mode.CUSTOM || s.fill == Fill.AUTO) {
			return Fill.LEFT_TO_RIGHT;
		}
		return s.fill;
	}

	/**
	 * Scissors a box down to its charged part, from whichever edge the fill starts at. The whole
	 * shape is then drawn at full size inside it, which is what lets one drawing fill in any
	 * direction without a second copy of it.
	 */
	private static void clipToCharge(GuiGraphicsExtractor g, Settings s, int x, int y, int w, int h,
			float progress) {
		float p = Math.max(0f, Math.min(1f, progress));
		int fw = Math.max(1, Math.round(w * p));
		int fh = Math.max(1, Math.round(h * p));
		switch (fillOf(s)) {
			case RIGHT_TO_LEFT -> g.enableScissor(x + w - fw, y, x + w, y + h);
			case TOP_TO_BOTTOM -> g.enableScissor(x, y, x + w, y + fh);
			case BOTTOM_TO_TOP -> g.enableScissor(x, y + h - fh, x + w, y + h);
			default -> g.enableScissor(x, y, x + fw, y + h);
		}
	}

	/**
	 * Vanilla's own two, drawn by us: same sprites, same proportions, but at the module's position
	 * and scale and in its colours.
	 *
	 * <p>The fill is a scissor rather than a partial sprite blit. Sprites come off an atlas, and
	 * the overload that takes a sub-region draws it at its own size — no scaling — so clipping a
	 * full-size scaled draw is the only way the bar can both fill and follow the scale slider.
	 */
	private void renderVanillaStyle(GuiGraphicsExtractor g, Settings s, Rect r,
			com.mojang.blaze3d.pipeline.RenderPipeline pipeline, float progress, boolean ready,
			boolean inRange) {
		int x = Math.round(r.x());
		int y = Math.round(r.y());
		int w = Math.round(r.w());
		int h = Math.round(r.h());
		if (s.mode == Mode.VANILLA_BAR) {
			g.blitSprite(pipeline, HOTBAR_BACKGROUND, x, y, w, h, 0xFFFFFFFF);
			if (s.showLoading && progress > 0f) {
				clipToCharge(g, s, x, y, w, h, progress);
				g.blitSprite(pipeline, HOTBAR_PROGRESS, x, y, w, h, tint(s, progress, ready));
				g.disableScissor();
			}
			return;
		}
		// Crosshair style: the 16x4 bar sits in the top quarter of the 16x16 box, and the
		// full-strength mark takes the whole box — exactly the two shapes vanilla draws.
		int barH = Math.max(1, Math.round(h / 4f));
		if (inRange && s.showReady) {
			g.blitSprite(pipeline, FULL_SPRITE, x, y, w, h, tint(s, 1f, true));
			return;
		}
		if (!s.showLoading) {
			return;
		}
		g.blitSprite(pipeline, BACKGROUND_SPRITE, x, y, w, barH, 0xFFFFFFFF);
		if (progress > 0f) {
			clipToCharge(g, s, x, y, w, barH, progress);
			g.blitSprite(pipeline, PROGRESS_SPRITE, x, y, w, barH, tint(s, progress, ready));
			g.disableScissor();
		}
	}

	/** (Re)uploads whichever drawing changed; each canvas has its own resolution. */
	private void ensureTextures(Minecraft mc, Settings s) {
		String loadingKey = s.loadingGrid + ":" + String.join("", s.loadingPixels);
		if (!loadingKey.equals(appliedLoading)) {
			if (loadingTex == null || appliedLoadingGrid != s.loadingGrid) {
				loadingTex = new DynamicTexture(() -> "Clientify attack indicator loading",
						new NativeImage(s.loadingGrid, s.loadingGrid, true));
				mc.getTextureManager().register(LOADING_TEX, loadingTex);
				appliedLoadingGrid = s.loadingGrid;
			}
			upload(loadingTex, s.loadingPixels, s.loadingGrid);
			appliedLoading = loadingKey;
		}
		String readyKey = s.readyGrid + ":" + String.join("", s.readyPixels);
		if (!readyKey.equals(appliedReady)) {
			if (readyTex == null || appliedReadyGrid != s.readyGrid) {
				readyTex = new DynamicTexture(() -> "Clientify attack indicator ready",
						new NativeImage(s.readyGrid, s.readyGrid, true));
				mc.getTextureManager().register(READY_TEX, readyTex);
				appliedReadyGrid = s.readyGrid;
			}
			upload(readyTex, s.readyPixels, s.readyGrid);
			appliedReady = readyKey;
		}
	}

	private static void upload(DynamicTexture texture, List<String> pixels, int n) {
		NativeImage img = texture.getPixels();
		if (img == null) {
			return;
		}
		for (int y = 0; y < n; y++) {
			String row = y < pixels.size() ? pixels.get(y) : "";
			for (int x = 0; x < n; x++) {
				boolean on = x < row.length() && row.charAt(x) == '1';
				img.setPixel(x, y, on ? 0xFFFFFFFF : 0);
			}
		}
		texture.upload();
	}

	// ---- the sound ----

	@Override
	public void tick(Minecraft mc) {
		Settings s = active();
		if (s == null || mc.player == null) {
			lastProgress = 1f;
			return;
		}
		float progress = mc.player.getAttackStrengthScale(0f);
		float threshold = s.readyPercent / 100f;
		boolean crossed = lastProgress < threshold && progress >= threshold;
		lastProgress = progress;
		if (!crossed || !s.playSound || !appliesToHeldItem(mc, s)) {
			return;
		}
		if (mc.player.getCurrentItemAttackStrengthDelay() <= MIN_DELAY_TICKS) {
			return; // no cooldown worth announcing
		}
		mc.getSoundManager().play(SimpleSoundInstance.forUI(s.sound.sound(),
				2f, Math.max(0.05f, s.soundVolume)));
	}

	/**
	 * The percentage the HUD shows when the NEXT swing would already be a full-strength one, for
	 * the weapon in hand. Vanilla reads the cooldown half a tick ahead of the indicator, so this
	 * sits below 90 by an amount that depends on the weapon's speed.
	 */
	private static String weaponThresholdText(Minecraft mc) {
		Player player = mc.player;
		if (player == null) {
			return "-";
		}
		float delay = player.getCurrentItemAttackStrengthDelay();
		if (delay <= 0) {
			return "-";
		}
		return Math.round(Math.max(0f, STRONG_ATTACK - ATTACK_LOOKAHEAD / delay) * 100f) + "%";
	}

	// ---- settings ----

	private boolean pixelAt(List<String> pixels, int x, int y) {
		if (y >= pixels.size()) {
			return false;
		}
		String row = pixels.get(y);
		return x < row.length() && row.charAt(x) == '1';
	}

	private void setPixel(List<String> pixels, int grid, int x, int y, boolean on) {
		while (pixels.size() < grid) {
			pixels.add("0".repeat(grid));
		}
		StringBuilder row = new StringBuilder(pixels.get(y));
		while (row.length() < grid) {
			row.append('0');
		}
		row.setCharAt(x, on ? '1' : '0');
		pixels.set(y, row.toString());
	}

	private void expand(String key) {
		expanded = key.equals(expanded) ? null : key;
	}

	private static int cycleGrid(int current, int dir) {
		int at = 0;
		for (int i = 0; i < GRID_SIZES.length; i++) {
			if (GRID_SIZES[i] == current) {
				at = i;
			}
		}
		return GRID_SIZES[(at + dir + GRID_SIZES.length) % GRID_SIZES.length];
	}

	/** Rescales a drawing when its canvas resolution changes (nearest neighbour). */
	private static List<String> resize(List<String> pixels, int old, int size) {
		List<String> out = new ArrayList<>(size);
		for (int y = 0; y < size; y++) {
			StringBuilder row = new StringBuilder(size);
			int sy = y * old / size;
			for (int x = 0; x < size; x++) {
				int sx = x * old / size;
				boolean on = sy < pixels.size() && sx < pixels.get(sy).length()
						&& pixels.get(sy).charAt(sx) == '1';
				row.append(on ? '1' : '0');
			}
			out.add(row.toString());
		}
		return out;
	}

	private void setLoadingGrid(Settings s, int size) {
		if (size != s.loadingGrid) {
			s.loadingPixels = resize(s.loadingPixels, s.loadingGrid, size);
			s.loadingGrid = size;
		}
	}

	private void setReadyGrid(Settings s, int size) {
		if (size != s.readyGrid) {
			s.readyPixels = resize(s.readyPixels, s.readyGrid, size);
			s.readyGrid = size;
		}
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();

		rows.add(screen.cycleRow("Display Mode", () -> s.mode.label(),
				() -> changeMode(s, -1), () -> changeMode(s, 1),
				() -> {
					s.mode = d.mode;
					positionFor(s);
				}));

		// The two drawings sit straight under the mode that uses them, each behind its own gear:
		// two pixel canvases open at once is more of the screen than either one deserves.
		if (s.mode == Mode.CUSTOM) {
			rows.add(screen.toggleGear("Charging", () -> s.showLoading, v -> s.showLoading = v,
					() -> expand("loading"), () -> "loading".equals(expanded),
					() -> s.showLoading = d.showLoading));
			if ("loading".equals(expanded)) {
				screen.groupCard(rows, group -> {
					group.add(screen.cycleRow("Canvas Size", () -> s.loadingGrid + "×" + s.loadingGrid,
							() -> setLoadingGrid(s, cycleGrid(s.loadingGrid, -1)),
							() -> setLoadingGrid(s, cycleGrid(s.loadingGrid, 1)),
							() -> setLoadingGrid(s, d.loadingGrid)));
					group.add(screen.note(() -> "Fills left to right as the swing recharges"));
					group.add(screen.pixelGrid(s.loadingGrid, (x, y) -> pixelAt(s.loadingPixels, x, y),
							(x, y, on) -> setPixel(s.loadingPixels, s.loadingGrid, x, y, on)));
					group.add(screen.button("Reset Drawing",
							() -> s.loadingPixels = defaultBar(s.loadingGrid)));
				});
			}
			rows.add(screen.cycleRow("Fill Direction", () -> s.fill.label(),
					() -> s.fill = cycle(s.fill, -1), () -> s.fill = cycle(s.fill, 1),
					() -> s.fill = d.fill));
			rows.add(screen.toggleGear("In Range", () -> s.showReady, v -> s.showReady = v,
					() -> expand("ready"), () -> "ready".equals(expanded),
					() -> s.showReady = d.showReady));
			if ("ready".equals(expanded)) {
				screen.groupCard(rows, group -> {
					group.add(screen.cycleRow("Canvas Size", () -> s.readyGrid + "×" + s.readyGrid,
							() -> setReadyGrid(s, cycleGrid(s.readyGrid, -1)),
							() -> setReadyGrid(s, cycleGrid(s.readyGrid, 1)),
							() -> setReadyGrid(s, d.readyGrid)));
					group.add(screen.note(() -> "Shown when the swing would land on a target"));
					group.add(screen.pixelGrid(s.readyGrid, (x, y) -> pixelAt(s.readyPixels, x, y),
							(x, y, on) -> setPixel(s.readyPixels, s.readyGrid, x, y, on)));
					group.add(screen.button("Reset Drawing",
							() -> s.readyPixels = defaultMark(s.readyGrid)));
				});
			}
		}

		// Position, blending and the two visibility switches apply to every style, because the
		// module draws every style — vanilla's two included.
		rows.add(screen.dualToggle("Show In Editor", () -> s.showInEditor, v -> s.showInEditor = v,
				"Vanilla Blend", () -> s.vanillaBlend, v -> s.vanillaBlend = v));
		rows.add(screen.dualToggle("Always Show", () -> s.alwaysShow, v -> s.alwaysShow = v,
				"Show Charging", () -> s.showLoading, v -> s.showLoading = v));
		rows.add(screen.button("Reset Position", () -> positionFor(s)));

		rows.add(screen.withTooltip(
				screen.sliderRowRecommended("Ready At", 0f, 100f, 1f, () -> s.readyPercent,
						v -> s.readyPercent = v, "%.0f%%", () -> s.readyPercent = d.readyPercent,
						SWORD_FULL_STRENGTH),
				"How charged the swing must be to count as ready.\n"
						+ "The sound fires here, and the colour turns high here.\n"
						+ "The tick marks 86%: the lowest a sword can be swung at\n"
						+ "and still land a full-strength, crit-capable hit."));
		rows.add(screen.withTooltip(
				screen.toggle("Full At Ready", () -> s.fullAtReady, v -> s.fullAtReady = v,
						() -> s.fullAtReady = d.fullAtReady),
				"The bar reaches full at the Ready At mark instead of at the\n"
						+ "end of the cooldown, so a full bar means swing now.\n"
						+ "Off shows the literal cooldown."));
		rows.add(screen.note(() -> "Held weapon hits at full strength from "
				+ weaponThresholdText(Minecraft.getInstance())));

		// Colour and sound apply whichever indicator is on screen — vanilla's art gets tinted.
		rows.add(screen.section("Colour"));
		rows.add(screen.toggle("Dynamic Colour", () -> s.dynamicColor, v -> s.dynamicColor = v,
				() -> s.dynamicColor = d.dynamicColor));
		if (s.dynamicColor) {
			rows.add(screen.cycleRow("Colour Mode", () -> s.colorMode.label(),
					() -> s.colorMode = cycle(s.colorMode, -1), () -> s.colorMode = cycle(s.colorMode, 1),
					() -> s.colorMode = d.colorMode));
			screen.addColorRows(rows, "Low Colour", () -> s.lowColor,
					() -> s.lowColor.copyFrom(d.lowColor));
			screen.addColorRows(rows, "High Colour", () -> s.highColor,
					() -> s.highColor.copyFrom(d.highColor));
		}

		rows.add(screen.section("Sound"));
		rows.add(screen.toggle("Play Sound", () -> s.playSound, v -> s.playSound = v,
				() -> s.playSound = d.playSound));
		if (s.playSound) {
			rows.add(screen.cycleRow("Sound", () -> s.sound.label(),
					() -> s.sound = cycle(s.sound, -1), () -> s.sound = cycle(s.sound, 1),
					() -> s.sound = d.sound));
			rows.add(screen.sliderRow("Volume", 0.1f, 2f, 0.05f, () -> s.soundVolume,
					v -> s.soundVolume = v, "%.2f", () -> s.soundVolume = d.soundVolume));
		}

		rows.add(screen.section("Show For"));
		rows.add(screen.dualToggle("Swords", () -> s.forSwords, v -> s.forSwords = v,
				"Axes", () -> s.forAxes, v -> s.forAxes = v));
		rows.add(screen.dualToggle("Tridents", () -> s.forTridents, v -> s.forTridents = v,
				"Pickaxes", () -> s.forPickaxes, v -> s.forPickaxes = v));
		rows.add(screen.dualToggle("Shovels", () -> s.forShovels, v -> s.forShovels = v,
				"Everything Else", () -> s.forOther, v -> s.forOther = v));
	}
}
