package dev.clientify.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Opening and leaving the Clientify menus.
 *
 * <p>In a world "leave" means close every screen and go back to the game, which is what RShift has
 * always done. Reached from outside a world — Mod Menu's config button on the title screen — closing
 * to no screen at all would leave the client with nothing to draw, so the way in is remembered and
 * closing goes back there.
 */
public final class Menus {
	private static Screen origin;

	private Menus() {
	}

	/** The editor, opened from in-game (RShift): leaving it returns to the game. */
	public static HudEditorScreen editor() {
		origin = null;
		return new HudEditorScreen();
	}

	/** The editor, opened from another screen: leaving it returns to {@code from}. */
	public static HudEditorScreen editorFrom(Screen from) {
		origin = from;
		return new HudEditorScreen();
	}

	/** Leaves the menus entirely — to the game in a world, else back to where we came in. */
	public static void exit(Minecraft mc) {
		Screen back = null;
		if (mc.level == null) {
			// The title screen is the fallback rather than nothing at all: a null screen with no
			// level is a black window with no way out.
			back = origin != null ? origin : new TitleScreen();
		}
		origin = null;
		mc.setScreen(back);
	}
}
