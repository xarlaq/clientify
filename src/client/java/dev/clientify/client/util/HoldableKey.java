package dev.clientify.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * A keybind that can also answer "is it held down right now" while a screen is open.
 *
 * <p>{@link KeyMapping#isDown()} is driven by the game's own input handling, which stops feeding
 * keybinds once a screen takes over — exactly when a tooltip lock needs to know. So the physical
 * key is read straight from the window instead. Subclassing is what gets at the bound key: the
 * field is protected, and reaching it any other way would mean a mixin for one getter.
 */
public class HoldableKey extends KeyMapping {
	public HoldableKey(String name, int defaultCode, Category category) {
		super(name, defaultCode, category);
	}

	/** True while the bound key is physically down. Mouse binds and unbound keys are never held. */
	public boolean isHeld() {
		return isHeld(this.key);
	}

	/**
	 * The same question for a keybind we do not own — the game's own Sprint, say. Fabric hands out
	 * the bound key, which is the part {@link KeyMapping} keeps to itself.
	 *
	 * <p>Worth knowing for vanilla's Sprint in particular: with Toggle Sprint on, {@code isDown()}
	 * is the latched toggle rather than a hold, so anything that means "while you are holding it"
	 * has to ask the window.
	 */
	public static boolean isHeld(KeyMapping mapping) {
		return mapping != null
				&& isHeld(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(mapping));
	}

	/**
	 * The same for a key held on its own rather than through a binding — what the keystrokes module
	 * watches for a key you picked yourself, which may not be bound to anything at all.
	 */
	public static boolean isHeld(InputConstants.Key bound) {
		// Nothing bound, or a value no key can have: a config written by the 1.21.11 build can hold
		// GLFW's -1 for "unbound", and vanilla's lookup indexes its buffer without checking.
		if (bound == null || bound.equals(InputConstants.UNKNOWN) || bound.getValue() <= 0) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getWindow() == null) {
			return false;
		}
		try {
			return switch (bound.getType()) {
				case KEYBOARD -> InputConstants.isKeyDown(bound.getValue());
				case MOUSE -> mouseButtonDown(bound.getValue());
			};
		} catch (IndexOutOfBoundsException e) {
			return false; // a key number past the end of SDL's table - again, an old config
		}
	}

	/**
	 * Reads the button from SDL itself, like vanilla's own {@link InputConstants#isKeyDown} does
	 * for keys. The game's MouseHandler only tracks left, middle and right, and a side button is only
	 * seen through a keybind bound to it - neither answers for a button nothing is bound to while a
	 * screen is open, which is the case this class exists for.
	 *
	 * <p>A mouse key's value in 26.x IS the SDL button number (SDLEventHandler builds the button info
	 * straight from the event), and SDL's mask for button n is bit n-1.
	 */
	private static boolean mouseButtonDown(int button) {
		if (button > 32) {
			return false;
		}
		int flags = org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null);
		return (flags & (1 << (button - 1))) != 0;
	}
}
