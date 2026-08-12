package dev.clientify.client.config;

import dev.clientify.client.ClientifyClient;
import dev.clientify.client.hud.ModuleManager;
import net.minecraft.client.Minecraft;

/**
 * Binds a profile to a server, so joining one swaps the HUD to the layout that suits it — a
 * compact set for a PvP server, coordinates and waypoints for survival — instead of rearranging by
 * hand each time. Bindings live at config root next to the profiles themselves, since a profile
 * cannot sensibly store which servers select it.
 */
public final class ServerProfiles {
	/** The key used for any single-player world; worlds come and go, the distinction that matters
	 * is server versus not. */
	public static final String SINGLEPLAYER = "singleplayer";

	private ServerProfiles() {
	}

	/** Where we are now, or null when in no world at all (title screen). */
	public static String currentKey(Minecraft mc) {
		if (mc.level == null) {
			return null;
		}
		var server = mc.getCurrentServer();
		return server != null ? server.ip : SINGLEPLAYER;
	}

	/** A readable name for the place we are in, for the settings row. */
	public static String currentLabel(Minecraft mc) {
		String key = currentKey(mc);
		if (key == null) {
			return "Not in a world";
		}
		return key.equals(SINGLEPLAYER) ? "Singleplayer" : key;
	}

	/** The profile bound to {@code key}, or null when nothing is bound. */
	public static String boundProfile(String key) {
		return key == null ? null : ClientifyConfig.global().serverProfiles.get(key);
	}

	/** Binds (or with a null profile, unbinds) the profile used for {@code key}. */
	public static void bind(String key, String profile) {
		if (key == null) {
			return;
		}
		if (profile == null) {
			ClientifyConfig.global().serverProfiles.remove(key);
		} else {
			ClientifyConfig.global().serverProfiles.put(key, profile);
		}
	}

	/**
	 * Applies the binding for wherever we just arrived. Does nothing when the feature is off, when
	 * nothing is bound, or when that profile is already active — switching reloads every module's
	 * settings, so it should not happen for no reason.
	 */
	public static void applyForCurrentWorld(Minecraft mc) {
		GlobalSettings gs = ClientifyConfig.global();
		if (!gs.profilePerServer) {
			return;
		}
		String wanted = boundProfile(currentKey(mc));
		if (wanted == null || wanted.equals(ModuleManager.activeProfile())) {
			return;
		}
		if (!ModuleManager.profiles().contains(wanted)) {
			// The profile was renamed or deleted since it was bound; drop the stale binding.
			bind(currentKey(mc), null);
			return;
		}
		ModuleManager.switchProfile(wanted);
		ClientifyClient.LOGGER.info("Switched to profile {} for {}", wanted, currentLabel(mc));
	}
}
