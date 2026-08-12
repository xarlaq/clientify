package dev.clientify.client.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * How many totems each player has popped.
 *
 * <p>Counted from the event the server sends everyone when a totem saves someone, so it sees every
 * player in range rather than only what our own inventory could tell us. Nothing is counted while
 * the counter is switched off, which is why turning it on mid-fight can start from the wrong
 * number — there is no way to ask the server what it missed.
 *
 * <p>Insertion order is kept so "most recent" can be sorted on: a pop moves its player to the end.
 * Everything here runs on the main thread — the packet handler waits for it, and rendering is on
 * it — so a plain map is enough.
 *
 * <p>Cleared when the connection changes rather than when the level does. Walking through a portal
 * is not a reason to forget who has popped what; joining a different server is.
 */
public final class TotemCounts {
	private static final Map<UUID, Integer> COUNTS = new LinkedHashMap<>();
	private static Object connection;

	private TotemCounts() {
	}

	public static void pop(UUID player) {
		Integer had = COUNTS.remove(player);
		COUNTS.put(player, had == null ? 1 : had + 1);
	}

	public static int of(UUID player) {
		return COUNTS.getOrDefault(player, 0);
	}

	/** Everyone who has popped, oldest first. */
	public static List<Map.Entry<UUID, Integer>> entries() {
		return new ArrayList<>(COUNTS.entrySet());
	}

	public static boolean isEmpty() {
		return COUNTS.isEmpty();
	}

	public static void reset() {
		COUNTS.clear();
	}

	/** Forgets everything on a new connection. Called once a tick from the module. */
	public static void followConnection(Minecraft mc) {
		Object now = mc.getConnection();
		if (now != connection) {
			connection = now;
			COUNTS.clear();
		}
	}
}
