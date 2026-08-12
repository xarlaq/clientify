package dev.clientify.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.clientify.client.ClientifyClient;
import dev.clientify.client.hud.HudModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Config on disk: a {@code Clientify} folder with one file per profile.
 *
 * <pre>
 * config/Clientify/
 *   _settings.json     shared: which profile is active, the sidebar order, the menu theme
 *   _waypoints.json    shared: places in a world, the same for every profile
 *   Default.json       one profile: { "name", "modules", "list" }
 *   PvP.json
 * </pre>
 *
 * <p>A profile is its own file named after it, so profiles can be copied between installs, kept in
 * a repo, or handed to someone by sending them one file.
 *
 * <p>The two shared files start with an underscore and profile names are never allowed to, which is
 * what keeps a profile called "settings" from overwriting them.
 *
 * <p>The name inside a profile file wins over its filename. Filenames have to lose the characters a
 * filesystem will not take, and a profile called {@code PvP/Build} should still read back as
 * {@code PvP/Build} rather than as whatever survived being written down.
 */
public final class ClientifyConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int VERSION = 3;
	private static final int MAX_NAME = 20;

	private static final String SETTINGS_FILE = "_settings.json";
	private static final String WAYPOINTS_FILE = "_waypoints.json";

	/** Profile name → its "modules" JsonObject. Insertion order = sidebar order. */
	private static final Map<String, JsonObject> PROFILES = new LinkedHashMap<>();
	/** Profile name → how its module list is sorted. */
	private static final Map<String, ModListPrefs> LIST = new LinkedHashMap<>();
	/** Profile name → the file it came from, so rename and delete can find it again. */
	private static final Map<String, Path> FILES = new LinkedHashMap<>();

	private static String active = "Default";
	private static GlobalSettings global = new GlobalSettings();
	private static dev.clientify.client.modules.WaypointsModule.Store waypointStore =
			new dev.clientify.client.modules.WaypointsModule.Store();

	private ClientifyConfig() {
	}

	/** The folder everything lives in. */
	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("Clientify");
	}

	public static String activeProfile() {
		return active;
	}

	public static List<String> profileNames() {
		return new ArrayList<>(PROFILES.keySet());
	}

	/** Menu theme — shared, not per-profile. */
	public static GlobalSettings global() {
		return global;
	}

	/**
	 * Waypoints are shared too: they describe places in a world, so they belong to the world rather
	 * than to whichever HUD layout happened to be on at the time.
	 */
	public static dev.clientify.client.modules.WaypointsModule.Store waypoints() {
		return waypointStore;
	}

	public static void resetWaypoints() {
		waypointStore = new dev.clientify.client.modules.WaypointsModule.Store();
	}

	public static void resetGlobal() {
		global = new GlobalSettings();
	}

	/** How the active profile's module list is arranged. Mutated in place by the list screen. */
	public static ModListPrefs listPrefs() {
		return LIST.computeIfAbsent(active, k -> new ModListPrefs()).normalised();
	}

	// ---- filenames ----

	/**
	 * A profile name as a filename. Everything a filesystem refuses becomes a dash, a leading
	 * underscore is dropped so nothing can pose as a shared file, and an empty result falls back to
	 * "Profile" rather than producing ".json".
	 */
	private static String stem(String name) {
		String s = name.replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").trim();
		while (s.startsWith("_") || s.startsWith(".")) {
			s = s.substring(1);
		}
		s = s.trim();
		return s.isEmpty() ? "Profile" : s;
	}

	/** A free path for a profile, stepping the name aside if another profile already holds it. */
	private static Path pathFor(String name) {
		String base = stem(name);
		Path candidate = dir().resolve(base + ".json");
		int n = 2;
		while (holdsPath(candidate, name)) {
			candidate = dir().resolve(base + " (" + n++ + ").json");
		}
		return candidate;
	}

	/** True when some OTHER profile already writes to this path. */
	private static boolean holdsPath(Path candidate, String forName) {
		for (Map.Entry<String, Path> e : FILES.entrySet()) {
			if (!e.getKey().equals(forName)
					&& e.getValue().getFileName().toString()
							.equalsIgnoreCase(candidate.getFileName().toString())) {
				return true;
			}
		}
		return false;
	}

	// ---- load ----

	public static void load(Collection<HudModule> modules) {
		PROFILES.clear();
		LIST.clear();
		FILES.clear();
		active = "Default";
		global = new GlobalSettings();
		waypointStore = new dev.clientify.client.modules.WaypointsModule.Store();

		List<String> order = new ArrayList<>();
		Path dir = dir();
		if (Files.isDirectory(dir)) {
			readShared(dir, order);
			readProfiles(dir);
		}

		// The saved order first, then anything found on disk it did not mention — a profile file
		// dropped into the folder by hand shows up rather than being ignored.
		Map<String, JsonObject> ordered = new LinkedHashMap<>();
		for (String name : order) {
			if (PROFILES.containsKey(name)) {
				ordered.put(name, PROFILES.get(name));
			}
		}
		ordered.putAll(PROFILES);
		PROFILES.clear();
		PROFILES.putAll(ordered);

		if (PROFILES.isEmpty()) {
			PROFILES.put("Default", new JsonObject());
			LIST.put("Default", new ModListPrefs());
		}
		if (!PROFILES.containsKey(active)) {
			active = PROFILES.keySet().iterator().next();
		}
		applyProfile(modules, active);
	}

	private static void readShared(Path dir, List<String> order) {
		Path settings = dir.resolve(SETTINGS_FILE);
		if (Files.exists(settings)) {
			try {
				JsonObject root = JsonParser.parseString(Files.readString(settings)).getAsJsonObject();
				if (root.has("activeProfile")) {
					active = root.get("activeProfile").getAsString();
				}
				if (root.has("global") && root.get("global").isJsonObject()) {
					GlobalSettings g = GSON.fromJson(root.get("global"), GlobalSettings.class);
					if (g != null) {
						global = g;
					}
				}
				if (root.has("profiles") && root.get("profiles").isJsonArray()) {
					for (JsonElement e : root.getAsJsonArray("profiles")) {
						order.add(e.getAsString());
					}
				}
			} catch (Exception e) {
				ClientifyClient.LOGGER.warn("Could not read {}, using defaults", settings, e);
			}
		}
		Path waypoints = dir.resolve(WAYPOINTS_FILE);
		if (Files.exists(waypoints)) {
			try {
				var w = GSON.fromJson(Files.readString(waypoints),
						dev.clientify.client.modules.WaypointsModule.Store.class);
				if (w != null) {
					waypointStore = w;
				}
			} catch (Exception e) {
				ClientifyClient.LOGGER.warn("Could not read {}, using defaults", waypoints, e);
			}
		}
	}

	/** Every {@code *.json} in the folder that is not one of the shared files is a profile. */
	private static void readProfiles(Path dir) {
		List<Path> files = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir)) {
			s.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
							.endsWith(".json"))
					.filter(p -> !p.getFileName().toString().startsWith("_"))
					.sorted(java.util.Comparator.comparing(p -> p.getFileName().toString()))
					.forEach(files::add);
		} catch (IOException e) {
			ClientifyClient.LOGGER.warn("Could not list {}", dir, e);
			return;
		}
		for (Path p : files) {
			String fileName = p.getFileName().toString();
			String name = fileName.substring(0, fileName.length() - ".json".length());
			JsonObject modules = new JsonObject();
			ModListPrefs prefs = new ModListPrefs();
			try {
				JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (root.has("name") && root.get("name").isJsonPrimitive()) {
					String inside = root.get("name").getAsString().trim();
					if (!inside.isEmpty()) {
						name = inside;
					}
				}
				if (root.has("modules") && root.get("modules").isJsonObject()) {
					modules = root.getAsJsonObject("modules");
				}
				if (root.has("list") && root.get("list").isJsonObject()) {
					ModListPrefs read = GSON.fromJson(root.get("list"), ModListPrefs.class);
					if (read != null) {
						prefs = read;
					}
				}
			} catch (Exception e) {
				ClientifyClient.LOGGER.warn("Bad profile file {}, using defaults", p, e);
			}
			if (PROFILES.containsKey(name)) {
				ClientifyClient.LOGGER.warn("Two profiles both called '{}' — keeping {}", name,
						FILES.get(name));
				continue;
			}
			PROFILES.put(name, modules);
			LIST.put(name, prefs.normalised());
			FILES.put(name, p);
		}
	}

	private static void applyProfile(Collection<HudModule> modules, String name) {
		JsonObject sections = PROFILES.getOrDefault(name, new JsonObject());
		for (HudModule module : modules) {
			ModuleSettings settings = null;
			JsonElement section = sections.get(module.id());
			if (section != null && section.isJsonObject()) {
				try {
					settings = GSON.fromJson(section, module.settingsClass());
				} catch (Exception e) {
					ClientifyClient.LOGGER.warn("Bad config section '{}', using defaults", module.id(), e);
				}
			}
			module.setSettings(settings != null ? settings : module.createDefaultSettings());
		}
	}

	private static JsonObject snapshot(Collection<HudModule> modules) {
		JsonObject sections = new JsonObject();
		for (HudModule module : modules) {
			sections.add(module.id(), GSON.toJsonTree(module.settings()));
		}
		return sections;
	}

	// ---- write ----

	private static void write(Path path, JsonElement json) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException e) {
			ClientifyClient.LOGGER.error("Could not save {}", path, e);
		}
	}

	/** The two shared files: which profile is active with the sidebar order, and the waypoints. */
	private static void writeShared() {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.addProperty("activeProfile", active);
		JsonArray order = new JsonArray();
		for (String name : PROFILES.keySet()) {
			order.add(name);
		}
		root.add("profiles", order);
		root.add("global", GSON.toJsonTree(global));
		write(dir().resolve(SETTINGS_FILE), root);
		write(dir().resolve(WAYPOINTS_FILE), GSON.toJsonTree(waypointStore));
	}

	/** One profile's file. Its name goes inside as well, so odd characters survive the filename. */
	private static void writeProfile(String name) {
		JsonObject root = new JsonObject();
		root.addProperty("name", name);
		root.add("modules", PROFILES.getOrDefault(name, new JsonObject()));
		root.add("list", GSON.toJsonTree(LIST.computeIfAbsent(name, k -> new ModListPrefs())
				.normalised()));
		Path path = FILES.computeIfAbsent(name, ClientifyConfig::pathFor);
		write(path, root);
	}

	/**
	 * Writes the active profile and the shared files.
	 *
	 * <p>Only the active one: it is the only profile whose settings are live in memory, so the rest
	 * cannot have changed. Anything that does change another profile writes it itself.
	 */
	public static void save(Collection<HudModule> modules) {
		PROFILES.put(active, snapshot(modules));
		writeProfile(active);
		writeShared();
	}

	// ---- profiles ----

	public static final int MAX_PROFILES = 10;

	public static void switchProfile(Collection<HudModule> modules, String name) {
		if (name.equals(active) || !PROFILES.containsKey(name)) {
			return;
		}
		// The profile being left is written before it stops being the live one.
		PROFILES.put(active, snapshot(modules));
		writeProfile(active);
		active = name;
		applyProfile(modules, name);
		writeShared();
	}

	/** Copies the current settings into a new profile and switches to it (max 10). */
	public static String createProfile(Collection<HudModule> modules) {
		if (PROFILES.size() >= MAX_PROFILES) {
			return active;
		}
		PROFILES.put(active, snapshot(modules));
		writeProfile(active);
		int n = PROFILES.size() + 1;
		String name = "Profile " + n;
		while (PROFILES.containsKey(name)) {
			name = "Profile " + ++n;
		}
		PROFILES.put(name, snapshot(modules));
		// A copy inherits how its list was arranged, the same way it inherits the modules.
		LIST.put(name, GSON.fromJson(GSON.toJsonTree(listPrefs()), ModListPrefs.class).normalised());
		active = name;
		writeProfile(name);
		writeShared();
		return name;
	}

	public static void renameProfile(Collection<HudModule> modules, String oldName, String newName) {
		newName = newName.trim();
		if (newName.length() > MAX_NAME) {
			newName = newName.substring(0, MAX_NAME).trim();
		}
		if (newName.isEmpty() || newName.equals(oldName) || PROFILES.containsKey(newName)
				|| !PROFILES.containsKey(oldName)) {
			return;
		}
		// Rebuilt rather than re-put, so the renamed profile keeps its place in the sidebar.
		Map<String, JsonObject> profiles = new LinkedHashMap<>();
		Map<String, ModListPrefs> lists = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> e : PROFILES.entrySet()) {
			String key = e.getKey().equals(oldName) ? newName : e.getKey();
			profiles.put(key, e.getValue());
			lists.put(key, LIST.computeIfAbsent(e.getKey(), k -> new ModListPrefs()));
		}
		PROFILES.clear();
		PROFILES.putAll(profiles);
		LIST.clear();
		LIST.putAll(lists);

		Path oldPath = FILES.remove(oldName);
		if (active.equals(oldName)) {
			active = newName;
		}
		writeProfile(newName); // takes a fresh path from the new name
		if (oldPath != null && !oldPath.equals(FILES.get(newName))) {
			try {
				Files.deleteIfExists(oldPath);
			} catch (IOException e) {
				ClientifyClient.LOGGER.warn("Renamed the profile but could not remove {}", oldPath, e);
			}
		}
		writeShared();
	}

	/** Resets every module of a profile to its defaults (an empty section = defaults on apply). */
	public static void resetProfile(Collection<HudModule> modules, String name) {
		if (!PROFILES.containsKey(name)) {
			return;
		}
		PROFILES.put(name, new JsonObject());
		LIST.put(name, new ModListPrefs());
		if (name.equals(active)) {
			applyProfile(modules, name);
		}
		writeProfile(name);
		writeShared();
	}

	public static void deleteProfile(Collection<HudModule> modules, String name) {
		if (PROFILES.size() <= 1 || !PROFILES.containsKey(name)) {
			return;
		}
		boolean wasActive = name.equals(active);
		PROFILES.remove(name);
		LIST.remove(name);
		Path path = FILES.remove(name);
		if (path != null) {
			try {
				Files.deleteIfExists(path);
			} catch (IOException e) {
				ClientifyClient.LOGGER.warn("Could not remove {}", path, e);
			}
		}
		if (wasActive) {
			active = PROFILES.keySet().iterator().next();
			applyProfile(modules, active);
		}
		writeShared();
	}
}
