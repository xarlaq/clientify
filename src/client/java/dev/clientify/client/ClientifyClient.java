package dev.clientify.client;

import dev.clientify.client.gui.Menus;
import dev.clientify.client.hud.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientifyClient implements ClientModInitializer {
	public static final String MOD_ID = "clientify";
	public static final Logger LOGGER = LoggerFactory.getLogger("Clientify");

	public static KeyMapping.Category KEY_CATEGORY;
	public static KeyMapping menuKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		KEY_CATEGORY = KeyMapping.Category.register(id("clientify"));
		menuKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.clientify.menu", InputConstants.KEY_RSHIFT, KEY_CATEGORY));

		ModuleManager.init();
		dev.clientify.client.util.WaypointShareHandler.init();

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ModuleManager.tick(mc);
			while (menuKey.consumeClick()) {
				mc.setScreen(Menus.editor());
			}
		});

		// Joining a world is when a per-server profile takes effect; the level is set by then.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) ->
				dev.clientify.client.config.ServerProfiles.applyForCurrentWorld(mc));

		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> ModuleManager.save());

		LOGGER.info("Clientify initialized");
	}
}
