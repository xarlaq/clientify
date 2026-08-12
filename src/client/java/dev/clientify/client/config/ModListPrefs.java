package dev.clientify.client.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How the module list is arranged: the sort mode, the order the user dragged cards into, and when
 * each module was last opened.
 *
 * <p>Saved per profile rather than globally. A profile is a whole HUD, and the order you want to
 * work through its modules in belongs to that HUD — a PvP layout and a building layout do not want
 * the same things at the top.
 *
 * <p>Serialized by GSON, so every field is public and defaulted at declaration. A section written
 * by an older build simply keeps these defaults for whatever it does not mention.
 */
public class ModListPrefs {
	/** One of ModListScreen's SORTS. An unknown value falls through to the registered order. */
	public String sort = "DEFAULT";

	/** The user's own order, by module id, once they have dragged a card. */
	public List<String> order = new ArrayList<>();

	/** Module id → when it was last opened, so LAST USED can put recent work first. */
	public Map<String, Long> lastUsed = new HashMap<>();

	/**
	 * Replaces anything a hand-edited or truncated file left null. GSON will happily write a null
	 * into a field it finds explicitly nulled in JSON, and the list code reads these every frame.
	 */
	public ModListPrefs normalised() {
		if (sort == null) {
			sort = "DEFAULT";
		}
		if (order == null) {
			order = new ArrayList<>();
		}
		if (lastUsed == null) {
			lastUsed = new HashMap<>();
		}
		return this;
	}
}
