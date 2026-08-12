package dev.clientify.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.Menus;

/**
 * Wires Mod Menu's config button on the Clientify row to the HUD editor, the same screen RShift
 * opens. Mod Menu is optional: this class is only ever loaded through its {@code modmenu}
 * entrypoint, so the mod runs identically without it.
 *
 * <p>The parent is handed to {@link Menus#editorFrom} because Mod Menu can be opened from the title
 * screen, where closing to no screen at all would leave the client with nothing to draw.
 */
public class ClientifyModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<HudEditorScreen> getModConfigScreenFactory() {
		return Menus::editorFrom;
	}
}
