package dev.clientify.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

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
		if (bound == null || bound.getValue() == GLFW.GLFW_KEY_UNKNOWN) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getWindow() == null) {
			return false;
		}
		return switch (bound.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(mc.getWindow(), bound.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(mc.getWindow().handle(), bound.getValue())
					== GLFW.GLFW_PRESS;
			default -> false; // scancode binds have no stable lookup
		};
	}
}
