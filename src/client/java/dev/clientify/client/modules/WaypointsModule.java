package dev.clientify.client.modules;

import com.mojang.blaze3d.platform.InputConstants;
import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.util.BlockOutline;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

/**
 * Waypoints, wwaypoints-style. Each waypoint projects to an on-screen chip (item/block
 * icon, name, live distance) clamped to the screen edge when off-screen, and highlights its
 * block(s) in the world with an outline. Tap the key to drop a waypoint where you stand; hold
 * it and walk to trace a multi-block waypoint. A separate, unbound key marks the block you are
 * looking at instead. Waypoints are stored per server / per world, and filtered by dimension.
 */
public class WaypointsModule extends HudModule {
	private static final int PAD_X = 4;
	private static final int PAD_Y = 3;
	private static final int EDGE = 6;

	/** What a waypoint's chip shows. */
	public enum Display {
		BOTH, ICON, NAME;

		public String label() {
			return switch (this) {
				case BOTH -> "Icon + Name";
				case ICON -> "Icon Only";
				case NAME -> "Name Only";
			};
		}
	}

	/** A waypoint block position (GSON-friendly). */
	public static class Block {
		public int x;
		public int y;
		public int z;

		public Block() {
		}

		public Block(int x, int y, int z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	public static class Waypoint {
		public String name = "Waypoint";
		/** First entry is the anchor; more blocks make it a multi-block waypoint. */
		public List<Block> blocks = new ArrayList<>();
		public String dimension = "minecraft:overworld";
		public ColorSpec color = new ColorSpec("#F35D12");
		public ColorSpec nameColor = new ColorSpec("#FFFFFF");
		/** Item or block id for the chip icon; blank = a plain colored square. */
		public String icon = "";
		public boolean visible = true;
		/** Block Display: the cuboid drawn on the waypoint's blocks. */
		public boolean highlight = true;
		public boolean renderThroughWalls = false;
		public float fillOpacity = 0.1f;
		public float outlineOpacity = 1f;
		/** Label: the on-screen chip. */
		public boolean labelEnabled = true;
		/**
		 * Keep the chip on screen (clamped to the edge) when the spot is out of view. Off for new
		 * waypoints: a pinned chip sits on the edge pointing at nothing, and with a few marks in a
		 * world the edges fill up with them. Waypoints saved before this keep whatever they had.
		 */
		public boolean clampToScreen = false;
		public Display display = Display.BOTH;
		// Per-waypoint display options (these used to be module-wide).
		public boolean showDistance = true;
		public boolean scaleWithZoom = true;
		public float scale = 1f;

		public Block anchor() {
			return blocks.isEmpty() ? new Block() : blocks.get(0);
		}
	}

	/**
	 * The waypoints themselves, saved at config root rather than inside a profile — they
	 * describe places in a world, so every profile shares them.
	 */
	public static class Store {
		/** Waypoints per world key (server address or singleplayer level name). */
		public Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
		/**
		 * Servers that run several worlds (lobby, skyblock, …) reuse one address, so this
		 * splits their waypoints by dimension instead of pooling them.
		 */
		public boolean perWorldOnServers = true;
		/** User-renamed world labels, keyed by world key. */
		public Map<String, String> worldLabels = new LinkedHashMap<>();
	}

	public static class Settings extends ModuleSettings {
		/** Drop a mark where you died, so the walk back is not from memory. */
		public boolean deathWaypoints = true;
		/** How many death marks to keep; the oldest goes when you die again. */
		public int maxDeathWaypoints = 3;

		public Settings() {
			enabled = false;
			// Chips read better against the world without a plate behind them.
			background = false;
		}
	}

	/** Death marks get their own colour, which is also how they are recognised for trimming. */
	private static final String DEATH_COLOR = "#FF4A4A";

	private static WaypointsModule instance;
	/** Default key: tap = the block underfoot, hold = trace the blocks you walk over. */
	private final KeyMapping key;
	/** Optional key that only marks the looked-at block. */
	private final KeyMapping lookKey;
	/** Opens the waypoint menu directly (M). */
	private final KeyMapping menuKey;
	private transient Waypoint tracing;
	/** Which waypoint the settings screen is editing (its list drives this). */
	private transient Waypoint selectedWp;

	/** Camera state captured at the start of the world render (exact projection). */
	private static Vec3 camPos = Vec3.ZERO;
	private static final Matrix4f WORLD_TO_CLIP = new Matrix4f();
	private static boolean frameReady;

	public WaypointsModule() {
		super("waypoints", "Waypoints");
		instance = this;
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.clientify.waypoint", InputConstants.KEY_N, ClientifyClient.KEY_CATEGORY));
		lookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.clientify.waypoint_look", GLFW.GLFW_KEY_UNKNOWN, ClientifyClient.KEY_CATEGORY));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.clientify.waypoint_menu", InputConstants.KEY_M, ClientifyClient.KEY_CATEGORY));
	}

	/** Magnification of the current view versus the player's own FOV (1 = not zoomed). */
	private static float fovZoom = 1f;

	/**
	 * Called by LevelRendererMixin each frame with the real render matrices.
	 *
	 * <p>The two are multiplied here rather than by the caller: the answer is copied into a matrix
	 * that already exists, so combining them on the way in only built one to throw away again,
	 * every frame.
	 */
	public static void captureFrame(Vec3 cameraPos, Matrix4f projection, Matrix4f cameraRotation) {
		camPos = cameraPos;
		WORLD_TO_CLIP.set(projection).mul(cameraRotation);
		frameReady = true;
	}

	/**
	 * Called from the FOV hook with the world FOV actually being rendered. Taking it here
	 * means every zoom mod that adjusts the FOV — ours, Zoomify, a spyglass — scales labels,
	 * and it avoids reading a matrix that view bobbing has already perturbed.
	 */
	public static void captureFov(float fov) {
		Minecraft mc = Minecraft.getInstance();
		double baseFov = mc.options == null ? 70.0 : mc.options.fov().get();
		double baseTan = Math.tan(Math.toRadians(baseFov) / 2.0);
		double tan = Math.tan(Math.toRadians(Math.max(1.0, fov)) / 2.0);
		fovZoom = tan <= 0.0001 ? 1f : (float) Math.max(1.0, baseTan / tan);
	}

	/** Label magnification from the current field of view. */
	public static float fovZoom() {
		return fovZoom;
	}

	@Override
	public String description() {
		return "Marks saved spots with on-screen labels and block outlines.";
	}

	@Override
	public String category() {
		return "MECHANIC";
	}

	@Override
	public boolean hasColorSection() {
		return false;
	}

	/** Chips follow the world, so there is nothing to drag. */
	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		return List.of();
	}

	@Override
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	// ---- per-world storage ----

	/** Identity of the current server or singleplayer world. */
	public static String worldKey(Minecraft mc) {
		var server = mc.getCurrentServer();
		if (server != null) {
			// Optionally split a multi-world server by dimension.
			return dev.clientify.client.config.ClientifyConfig.waypoints().perWorldOnServers
					? "server:" + server.ip + "|" + currentDimension(mc)
					: "server:" + server.ip;
		}
		if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
			return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		return "unknown";
	}

	/** The key prefix shared by every saved world of the server (or singleplayer world). */
	public static String serverPrefix(Minecraft mc) {
		var server = mc.getCurrentServer();
		if (server != null) {
			return "server:" + server.ip;
		}
		if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
			return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		return "unknown";
	}

	/** Saved worlds for the current server — only ones actually visited appear. */
	public static List<String> savedWorlds(Minecraft mc) {
		String prefix = serverPrefix(mc);
		List<String> out = new ArrayList<>();
		for (String key : dev.clientify.client.config.ClientifyConfig.waypoints().worlds.keySet()) {
			if (key.equals(prefix) || key.startsWith(prefix + "|")) {
				out.add(key);
			}
		}
		out.sort(String::compareToIgnoreCase);
		return out;
	}

	/** Registers the world we just loaded so it appears in the list (no synthetic entries). */
	private static void registerWorld(Minecraft mc) {
		dev.clientify.client.config.ClientifyConfig.waypoints()
				.worlds.computeIfAbsent(worldKey(mc), k -> new ArrayList<>());
	}

	/** A world's label: the user's rename, else its dimension, else the world/server name. */
	public static String worldDisplayName(String key) {
		String custom = dev.clientify.client.config.ClientifyConfig.waypoints().worldLabels.get(key);
		if (custom != null && !custom.isBlank()) {
			return custom;
		}
		int bar = key.indexOf('|');
		if (bar >= 0) {
			return prettyDim(shortDim(key.substring(bar + 1)));
		}
		int colon = key.indexOf(':');
		return colon < 0 ? key : key.substring(colon + 1);
	}

	public static void renameWorld(String key, String label) {
		var labels = dev.clientify.client.config.ClientifyConfig.waypoints().worldLabels;
		if (label == null || label.isBlank()) {
			labels.remove(key);
		} else {
			labels.put(key, label.trim());
		}
	}

	/** Forgets a saved world and every waypoint in it. */
	public static void deleteWorld(String key) {
		var store = dev.clientify.client.config.ClientifyConfig.waypoints();
		store.worlds.remove(key);
		store.worldLabels.remove(key);
	}

	/**
	 * Whether {@code worldKey} already holds a waypoint at the same block in the same dimension.
	 *
	 * <p>Matched on position rather than name: the same spot saved twice under two names is still
	 * one place, and a shared waypoint passed around a group would otherwise stack up a copy per
	 * person who sent it.
	 */
	public static boolean alreadyHas(String worldKey, Waypoint candidate) {
		if (candidate == null || candidate.blocks.isEmpty()) {
			return false;
		}
		Block a = candidate.anchor();
		for (Waypoint wp : waypointsIn(worldKey)) {
			if (wp.blocks.isEmpty()) {
				continue;
			}
			Block b = wp.anchor();
			if (b.x == a.x && b.y == a.y && b.z == a.z
					&& java.util.Objects.equals(wp.dimension, candidate.dimension)) {
				return true;
			}
		}
		return false;
	}

	/** A readable name for a dimension id, for anywhere outside this class. */
	public static String dimensionLabel(String dimension) {
		return prettyDim(shortDim(dimension));
	}

	private static String prettyDim(String dim) {
		return switch (dim) {
			case "overworld" -> "Overworld";
			case "the_nether" -> "Nether";
			case "the_end" -> "End";
			default -> dim;
		};
	}

	/** The block that stands for a world in the list: grass, netherrack or end stone. */
	public static ItemStack worldIcon(String key) {
		if (key.contains("the_nether")) {
			return new ItemStack(Items.NETHERRACK);
		}
		if (key.contains("the_end")) {
			return new ItemStack(Items.END_STONE);
		}
		return new ItemStack(Items.GRASS_BLOCK);
	}

	/** Waypoints stored under a specific world key. */
	public static List<Waypoint> waypointsIn(String key) {
		return dev.clientify.client.config.ClientifyConfig.waypoints()
				.worlds.computeIfAbsent(key, k -> new ArrayList<>());
	}

	private static String currentDimension(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	private static String shortDim(String dimension) {
		int at = dimension.indexOf(':');
		return at < 0 ? dimension : dimension.substring(at + 1);
	}

	/** The waypoint list for the world we are currently in (shared by all profiles). */
	private List<Waypoint> current(Minecraft mc) {
		return dev.clientify.client.config.ClientifyConfig.waypoints()
				.worlds.computeIfAbsent(worldKey(mc), k -> new ArrayList<>());
	}

	/** Distinct, readable marker colors handed out to new waypoints in turn. */
	private static final String[] PALETTE = {
			"#F35D12", "#4ADE6A", "#3CC8FF", "#FFE24A", "#FF5CC8",
			"#6A5CFF", "#FF4A4A", "#4ADEC8", "#FF9A3C", "#B47AFF"};

	/** A colour for a fresh waypoint — random, so new marks are told apart at a glance. */
	private static String randomColor() {
		return PALETTE[(int) (Math.random() * PALETTE.length)];
	}

	// ---- keybind: tap = looked-at block, hold + walk = multi-block trace ----

	@Override
	public void tick(Minecraft mc) {
		if (!isEnabled() || mc.player == null || mc.level == null) {
			tracing = null;
			return;
		}
		registerWorld(mc);
		markDeath(mc);
		// M jumps straight to the waypoint menu.
		while (menuKey.consumeClick()) {
			mc.setScreen(settingsScreen(new dev.clientify.client.gui.ModListScreen(
					new dev.clientify.client.gui.HudEditorScreen())));
		}
		// The looked-at block gets its own key, unbound by default.
		while (lookKey.consumeClick()) {
			beginWaypoint(mc, true);
			ModuleManager.save();
		}

		boolean down = key.isDown();
		while (key.consumeClick()) {
			// Ignored — the held state below drives creation.
		}
		if (down && tracing == null) {
			// Where you stand, not what you are aiming at: tapping and holding are then the same
			// gesture at two lengths, rather than the tap marking somewhere the hold never goes.
			tracing = beginWaypoint(mc, false);
		} else if (down && tracing != null) {
			// Holding: trace the blocks the player walks over, but never a block already in the
			// selection — re-walking one would stack a second fill and darken it.
			Block standing = new Block(mc.player.getBlockX(), mc.player.getBlockY() - 1, mc.player.getBlockZ());
			boolean already = false;
			for (Block b : tracing.blocks) {
				if (b.x == standing.x && b.y == standing.y && b.z == standing.z) {
					already = true;
					break;
				}
			}
			if (!already) {
				tracing.blocks.add(standing);
			}
		} else if (!down && tracing != null) {
			tracing = null;
			ModuleManager.save();
		}
	}

	// ---- death waypoints ----

	/** Marks where you died, once per death. Reset when you are alive again. */
	private boolean deathMarked;

	private void markDeath(Minecraft mc) {
		Settings s = (Settings) settings();
		if (mc.player.isAlive()) {
			deathMarked = false;
			return;
		}
		if (deathMarked || !s.deathWaypoints) {
			deathMarked = true; // don't mark again on this death, even if turned on mid-death
			return;
		}
		deathMarked = true;
		List<Waypoint> list = current(mc);
		Waypoint wp = new Waypoint();
		wp.name = "Death " + java.time.LocalTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
				.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
		wp.color = new ColorSpec(DEATH_COLOR);
		wp.icon = "skeleton_skull";
		wp.dimension = currentDimension(mc);
		wp.blocks.add(new Block(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()));
		list.add(wp);
		trimDeaths(list, Math.max(1, s.maxDeathWaypoints));
		ModuleManager.save();
	}

	/** Keeps only the newest {@code keep} death marks; older ones are dropped as you die again. */
	private static void trimDeaths(List<Waypoint> list, int keep) {
		int found = 0;
		for (int i = list.size() - 1; i >= 0; i--) {
			Waypoint wp = list.get(i);
			if (!isDeathWaypoint(wp)) {
				continue;
			}
			found++;
			if (found > keep) {
				list.remove(i);
			}
		}
	}

	/** A death mark is one this module made: our name and our colour, both still untouched. */
	private static boolean isDeathWaypoint(Waypoint wp) {
		return wp.name.startsWith("Death ") && DEATH_COLOR.equalsIgnoreCase(wp.color.a);
	}

	/**
	 * Creates a waypoint. {@code preferLookedAt} uses the block in your crosshair (falling
	 * back to the one underfoot); otherwise it always marks the block you are standing on.
	 */
	private Waypoint beginWaypoint(Minecraft mc, boolean preferLookedAt) {
		Block at;
		if (preferLookedAt && mc.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK) {
			var p = hit.getBlockPos();
			at = new Block(p.getX(), p.getY(), p.getZ());
		} else {
			at = new Block(mc.player.getBlockX(), mc.player.getBlockY() - 1, mc.player.getBlockZ());
		}
		List<Waypoint> list = current(mc);
		Waypoint wp = new Waypoint();
		wp.name = "Waypoint " + (list.size() + 1);
		wp.color = new ColorSpec(randomColor());
		wp.dimension = currentDimension(mc);
		wp.blocks.add(at);
		list.add(wp);
		return wp;
	}

	// ---- settings ----

	// ---- selection (driven by the settings screen's sidebar) ----

	@Override
	public net.minecraft.client.gui.screens.Screen settingsScreen(dev.clientify.client.gui.ModListScreen list) {
		return new dev.clientify.client.gui.WaypointSettingsScreen(list, this);
	}

	/** The waypoint list for the world we are in (the settings sidebar shows this). */
	public List<Waypoint> currentList(Minecraft mc) {
		return current(mc);
	}

	/** The waypoint being edited, tracked by identity so it survives list changes. */
	public void setSelected(Waypoint wp) {
		selectedWp = wp;
	}

	/** True when {@code wp} is the one being edited. */
	public boolean isSelected(Waypoint wp) {
		return selectedWp == wp;
	}

	/** Creates a waypoint at the player's feet (the sidebar's + New Waypoint). */
	public void addAtPlayer() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			beginWaypoint(mc, false);
			tracing = null;
		}
	}

	/** The waypoint currently being edited, or null when it is gone. */
	private Waypoint selectedWaypoint() {
		return selectedWp;
	}

	/** Name, icon and each coordinate as editable text boxes for the selected waypoint. */
	@Override
	public List<TextField> textFields() {
		Waypoint wp = selectedWaypoint();
		if (wp == null) {
			return List.of();
		}
		List<TextField> fields = new ArrayList<>(5);
		fields.add(new TextField("Name", () -> wp.name, v -> wp.name = v, "Waypoint"));
		fields.add(new TextField("Icon", () -> wp.icon, v -> wp.icon = v, ""));
		// Editing a coordinate moves the WHOLE waypoint: the anchor lands where you typed and
		// every traced block keeps its offset, so the shape travels intact.
		fields.add(new TextField("X", () -> String.valueOf(wp.anchor().x),
				v -> moveTo(wp, 0, parseCoord(v, wp.anchor().x)), "0"));
		fields.add(new TextField("Y", () -> String.valueOf(wp.anchor().y),
				v -> moveTo(wp, 1, parseCoord(v, wp.anchor().y)), "0"));
		fields.add(new TextField("Z", () -> String.valueOf(wp.anchor().z),
				v -> moveTo(wp, 2, parseCoord(v, wp.anchor().z)), "0"));
		return fields;
	}

	/** Shifts every block so the anchor's {@code axis} coordinate becomes {@code value}. */
	private static void moveTo(Waypoint wp, int axis, int value) {
		if (wp.blocks.isEmpty()) {
			return;
		}
		Block anchor = wp.anchor();
		int delta = value - switch (axis) {
			case 0 -> anchor.x;
			case 1 -> anchor.y;
			default -> anchor.z;
		};
		if (delta == 0) {
			return;
		}
		for (Block b : wp.blocks) {
			switch (axis) {
				case 0 -> b.x += delta;
				case 1 -> b.y += delta;
				default -> b.z += delta;
			}
		}
	}

	/** Keeps the old value while the box holds a partial entry like "-" or "". */
	private static int parseCoord(String raw, int fallback) {
		try {
			return Integer.parseInt(raw.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Keybind rows for the General dropdown (the screen supplies the rest of that card). */
	public void appendKeybindRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		var store = dev.clientify.client.config.ClientifyConfig.waypoints();
		Settings s = (Settings) settings();
		rows.add(screen.toggle("Separate Per World On Servers", () -> store.perWorldOnServers,
				v -> store.perWorldOnServers = v, () -> store.perWorldOnServers = false));
		rows.add(screen.toggle("Mark Where You Died", () -> s.deathWaypoints,
				v -> s.deathWaypoints = v, () -> s.deathWaypoints = true));
		if (s.deathWaypoints) {
			rows.add(screen.sliderRow("Death Marks Kept", 1f, 10f, 1f, () -> (float) s.maxDeathWaypoints,
					v -> s.maxDeathWaypoints = Math.round(v), "%.0f", () -> s.maxDeathWaypoints = 3));
		}
		rows.add(screen.keybindRow("Waypoint Key", key, () -> {
			key.setKey(key.getDefaultKey());
			KeyMapping.resetMapping();
		}));
		rows.add(screen.keybindRow("Open Waypoint Menu", menuKey, () -> {
			menuKey.setKey(menuKey.getDefaultKey());
			KeyMapping.resetMapping();
		}));
		rows.add(screen.keybindRow("Mark Looked-At Block", lookKey, () -> {
			lookKey.setKey(lookKey.getDefaultKey());
			KeyMapping.resetMapping();
		}));
	}

	/** The Label card: how the on-screen chip looks. */
	public void appendLabelRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Waypoint wp) {
		rows.add(screen.cycleRow("Shows", () -> wp.display.label(),
				() -> wp.display = cycle(wp.display, -1), () -> wp.display = cycle(wp.display, 1),
				() -> wp.display = Display.BOTH));
		screen.addDualColorRows(rows, "Marker", () -> wp.color,
				() -> wp.color.copyFrom(new ColorSpec(randomColor())),
				"Name", () -> wp.nameColor, () -> wp.nameColor.copyFrom(new ColorSpec("#FFFFFF")));
		rows.add(screen.sliderRow("Scale", 0.5f, 3f, 0.05f, () -> wp.scale, v -> wp.scale = v,
				"%.2f", () -> wp.scale = 1f));
		rows.add(screen.dualToggle("Show Distance", () -> wp.showDistance, v -> wp.showDistance = v,
				"Stick To Edge", () -> wp.clampToScreen, v -> wp.clampToScreen = v));
		rows.add(screen.toggle("Scale With Zoom", () -> wp.scaleWithZoom, v -> wp.scaleWithZoom = v,
				() -> wp.scaleWithZoom = true));
	}

	/** The Block Display card: the cuboid drawn on the waypoint's blocks. */
	public void appendBlockRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Waypoint wp) {
		rows.add(screen.toggle("Render Through Walls", () -> wp.renderThroughWalls,
				v -> wp.renderThroughWalls = v, () -> wp.renderThroughWalls = false));
		rows.add(screen.sliderRow("Fill Opacity", 0f, 100f, 5f, () -> wp.fillOpacity * 100f,
				v -> wp.fillOpacity = v / 100f, "%.0f%%", () -> wp.fillOpacity = 0.1f));
		rows.add(screen.sliderRow("Outline Opacity", 0f, 100f, 5f, () -> wp.outlineOpacity * 100f,
				v -> wp.outlineOpacity = v / 100f, "%.0f%%", () -> wp.outlineOpacity = 1f));
	}

	/** Placement rows shared by the editor footer. */
	public void appendPlacementRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Waypoint wp) {
		Minecraft mc = Minecraft.getInstance();
		rows.add(screen.infoRow("Blocks", () -> wp.blocks.size() + " (" + shortDim(wp.dimension) + ")"));
		rows.add(screen.button("Move To Me", () -> {
			if (mc.player != null) {
				wp.blocks.clear();
				wp.blocks.add(new Block(mc.player.getBlockX(), mc.player.getBlockY() - 1,
						mc.player.getBlockZ()));
				wp.dimension = currentDimension(mc);
				screen.refreshTextFields();
			}
		}));
	}

	// ---- icon parsing ----

	/**
	 * Memoized {@link #resolveIcon} results. Icon names only change when the user edits one,
	 * but this is called for every visible waypoint every frame — parsing and hitting the
	 * registry that often is pure waste. The cached stacks are for RENDERING ONLY; never
	 * mutate one.
	 */
	private static final Map<String, ItemStack> ICON_CACHE = new java.util.HashMap<>();

	/**
	 * Resolves a typed icon name to a stack: an item/block id ("diamond", "oak_log") or a
	 * potion phrase ("splash potion of healing 2" → splash potion with strong_healing).
	 */
	public static ItemStack iconStack(String raw) {
		if (raw == null || raw.isBlank()) {
			return ItemStack.EMPTY;
		}
		ItemStack cached = ICON_CACHE.get(raw);
		if (cached != null) {
			return cached;
		}
		ItemStack resolved = resolveIcon(raw);
		// Bound the cache: only user-typed names land here, but a pathological config shouldn't
		// grow it without limit.
		if (ICON_CACHE.size() > 512) {
			ICON_CACHE.clear();
		}
		ICON_CACHE.put(raw, resolved);
		return resolved;
	}

	private static ItemStack resolveIcon(String raw) {
		String name = raw.trim().toLowerCase(java.util.Locale.ROOT);
		ItemStack potion = parsePotion(name);
		if (!potion.isEmpty()) {
			return potion;
		}
		String id = name.replace(' ', '_');
		Identifier key = id.contains(":") ? Identifier.tryParse(id) : Identifier.tryParse("minecraft:" + id);
		if (key == null) {
			return ItemStack.EMPTY;
		}
		var item = BuiltInRegistries.ITEM.getOptional(key);
		return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	/** One icon search hit: the phrase to store plus the stack to preview. */
	public record IconSuggestion(String value, String label, ItemStack stack) {
	}

	/**
	 * Icon suggestions for a typed query. Matches item display names, and expands potions into
	 * their three containers and strengths so "healing" lists every healing potion variant.
	 */
	public static List<IconSuggestion> suggestIcons(String query, int limit) {
		String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
		List<IconSuggestion> out = new ArrayList<>();
		if (q.isEmpty()) {
			return out;
		}
		for (var item : BuiltInRegistries.ITEM) {
			if (out.size() >= limit) {
				return out;
			}
			ItemStack stack = new ItemStack(item);
			String label = stack.getHoverName().getString();
			if (!label.toLowerCase(java.util.Locale.ROOT).contains(q)) {
				continue;
			}
			// Potions get expanded below with their effect names; skip the bare item here.
			if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(item).getPath();
			out.add(new IconSuggestion(id, label, stack));
		}
		var containers = new java.util.LinkedHashMap<String, net.minecraft.world.item.Item>();
		containers.put("potion", Items.POTION);
		containers.put("splash potion", Items.SPLASH_POTION);
		containers.put("lingering potion", Items.LINGERING_POTION);
		for (var potion : BuiltInRegistries.POTION) {
			String potionId = BuiltInRegistries.POTION.getKey(potion).getPath();
			if (potionId.equals("empty")) {
				continue;
			}
			// "strong_strength" is Strength II and "long_strength" is the extended one, but neither
			// says so when spelled out — so the label carries the level the way the game writes it,
			// while the stored value stays in the phrasing parsePotion understands.
			boolean strong = potionId.startsWith("strong_");
			boolean extended = potionId.startsWith("long_");
			String base = strong ? potionId.substring(7) : (extended ? potionId.substring(5) : potionId);
			String effectName = potionEffectName(potion, base);
			int level = potionLevel(potion);
			for (var entry : containers.entrySet()) {
				if (out.size() >= limit) {
					return out;
				}
				String value = entry.getKey() + " of " + base.replace('_', ' ')
						+ (strong ? " 2" : extended ? " +" : "");
				String label = titleCase(entry.getKey()) + " of " + effectName
						+ (level > 1 ? " " + roman(level) : "") + (extended ? " (Extended)" : "");
				if (!value.contains(q) && !label.toLowerCase(java.util.Locale.ROOT).contains(q)) {
					continue;
				}
				ItemStack stack = new ItemStack(entry.getValue());
				stack.set(DataComponents.POTION_CONTENTS,
						new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(potion)));
				out.add(new IconSuggestion(value, label, stack));
			}
		}
		return out;
	}

	/** The effect a potion grants, named as the game names it; the id when it grants none. */
	private static String potionEffectName(net.minecraft.world.item.alchemy.Potion potion, String fallbackId) {
		var effects = potion.getEffects();
		if (effects.isEmpty()) {
			return titleCase(fallbackId.replace('_', ' '));
		}
		return effects.get(0).getEffect().value().getDisplayName().getString();
	}

	/** Potion strength as a level, so Strength II reads as II rather than "strong strength". */
	private static int potionLevel(net.minecraft.world.item.alchemy.Potion potion) {
		var effects = potion.getEffects();
		return effects.isEmpty() ? 1 : effects.get(0).getAmplifier() + 1;
	}

	private static String roman(int value) {
		return switch (value) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> Integer.toString(value);
		};
	}

	private static String titleCase(String words) {
		StringBuilder out = new StringBuilder(words.length());
		boolean start = true;
		for (char c : words.toCharArray()) {
			out.append(start ? Character.toUpperCase(c) : c);
			start = c == ' ';
		}
		return out.toString();
	}

	/** "[splash|lingering] potion of X [2|ii]" → the matching potion stack. */
	private static ItemStack parsePotion(String name) {
		if (!name.contains("potion of ")) {
			return ItemStack.EMPTY;
		}
		var item = Items.POTION;
		if (name.startsWith("splash")) {
			item = Items.SPLASH_POTION;
		} else if (name.startsWith("lingering")) {
			item = Items.LINGERING_POTION;
		}
		String effect = name.substring(name.indexOf("potion of ") + "potion of ".length()).trim();
		boolean strong = effect.endsWith(" 2") || effect.endsWith(" ii");
		boolean longer = effect.endsWith(" +") || effect.contains("extended");
		final String base = effect.replace(" 2", "").replace(" ii", "").replace(" +", "")
				.replace("extended", "").trim().replace(' ', '_');
		String potionId = strong ? "strong_" + base : (longer ? "long_" + base : base);
		Identifier key = Identifier.tryParse("minecraft:" + potionId);
		if (key == null) {
			return ItemStack.EMPTY;
		}
		var potion = BuiltInRegistries.POTION.getOptional(key)
				.or(() -> BuiltInRegistries.POTION.getOptional(Identifier.withDefaultNamespace(base)));
		if (potion.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.POTION_CONTENTS,
				new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(potion.get())));
		return stack;
	}

	// ---- world outlines (gizmo pass) + on-screen chips (HUD pass) ----

	/** Called by DebugRendererMixin where the gizmo collector is armed. */
	public static void emitGizmos() {
		WaypointsModule m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.isEnabled() || m.settings() == null || mc.level == null) {
			return;
		}
		String dimension = currentDimension(mc);
		for (Waypoint wp : m.current(mc)) {
			if (!wp.visible || !wp.highlight || !dimension.equals(wp.dimension)) {
				continue;
			}
			int base = wp.color.chrome();
			int stroke = dev.clientify.client.util.Colors.withAlpha(base, wp.outlineOpacity);
			int fill = dev.clientify.client.util.Colors.withAlpha(base, wp.fillOpacity);
			boolean hasFill = wp.fillOpacity > 0.001f;
			if (wp.blocks.size() == 1) {
				Block b = wp.blocks.get(0);
				GizmoStyle style = hasFill
						? GizmoStyle.strokeAndFill(stroke, 2.5f, fill)
						: GizmoStyle.stroke(stroke, 2.5f);
				var gizmo = Gizmos.cuboid(new AABB(b.x, b.y, b.z, b.x + 1.0, b.y + 1.0, b.z + 1.0), style);
				if (wp.renderThroughWalls) {
					gizmo.setAlwaysOnTop();
				}
			} else {
				emitMergedOutline(wp, stroke, fill, hasFill);
			}
		}
	}

	/**
	 * A multi-block waypoint drawn as ONE shape: every block is filled, but only the edges on
	 * the outside of the combined shape are stroked, so there are no seams between neighbours.
	 * The geometry itself lives in {@link BlockOutline}.
	 */
	private static void emitMergedOutline(Waypoint wp, int stroke, int fill, boolean hasFill) {
		java.util.Set<Long> cells = new java.util.HashSet<>();
		for (Block b : wp.blocks) {
			cells.add(BlockOutline.cell(b.x, b.y, b.z));
		}
		if (hasFill) {
			GizmoStyle fillOnly = GizmoStyle.fill(fill);
			for (Block b : wp.blocks) {
				var g = Gizmos.cuboid(new AABB(b.x, b.y, b.z, b.x + 1.0, b.y + 1.0, b.z + 1.0), fillOnly);
				if (wp.renderThroughWalls) {
					g.setAlwaysOnTop();
				}
			}
		}
		for (BlockOutline.Edge e : BlockOutline.silhouette(cells)) {
			Vec3 from = new Vec3(e.x(), e.y(), e.z());
			Vec3 to = new Vec3(e.x() + (e.axis() == 0 ? 1 : 0), e.y() + (e.axis() == 1 ? 1 : 0),
					e.z() + (e.axis() == 2 ? 1 : 0));
			var line = Gizmos.line(from, to, stroke, 2.5f);
			if (wp.renderThroughWalls) {
				line.setAlwaysOnTop();
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
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		if (!frameReady || mc.level == null) {
			return;
		}
		String dimension = currentDimension(mc);
		for (Waypoint wp : current(mc)) {
			if (!wp.visible || !wp.labelEnabled || !dimension.equals(wp.dimension)) {
				continue;
			}
			Vec3 pos = centroid(wp);
			Vector4f clip = new Vector4f((float) (pos.x - camPos.x), (float) (pos.y - camPos.y),
					(float) (pos.z - camPos.z), 1f);
			WORLD_TO_CLIP.transform(clip);
			boolean behind = clip.w <= 0.05f;
			if (behind && !wp.clampToScreen) {
				continue; // not clamping: a spot behind you simply isn't drawn
			}
			float div = behind ? -clip.w : clip.w;
			float ndcX = clip.x / div;
			float ndcY = clip.y / div;
			if (behind) {
				float mag = Math.max(Math.abs(ndcX), Math.abs(ndcY));
				float push = mag < 0.001f ? 1000f : 1.2f / mag;
				ndcX *= -push;
				ndcY *= -push;
			}
			float sx = (ndcX * 0.5f + 0.5f) * g.guiWidth();
			float sy = (1f - (ndcY * 0.5f + 0.5f)) * g.guiHeight();
			boolean offScreen = behind || sx < 0 || sx > g.guiWidth() || sy < 0 || sy > g.guiHeight();
			if (!wp.clampToScreen && offScreen) {
				continue; // off-screen and not clamped
			}
			// Zoom scaling follows the actual field of view, so any zoom mod works. A chip
			// pinned to the screen edge is not at its real position, so it never scales.
			float zoom = wp.scaleWithZoom && !offScreen ? fovZoom : 1f;
			drawChip(g, mc, s, wp, sx, sy, (int) pos.distanceTo(camPos), zoom);
		}
	}

	/** Center of the waypoint's blocks (multi-block waypoints label their middle). */
	private static Vec3 centroid(Waypoint wp) {
		if (wp.blocks.isEmpty()) {
			return Vec3.ZERO;
		}
		double x = 0;
		double y = 0;
		double z = 0;
		for (Block b : wp.blocks) {
			x += b.x + 0.5;
			y += b.y + 1.0;
			z += b.z + 0.5;
		}
		int n = wp.blocks.size();
		return new Vec3(x / n, y / n, z / n);
	}

	private void drawChip(GuiGraphicsExtractor g, Minecraft mc, Settings s, Waypoint wp, float sx, float sy,
			int dist, float zoom) {
		// The chip's own size: module scale × zoom magnification × the waypoint's own scale.
		float scale = s.scale * zoom * wp.scale;
		boolean showName = wp.display != Display.ICON;
		boolean wantIcon = wp.display != Display.NAME;
		String name = showName ? wp.name : "";
		String distText = wp.showDistance ? dist + "m" : null;
		ItemStack icon = wantIcon ? iconStack(wp.icon) : ItemStack.EMPTY;
		boolean hasItem = !icon.isEmpty();
		// Icon-only chips with no resolvable item still show the colored marker square;
		// name-only chips show no mark at all, so it takes no width.
		float iconS = !wantIcon ? 0 : (hasItem ? 10 : (showName ? 5 : 8)) * scale;
		float gap = showName && wantIcon ? 3 * scale : 0;
		float nameW = showName ? HudText.width(mc, s, name, scale) : 0;
		float distW = distText != null ? HudText.width(mc, s, distText, scale) : 0;
		float lineH = HudText.lineHeight(mc, s, scale);
		float contentW = Math.max(iconS + gap + nameW, distW);
		float w = contentW + s.extraW(PAD_X) * scale;
		float h = lineH + (distText != null ? lineH + scale : 0) + s.extraH(PAD_Y) * scale;

		float x = Math.max(EDGE, Math.min(g.guiWidth() - EDGE - w, sx - w / 2f));
		float y = Math.max(EDGE, Math.min(g.guiHeight() - EDGE - h, sy - h));

		drawChromeScreen(g, mc, Math.round(x), Math.round(y), Math.round(w), Math.round(h), scale);
		int color = wp.color.chrome();
		float ty = y + s.insetY(PAD_Y) * scale;
		// With no name the mark is alone on its line, so centre it in the chip rather than
		// pinning it left (the distance line can be wider than the mark itself).
		float tx = showName
				? x + s.insetX(PAD_X) * scale
				: x + (w - iconS) / 2f;
		if (hasItem) {
			g.pose().pushMatrix();
			g.pose().translate(tx, ty + (lineH - iconS) / 2f);
			g.pose().scale(iconS / 16f, iconS / 16f);
			g.item(icon, 0, 0);
			g.pose().popMatrix();
		} else if (wantIcon) {
			// Name-only chips carry no marker square at all.
			g.fill(Math.round(tx), Math.round(ty + (lineH - iconS) / 2f),
					Math.round(tx + iconS), Math.round(ty + (lineH + iconS) / 2f), color);
		}
		if (showName) {
			float nameX = wantIcon ? tx + iconS + gap : tx;
			HudText.draw(g, mc, s, name, nameX, ty, wp.nameColor, 0, name.length(), scale);
		}
		if (distText != null) {
			HudText.draw(g, mc, s, distText, x + (w - distW) / 2f, ty + lineH + scale,
					i -> 0xB2000000 | (color & 0xFFFFFF), distText.length(), scale);
		}
	}
}
