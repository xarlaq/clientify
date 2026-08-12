package dev.clientify.client.util;

import net.minecraft.util.Mth;

/** 8-way compass direction from a yaw. */
public final class Directions {
	private static final String[] SHORT = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
	private static final String[] FULL = {
			"North", "North East", "East", "South East", "South", "South West", "West", "North West"
	};

	private Directions() {
	}

	public static String of(float yaw, boolean full) {
		// Yaw 0 = south, ±180 = north; 45° sectors with N at index 0.
		int idx = Math.floorMod(Math.round((Mth.wrapDegrees(yaw) + 180f) / 45f), 8);
		return (full ? FULL : SHORT)[idx];
	}
}
