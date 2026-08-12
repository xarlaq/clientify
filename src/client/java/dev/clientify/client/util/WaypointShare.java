package dev.clientify.client.util;

import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.modules.WaypointsModule;
import dev.clientify.client.modules.WaypointsModule.Block;
import dev.clientify.client.modules.WaypointsModule.Waypoint;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.minecraft.client.Minecraft;

/**
 * Turning a waypoint into a string and back.
 *
 * <p>One format serves both ways of sharing — the chat line and the copied code are the same
 * payload, so a code pasted from Discord and a code clicked in chat travel through identical
 * parsing. Base64 of UTF-8, URL-safe and unpadded, because the alternative is worrying about
 * which characters a given server's chat filter eats.
 *
 * <p>Fields are joined with a unit separator and the NAME IS LAST, split with a limit, so a name
 * containing the separator cannot shift the numbers that follow it.
 */
public final class WaypointShare {
	/** Marks a payload in a chat line. Versioned so a later format can be told apart. */
	public static final String PREFIX = "CWP1-";
	private static final char SEP = '\u001F';
	/** Chat is capped at 256; this leaves room for the prefix and the readable name. */
	private static final int MAX_CODE = 180;

	private WaypointShare() {
	}

	/** The shareable code for {@code wp}, or null if it has no position to share. */
	public static String encode(Waypoint wp) {
		if (wp == null || wp.blocks.isEmpty()) {
			return null;
		}
		Block a = wp.anchor();
		String body = a.x + "" + SEP + a.y + SEP + a.z + SEP + wp.dimension + SEP
				+ wp.color.a + SEP + wp.name;
		String code = PREFIX + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(body.getBytes(StandardCharsets.UTF_8));
		return code.length() > MAX_CODE ? null : code;
	}

	/**
	 * Reads a code back into a waypoint, or null if it is not one of ours or is malformed.
	 *
	 * <p>Everything here is attacker-controlled — it arrives over public chat — so nothing is
	 * trusted: a bad code returns null rather than throwing, and the coordinates are clamped to
	 * the world's own limits so a hostile one cannot push a marker somewhere that breaks rendering.
	 */
	public static Waypoint decode(String code) {
		if (code == null) {
			return null;
		}
		String trimmed = code.trim();
		int at = trimmed.indexOf(PREFIX);
		if (at < 0) {
			return null;
		}
		trimmed = trimmed.substring(at + PREFIX.length());
		// Stop at the first character that cannot be in the alphabet, so a code pasted with
		// trailing words still reads.
		int end = 0;
		while (end < trimmed.length() && isCodeChar(trimmed.charAt(end))) {
			end++;
		}
		trimmed = trimmed.substring(0, end);
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			String body = new String(Base64.getUrlDecoder().decode(trimmed), StandardCharsets.UTF_8);
			String[] parts = body.split(String.valueOf(SEP), 6);
			if (parts.length < 6) {
				return null;
			}
			Waypoint wp = new Waypoint();
			int x = clamp(Integer.parseInt(parts[0]));
			int y = Math.max(-2048, Math.min(2048, Integer.parseInt(parts[1])));
			int z = clamp(Integer.parseInt(parts[2]));
			wp.blocks.add(new Block(x, y, z));
			wp.dimension = sane(parts[3], 64);
			wp.color = new ColorSpec(hexOr(parts[4]));
			wp.name = sane(parts[5], 48);
			if (wp.name.isBlank()) {
				wp.name = "Waypoint";
			}
			return wp;
		} catch (RuntimeException e) {
			return null; // malformed base64, bad numbers, anything at all
		}
	}

	/** Finds a code inside a chat line, or null when there is not one. */
	public static String findCode(String line) {
		if (line == null) {
			return null;
		}
		int at = line.indexOf(PREFIX);
		if (at < 0) {
			return null;
		}
		int end = at + PREFIX.length();
		while (end < line.length() && isCodeChar(line.charAt(end))) {
			end++;
		}
		return line.substring(at, end);
	}

	/** The line sent to chat: readable for everyone, parseable for us. */
	public static String chatLine(Waypoint wp) {
		String code = encode(wp);
		return code == null ? null : "[Clientify Waypoint] " + sane(wp.name, 48) + " " + code;
	}

	/** Puts a code on the system clipboard. */
	public static void copyToClipboard(Minecraft mc, String code) {
		if (code != null && mc.keyboardHandler != null) {
			mc.keyboardHandler.setClipboard(code);
		}
	}

	public static String fromClipboard(Minecraft mc) {
		return mc.keyboardHandler == null ? null : mc.keyboardHandler.getClipboard();
	}

	private static boolean isCodeChar(char c) {
		return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
				|| c == '-' || c == '_';
	}

	/** A #RRGGBB from the payload, or the module default when it is anything else. */
	private static String hexOr(String s) {
		String h = sane(s, 9);
		return h.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?") ? h : "#F35D12";
	}

	private static int clamp(int v) {
		return Math.max(-30000000, Math.min(30000000, v));
	}

	/** Trims to length and strips control characters, section signs included. */
	private static String sane(String s, int max) {
		StringBuilder out = new StringBuilder(Math.min(s.length(), max));
		for (int i = 0; i < s.length() && out.length() < max; i++) {
			char c = s.charAt(i);
			if (c >= ' ' && c != '§' && c != '\u001F') {
				out.append(c);
			}
		}
		return out.toString().trim();
	}
}
