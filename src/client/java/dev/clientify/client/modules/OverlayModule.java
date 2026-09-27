package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.util.BlockTextures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;


/**
 * Tweaks to what the game paints over the world: the first-person overlays, the things stuck to
 * other players, and the armour on them.
 */
public class OverlayModule extends HudModule {
	public static class Settings extends ModuleSettings {
		/**
		 * Camera stops swaying, the held item keeps bobbing. Vanilla's own View Bobbing option is
		 * all or nothing: turning it off there flattens the hand animation too, which is the part
		 * worth keeping.
		 */
		public boolean minimalViewBobbing = false;

		/** How much of the burning overlay you see: 1 is vanilla's height, 0 is nothing at all. */
		public float fireOverlay = 1f;
		/** How much of a raised shield you see: 1 is vanilla, 0 drops it out of view entirely. */
		public float shieldHeight = 1f;
		public float offHandScale = 1f;

		/** Master switch for the rod: off leaves the bobber and its line as vanilla draws them. */
		public boolean customFishingRod = false;
		public float fishingHookOpacity = 1f;
		/** Scopes the hook opacity to the bobber another player has hooked into YOU. */
		public boolean onlyHooksOnYou = false;
		public boolean customFishingLine = false;
		public ColorSpec fishingLineColor = new ColorSpec("#FF000000");
		public float fishingLineThickness = 1f;

		public boolean customXpOrbColor = false;
		public ColorSpec xpOrbColor = new ColorSpec("#FF80FF20");

		/**
		 * Glass, faded rather than swapped for a clear texture: it sits on the translucent chunk
		 * layer in this version, so any alpha in between really is drawn.
		 */
		public boolean clearGlass = false;
		public float glassOpacity = 0f;
		/** Dyed glass fades by the same amount, which is one slider to think about, not two. */
		public boolean clearStainedGlass = false;
		/** Keeps the frame around each pane so the glass is still something you can see. */
		public boolean glassOutlines = false;
		public boolean customGlassOutline = false;
		public ColorSpec glassOutlineColor = new ColorSpec("#FFFFFFFF");
		public float glassOutlineThickness = 1f;
		public float glassOutlineOpacity = 1f;

		public boolean coloredString = false;
		public ColorSpec stringColor = new ColorSpec("#FFFF2E2E");
		/** A line one pixel wide is easy to miss; this grows it to two. */
		public boolean boldString = false;

		public boolean hideFoliage = false;
		public boolean hideGrass = true;
		public boolean hideFerns = true;
		public boolean hideVines = false;
		public boolean hideDeadBushes = false;

		/** How tall the fire block burns: 1 is vanilla, 0 leaves the flames invisible. */
		public float fireBlock = 1f;
		/**
		 * Drops the flames drawn on burning players, and on burning mobs, separately — a player on
		 * fire is otherwise mostly fire, which is a problem when you are trying to hit them, while a
		 * burning mob is usually just something you want to see coming.
		 *
		 * <p>Your own flames are left alone by both: in first person they are the burning overlay
		 * ({@link #fireOverlay}) rather than this, and in third person seeing yourself alight is how
		 * you know to stop standing in it.
		 */
		public boolean hideFirePlayers = false;
		public boolean hideFireMobs = false;

		/** Where the enchantment shimmer is drawn at all. */
		public String glint = Glint.ALL.name();

		public Glint glintMode() {
			try {
				return Glint.valueOf(glint);
			} catch (IllegalArgumentException e) {
				return Glint.ALL;
			}
		}

		/** Opacity of the helmet camera overlay — the pumpkin, and anything like it. */
		public float pumpkinOpacity = 1f;
		public float spyglassOpacity = 1f;
		/** The frost vignette while freezing. */
		public float frostOpacity = 1f;

		// Things on other people and on the ground.
		public boolean showGroundArrows = true;
		public boolean showStuckArrows = true;
		public boolean hidePlacedSkulls = false;

		// Armour, per piece. Only Hide For Self leaves everyone else's alone.
		public boolean hideHelmet = false;
		public boolean hideChestplate = false;
		public boolean hideLeggings = false;
		public boolean hideBoots = false;
		public boolean onlyHideForSelf = true;

		public Settings() {
			enabled = false;
		}
	}

	/** Where the enchantment shimmer survives. */
	public enum Glint {
		ALL("All"),
		INVENTORY("Inventory Only"),
		NONE("None");

		public final String label;

		Glint(String label) {
			this.label = label;
		}
	}

	private static OverlayModule instance;
	/** Which dropdown is open; a state of the screen, not of the settings. */
	private transient String expanded;

	private void expand(String key) {
		expanded = key.equals(expanded) ? null : key;
	}

	/**
	 * The second level of nesting — the glass outlines and the fishing line each open inside
	 * another card, and two dropdowns that nest cannot share one field for which is open.
	 */
	private transient String expandedSub;

	private void expandSub(String key) {
		expandedSub = key.equals(expandedSub) ? null : key;
	}

	public OverlayModule() {
		super("overlay", "Overlay");
		instance = this;
		// Plain glass is built into the chunk on the CUTOUT layer, where alpha is a yes or no
		// question answered at a half -- which is why a faded pane looked right in the hand and
		// was either solid or gone in the world. On the translucent layer the fade is drawn as
		// asked. Dyed and tinted glass are already there; these two are the exceptions.
		net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlocks(
				net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT,
				Blocks.GLASS, Blocks.GLASS_PANE);
		// Animated textures are painted as they load, which needs asking rather than telling:
		// they load long before a tick has said what is wanted.
		BlockTextures.provider(OverlayModule::wantedTextures);
	}

	@Override
	public String description() {
		return "Tweaks the overlays drawn over your view.";
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

	// ---- first person ----

	/** GameRendererMixin: true to leave the camera out of the walk bob. */
	public static boolean minimalViewBobbing() {
		Settings s = active();
		return s != null && s.minimalViewBobbing;
	}

	/** ScreenEffectRendererMixin: how much of the burning overlay to show, 1 being vanilla's. */
	public static float fireOverlay() {
		Settings s = active();
		return s == null ? 1f : s.fireOverlay;
	}

	/** EntityRendererMixin: true to drop the flames on other players (never the camera entity). */
	public static boolean hideFirePlayers() {
		Settings s = active();
		return s != null && s.hideFirePlayers;
	}

	/** EntityRendererMixin: true to drop the flames on burning mobs. */
	public static boolean hideFireMobs() {
		Settings s = active();
		return s != null && s.hideFireMobs;
	}

	/** ItemInHandRendererMixin: how big the off-hand item is drawn, 1 being vanilla. */
	public static float offHandScale() {
		Settings s = active();
		return s == null ? 1f : s.offHandScale;
	}

	/** ItemInHandRendererMixin: how much of a raised shield to leave in view, 1 being vanilla. */
	public static float shieldHeight() {
		Settings s = active();
		return s == null ? 1f : s.shieldHeight;
	}

	/**
	 * FishingHookRendererMixin: opacity for the bobber about to be drawn, given whether it is the
	 * one hooked into you.
	 *
	 * <p>That bobber is the whole point of the setting — somebody reels you in and it sits on your
	 * face for the rest of the fight. With Only Hooks On You set, every other bobber is left at
	 * vanilla, so a fishing rod in the distance still reads as a fishing rod.
	 */
	public static float hookOpacity(boolean hookedIntoYou) {
		Settings s = active();
		if (s == null || !s.customFishingRod) {
			return 1f;
		}
		if (s.onlyHooksOnYou && !hookedIntoYou) {
			return 1f;
		}
		return s.fishingHookOpacity;
	}

	/** FishingHookRendererMixin: line colour, or null to leave vanilla's black alone. */
	public static Integer fishingLineColor() {
		Settings s = active();
		return s == null || !s.customFishingRod || !s.customFishingLine ? null : s.fishingLineColor.chrome();
	}

	/** FishingHookRendererMixin: line width multiplier. */
	public static float fishingLineThickness() {
		Settings s = active();
		return s == null || !s.customFishingRod || !s.customFishingLine ? 1f
				: Math.max(0.1f, s.fishingLineThickness);
	}

	/** ExperienceOrbRendererMixin: orb colour, or null for the vanilla shimmer. */
	public static Integer xpOrbColor() {
		Settings s = active();
		return s == null || !s.customXpOrbColor ? null : s.xpOrbColor.chrome();
	}

	/**
	 * GuiOverlayMixin: opacity for a full-screen camera overlay, by which one it is. Both the
	 * helmet overlays and the freezing vignette come through vanilla's one method, told apart by
	 * the texture it was handed.
	 */
	public static float overlayOpacity(Identifier texture) {
		Settings s = active();
		if (s == null) {
			return 1f;
		}
		return texture.getPath().contains("powder_snow") ? s.frostOpacity : s.pumpkinOpacity;
	}

	/** GuiOverlayMixin: opacity for the spyglass scope and the black around it. */
	public static float spyglassOpacity() {
		Settings s = active();
		return s == null ? 1f : s.spyglassOpacity;
	}

	// ---- other people, and the ground ----

	/** ArrowRendererMixin: false to leave arrows that have landed undrawn. */
	public static boolean showGroundArrows() {
		Settings s = active();
		return s == null || s.showGroundArrows;
	}

	/** StuckInBodyLayerMixin: false to drop the arrows sticking out of people. */
	public static boolean showStuckArrows() {
		Settings s = active();
		return s == null || s.showStuckArrows;
	}

	/** SkullBlockRendererMixin: true to leave placed heads undrawn. */
	public static boolean hidePlacedSkulls() {
		Settings s = active();
		return s != null && s.hidePlacedSkulls;
	}

	/**
	 * HumanoidArmorLayerMixin: true to skip this piece. With Only Hide For Self on, everyone
	 * else's armour stays — which is the point of the option, since your own gear is the only
	 * gear you are choosing not to look at, and other people's tells you what you are up against.
	 *
	 * <p>Render states carry no entity, but a player's carries the entity id it came from, which
	 * is enough to recognise yourself without any plumbing of our own.
	 */
	public static boolean hideArmor(EquipmentSlot slot, Object renderState) {
		Settings s = active();
		if (s == null) {
			return false;
		}
		boolean hidden = switch (slot) {
			case HEAD -> s.hideHelmet;
			case CHEST -> s.hideChestplate;
			case LEGS -> s.hideLeggings;
			case FEET -> s.hideBoots;
			default -> false;
		};
		if (!hidden) {
			return false;
		}
		if (!s.onlyHideForSelf) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && renderState instanceof AvatarRenderState avatar
				&& avatar.id == mc.player.getId();
	}

	// ---- enchantment glint ----

	/**
	 * True when an item being drawn for this purpose should keep its shimmer.
	 *
	 * <p>The display context is the whole question: "inventory only" means the glint tells you
	 * what an item is while you are looking at it, and stops covering the item in your hand.
	 */
	public static boolean glintAllowed(ItemDisplayContext context) {
		Settings s = active();
		if (s == null) {
			return true;
		}
		return switch (s.glintMode()) {
			case ALL -> true;
			case NONE -> false;
			case INVENTORY -> context == ItemDisplayContext.GUI;
		};
	}

	// ---- block textures: the pixels are rewritten, nothing is drawn over the block ----

	/** Plain glass, its panes, and tinted glass — everything without a dye in it. */
	private static final List<Identifier> PLAIN_GLASS = List.of(
			BlockTextures.block("glass"),
			BlockTextures.block("glass_pane_top"));

	/**
	 * Every dyed glass texture, worked out once.
	 *
	 * <p>Tinted glass is in here rather than with the plain: it is dyed glass in all but name.
	 * These identifiers never change, and this list was being rebuilt on every tick.
	 */
	private static final List<Identifier> STAINED_GLASS = stainedGlass();

	private static List<Identifier> stainedGlass() {
		List<Identifier> out = new ArrayList<>();
		out.add(BlockTextures.block("tinted_glass"));
		for (DyeColor dye : DyeColor.values()) {
			out.add(BlockTextures.block(dye.getSerializedName() + "_stained_glass"));
			out.add(BlockTextures.block(dye.getSerializedName() + "_stained_glass_pane_top"));
		}
		return List.copyOf(out);
	}

	/**
	 * True for a block whose quads should be left out of the chunk mesh.
	 *
	 * <p>Hiding foliage used to clear the plant's texture, which also emptied its inventory icon —
	 * a block and its item are the same picture. Skipping the block while it is being built into a
	 * chunk keeps the item, the hitbox and everything else exactly as it was.
	 */
	/**
	 * Whether anything is hidden at all, kept as a plain field.
	 *
	 * <p>Every builder asks a block for its render shape while making a chunk, so this answer is
	 * on a path walked for every block of every rebuild. With the feature off, which is almost
	 * always, it should cost one field read rather than a module lookup and a settings
	 * dereference. Kept in step by the tick that already watches these settings.
	 */
	private static volatile boolean anyFoliageHidden;

	public static boolean hiddenInWorld(net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase state) {
		if (!anyFoliageHidden) {
			return false;
		}
		Settings s = active();
		if (s == null || !s.hideFoliage) {
			return false;
		}
		Block block = state.getBlock();
		if (s.hideGrass && (block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS)) {
			return true;
		}
		if (s.hideFerns && (block == Blocks.FERN || block == Blocks.LARGE_FERN)) {
			return true;
		}
		if (s.hideVines && block == Blocks.VINE) {
			return true;
		}
		return s.hideDeadBushes && block == Blocks.DEAD_BUSH;
	}

	/** What the chunks were last built for; a change means they have to be built again. */
	private String appliedFoliage = "";

	/**
	 * Rebuilds the world when what should be hidden changes.
	 *
	 * <p>Whether a block is in the mesh is decided when the chunk is built, not when it is drawn,
	 * so nothing happens to the grass already around you until the chunks are made again.
	 */
	private void syncFoliage(Minecraft mc, Settings s) {
		anyFoliageHidden = s != null && s.hideFoliage
				&& (s.hideGrass || s.hideFerns || s.hideVines || s.hideDeadBushes);
		StringBuilder state = new StringBuilder();
		if (s != null && s.hideFoliage) {
			state.append(s.hideGrass).append(s.hideFerns).append(s.hideVines).append(s.hideDeadBushes);
		}
		if (s != null && s.clearGlass) {
			state.append("g").append(Math.round(s.glassOpacity * 100)).append(s.clearStainedGlass);
		}
		String wanted = state.toString();
		if (wanted.equals(appliedFoliage)) {
			return;
		}
		// Nothing is applied until the chunks are built again, so leave it marked unapplied while
		// there is no world to rebuild — otherwise a setting changed at the menu is recorded as
		// done and the world you join is built to it only by luck.
		if (mc.levelRenderer == null || mc.level == null) {
			return;
		}
		appliedFoliage = wanted;
		mc.levelExtractor.allChanged();
	}

	/**
	 * Keeps the block atlas in step with the settings. Nothing here runs per frame: this describes
	 * what every texture should look like and hands it to {@link BlockTextures}, which compares it
	 * against what is already painted and touches only what differs — a colour change, a switch, or
	 * a resource reload that took the paint with it.
	 */
	@Override
	public void tick(Minecraft mc) {
		Settings s = active();
		// No world needed: this paints the block atlas, which exists from the first resource load.
		// Waiting for a level only meant the first chunks you saw were the unpainted ones.
		syncFoliage(mc, s);
		if (s == null) {
			BlockTextures.restoreAll(mc);
			return;
		}
		BlockTextures.sync(mc, wantedTextures());
	}

	/** Every texture this module wants painted, worked out fresh whenever it is asked for. */
	/**
	 * @param includeAtLoad whether the textures that can only be painted as they load count.
	 *
	 * <p>Glass and water are painted at load and nowhere else. Painting them live as well would
	 * take a snapshot of already painted pixels as if they were the originals, and turning the
	 * setting off would then restore to the painted state rather than to vanilla.
	 */
	public static Map<Identifier, BlockTextures.Op> wantedTextures() {
		Settings s = active();
		if (s == null) {
			return Map.of();
		}
		Map<Identifier, BlockTextures.Op> wanted = new LinkedHashMap<>();
		if (s.clearGlass) {
			addGlass(wanted, s, PLAIN_GLASS, s.glassOpacity);
		}
		if (s.clearStainedGlass && s.clearGlass) {
			// The dyed glass follows the same slider: two transparencies to keep in step was one
			// more decision than the setting is worth.
			addGlass(wanted, s, STAINED_GLASS, s.glassOpacity);
		}
		if (s.coloredString) {
			int colour = s.stringColor.chrome();
			wanted.put(BlockTextures.block("tripwire"), new BlockTextures.Op(
					"string:" + Integer.toHexString(colour) + ':' + s.boldString,
					BlockTextures.recolour(colour, s.boldString)));
		}
		if (s.fireBlock < 1f) {
			BlockTextures.Op op = new BlockTextures.Op("fire:" + Math.round(s.fireBlock * 100),
					BlockTextures.cropTop(s.fireBlock));
			wanted.put(BlockTextures.block("fire_0"), op);
			wanted.put(BlockTextures.block("fire_1"), op);
		}
		return wanted;
	}

	/** The next glint mode along, wrapping at either end. */
	private static String cycleGlint(Settings s, int step) {
		Glint[] all = Glint.values();
		int next = (s.glintMode().ordinal() + step + all.length) % all.length;
		return all[next].name();
	}

	private static void addGlass(Map<Identifier, BlockTextures.Op> wanted, Settings s,
			List<Identifier> textures, float opacity) {
		Integer edge = s.customGlassOutline ? s.glassOutlineColor.chrome() : null;
		int thickness = Math.max(1, Math.round(s.glassOutlineThickness));
		float edgeAlpha = s.glassOutlineOpacity;
		BlockTextures.Op op = new BlockTextures.Op(
				"glass:" + Math.round(opacity * 100) + ':' + s.glassOutlines + ':' + edge + ':' + thickness
						+ ':' + Math.round(edgeAlpha * 100),
				BlockTextures.glass(opacity, s.glassOutlines, edge, thickness, edgeAlpha));
		for (Identifier texture : textures) {
			wanted.put(texture, op);
		}
	}

	// ---- settings ----

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();

		rows.add(screen.section("First Person"));
		rows.add(screen.withTooltip(
				screen.toggle("Minimal View Bobbing", () -> s.minimalViewBobbing,
						v -> s.minimalViewBobbing = v, () -> s.minimalViewBobbing = d.minimalViewBobbing),
				"Keeps the hand bob and drops the camera sway.\n"
						+ "Vanilla's own option turns off both."));
		rows.add(screen.withTooltip(
				screen.sliderRow("Fire Height (Overlay)", 0f, 1f, 0.01f, () -> s.fireOverlay,
						v -> s.fireOverlay = v, "%.2f", () -> s.fireOverlay = d.fireOverlay),
				"How much of the burning overlay you see.\n1 is vanilla's height, 0 hides it."));
		rows.add(screen.withTooltip(
				screen.sliderRow("Fire Height (Block)", 0f, 1f, 0.01f, () -> s.fireBlock,
						v -> s.fireBlock = v, "%.2f", () -> s.fireBlock = d.fireBlock),
				"How tall fire burns in the world. 1 is vanilla,\n"
						+ "0 leaves the flames invisible but still burning."));
		rows.add(screen.withTooltip(
				screen.sliderRow("Shield Height", 0f, 1f, 0.01f, () -> s.shieldHeight,
						v -> s.shieldHeight = v, "%.2f", () -> s.shieldHeight = d.shieldHeight),
				"How much of a raised shield you see.\n1 is vanilla, 0 drops it out of view."));
		rows.add(screen.withTooltip(
				screen.sliderRow("Off-Hand Size", 0.1f, 2f, 0.01f, () -> s.offHandScale,
						v -> s.offHandScale = v, "%.2f", () -> s.offHandScale = d.offHandScale),
				"How big the off-hand item is drawn. 1 is vanilla,\n"
						+ "smaller gets a shield or a totem out of the corner\n"
						+ "you would rather be able to see through."));
		rows.add(screen.sliderRow("Pumpkin Overlay", 0f, 1f, 0.01f, () -> s.pumpkinOpacity,
				v -> s.pumpkinOpacity = v, "%.2f", () -> s.pumpkinOpacity = d.pumpkinOpacity));
		rows.add(screen.sliderRow("Spyglass Overlay", 0f, 1f, 0.01f, () -> s.spyglassOpacity,
				v -> s.spyglassOpacity = v, "%.2f", () -> s.spyglassOpacity = d.spyglassOpacity));
		rows.add(screen.sliderRow("Frost Overlay", 0f, 1f, 0.01f, () -> s.frostOpacity,
				v -> s.frostOpacity = v, "%.2f", () -> s.frostOpacity = d.frostOpacity));

		rows.add(screen.section("World"));
		rows.add(screen.dualToggle("Show Ground Arrows", () -> s.showGroundArrows,
				v -> s.showGroundArrows = v,
				"Show Stuck Arrows", () -> s.showStuckArrows, v -> s.showStuckArrows = v));
		rows.add(screen.withTooltip(
				screen.dualToggle("Hide Fire On Players", () -> s.hideFirePlayers,
						v -> s.hideFirePlayers = v,
						"Hide Fire On Mobs", () -> s.hideFireMobs, v -> s.hideFireMobs = v),
				"Drops the flames drawn on other burning players, who\n"
						+ "are otherwise mostly fire when you need to hit them.\n"
						+ "Your own are left alone either way.",
				"Drops the flames drawn on burning mobs. They still\n"
						+ "burn, and you still see the mob."));
		rows.add(screen.withTooltip(
				screen.dualToggleGear(
						"Hide Placed Skulls", () -> s.hidePlacedSkulls,
						v -> s.hidePlacedSkulls = v, null, () -> false,
						"Hide Foliage", () -> s.hideFoliage, v -> s.hideFoliage = v,
						() -> expand("foliage"), () -> "foliage".equals(expanded)),
				"Takes placed heads out of the world, so a wall of them\n"
						+ "stops hiding what is behind it.",
				"Leaves the plants out of the world, so they stop hiding\n"
						+ "what is behind them. They are still there to walk\n"
						+ "through, and their inventory icons are untouched."));
		if ("foliage".equals(expanded)) {
			screen.groupCard(rows, group -> {
				group.add(screen.dualToggle("Grass", () -> s.hideGrass, v -> s.hideGrass = v,
						"Ferns", () -> s.hideFerns, v -> s.hideFerns = v));
				group.add(screen.dualToggle("Vines", () -> s.hideVines, v -> s.hideVines = v,
						"Dead Bushes", () -> s.hideDeadBushes, v -> s.hideDeadBushes = v));
			});
		}

		rows.add(screen.section("Textures"));
		// Line one: the two that rewrite the world's own textures, each with its own card.
		rows.add(screen.withTooltip(
				screen.dualToggleGear(
						"Clear Glass", () -> s.clearGlass, v -> s.clearGlass = v,
						() -> expand("glass"), () -> "glass".equals(expanded),
						"Coloured String", () -> s.coloredString, v -> s.coloredString = v,
						() -> expand("string"), () -> "string".equals(expanded)),
				"Glass faded to any degree you like. It stacks on your\n"
						+ "own resource pack rather than replacing it.",
				"Tripwire repainted so a trap line reads at a glance,\n"
						+ "again over whatever pack you already run."));
		if ("glass".equals(expanded)) {
			screen.groupCard(rows, group -> {
				group.add(screen.withTooltip(
						screen.sliderRow("Transparency", 0f, 1f, 0.01f, () -> s.glassOpacity,
								v -> s.glassOpacity = v, "%.2f", () -> s.glassOpacity = d.glassOpacity),
						"1 is vanilla, 0 leaves the pane invisible.\n"
								+ "Panes keep blocking movement either way."));
				group.add(screen.withTooltip(
						screen.toggle("Clear Coloured Glass", () -> s.clearStainedGlass,
								v -> s.clearStainedGlass = v,
								() -> s.clearStainedGlass = d.clearStainedGlass),
						"Dyed glass and its panes fade by the same amount.\n"
								+ "They keep their colour, so at nought they are gone\n"
								+ "rather than clear."));
				// Glass is painted as its texture loads, like the water, so a new value shows when
				// the textures are loaded again and not before.
								group.add(screen.withTooltip(
						screen.toggleGear("Glass Outlines", () -> s.glassOutlines,
								v -> s.glassOutlines = v, () -> expandSub("glassedge"),
								() -> "glassedge".equals(expandedSub),
								() -> s.glassOutlines = d.glassOutlines),
						"Keeps the frame around each pane, so cleared glass\n"
								+ "is still something you can see."));
				if ("glassedge".equals(expandedSub)) {
					screen.groupCard(group, edge -> {
						edge.add(screen.sliderRow("Thickness", 1f, 4f, 1f,
								() -> s.glassOutlineThickness, v -> s.glassOutlineThickness = v, "%.0f",
								() -> s.glassOutlineThickness = d.glassOutlineThickness));
						edge.add(screen.sliderRow("Transparency", 0f, 1f, 0.01f,
								() -> s.glassOutlineOpacity, v -> s.glassOutlineOpacity = v, "%.2f",
								() -> s.glassOutlineOpacity = d.glassOutlineOpacity));
						edge.add(screen.withTooltip(
								screen.toggle("Custom Colour", () -> s.customGlassOutline,
										v -> s.customGlassOutline = v,
										() -> s.customGlassOutline = d.customGlassOutline),
								"Off keeps each pane's own frame, so stained glass\n"
										+ "still tells you which colour it is."));
						if (s.customGlassOutline) {
							screen.addColorRows(edge, "Colour", () -> s.glassOutlineColor,
									() -> s.glassOutlineColor.copyFrom(d.glassOutlineColor));
						}
					});
				}
			});
		}
		// Line two: everything about the rod in one place.
		rows.add(screen.withTooltip(
				screen.dualToggleGear(
						"Fishing Rod", () -> s.customFishingRod, v -> s.customFishingRod = v,
						() -> expand("rod"), () -> "rod".equals(expanded),
						"XP Orb Colour", () -> s.customXpOrbColor, v -> s.customXpOrbColor = v,
						() -> expand("xp"), () -> "xp".equals(expanded)),
				"The bobber and the line it hangs on. Off leaves both\n"
						+ "as vanilla draws them.",
				"What colour experience orbs are drawn in. Off leaves\n"
						+ "them the green vanilla uses."));
		if ("rod".equals(expanded)) {
			screen.groupCard(rows, group -> {
				group.add(screen.withTooltip(
						screen.sliderRow("Hook Transparency", 0f, 1f, 0.01f,
								() -> s.fishingHookOpacity, v -> s.fishingHookOpacity = v, "%.2f",
								() -> s.fishingHookOpacity = d.fishingHookOpacity),
						"1 is vanilla, 0 hides the bobber and its line."));
				group.add(screen.withTooltip(
						screen.dualToggleGear(
								"Only Hooks On You", () -> s.onlyHooksOnYou, v -> s.onlyHooksOnYou = v,
								null, () -> false,
								"Custom Line", () -> s.customFishingLine, v -> s.customFishingLine = v,
								() -> expandSub("line"), () -> "line".equals(expandedSub)),
						"Scopes the transparency to a bobber another player has\n"
								+ "hooked into you, which is the one covering your screen.",
						"Draws the line yourself instead of leaving it to\n"
								+ "vanilla, so its colour and width are yours to set."));
				if ("line".equals(expandedSub)) {
					screen.groupCard(group, line -> {
						line.add(screen.sliderRow("Thickness", 0.5f, 5f, 0.1f,
								() -> s.fishingLineThickness, v -> s.fishingLineThickness = v, "%.1f",
								() -> s.fishingLineThickness = d.fishingLineThickness));
						screen.addColorRows(line, "Colour", () -> s.fishingLineColor,
								() -> s.fishingLineColor.copyFrom(d.fishingLineColor));
					});
				}
			});
		}
		if ("xp".equals(expanded)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Colour", () -> s.xpOrbColor,
					() -> s.xpOrbColor.copyFrom(d.xpOrbColor)));
		}
		// Line three: the two smallest, each a colour or a number away from vanilla.
		if ("string".equals(expanded)) {
			screen.groupCard(rows, group -> {
				group.add(screen.withTooltip(
						screen.toggle("Bold", () -> s.boldString, v -> s.boldString = v,
								() -> s.boldString = d.boldString),
						"Two pixels wide instead of one, so the line reads\n"
								+ "from further away."));
				screen.addColorRows(group, "Colour", () -> s.stringColor,
						() -> s.stringColor.copyFrom(d.stringColor));
			});
		}

		rows.add(screen.withTooltip(
				screen.cycleRow("Enchantment Glint", () -> s.glintMode().label,
						() -> s.glint = cycleGlint(s, -1), () -> s.glint = cycleGlint(s, 1),
						() -> s.glint = d.glint),
				"Where the shimmer is drawn. Inventory Only keeps it\n"
						+ "where it tells you something and drops it from the\n"
						+ "item in your hand and from the world."));

		rows.add(screen.section("Armour"));
		rows.add(screen.dualToggle("Hide Helmet", () -> s.hideHelmet, v -> s.hideHelmet = v,
				"Hide Chestplate", () -> s.hideChestplate, v -> s.hideChestplate = v));
		rows.add(screen.dualToggle("Hide Leggings", () -> s.hideLeggings, v -> s.hideLeggings = v,
				"Hide Boots", () -> s.hideBoots, v -> s.hideBoots = v));
		rows.add(screen.withTooltip(
				screen.toggle("Only Hide For Self", () -> s.onlyHideForSelf,
						v -> s.onlyHideForSelf = v, () -> s.onlyHideForSelf = d.onlyHideForSelf),
				"Hides only your own armour. Off hides it on everyone,\n"
						+ "which also hides what your opponents are wearing."));
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
