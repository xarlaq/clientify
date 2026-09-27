package dev.clientify.client.util;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.clientify.client.gui.WaypointImportScreen;
import dev.clientify.client.modules.WaypointsModule.Waypoint;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * The receiving half of waypoint sharing: spotting a shared code in chat, showing it as something
 * worth clicking, and turning the click into an import.
 *
 * <p>No mixin anywhere in here. Fabric's message events allow an incoming line to be cancelled and
 * our own printed in its place, so vanilla chat rendering is untouched — which keeps this off the
 * streamproof surface and out of the way of every other mod that patches chat.
 *
 * <p>The click runs a CLIENT command. Fabric injects into {@code sendUnattendedCommand} — the path
 * a clicked component takes — at HEAD and cancellable, so the command is handled here and the
 * server never sees it.
 */
public final class WaypointShareHandler {
	/** Bare name, no slash: what a click sends and what the dispatcher matches. */
	public static final String COMMAND = "clientifywp";

	private WaypointShareHandler() {
	}

	public static void init() {
		// Player chat and system messages both carry shared codes: a server may relay a player's
		// line as a system message, so watching only one of them misses half the cases.
		ClientReceiveMessageEvents.ALLOW_CHAT.register(
				(message, signedMessage, sender, params, receptionTimestamp) -> allow(message));
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || allow(message));

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				dispatcher.register(ClientCommands.literal(COMMAND)
						.then(ClientCommands.argument("code", StringArgumentType.greedyString())
								.executes(ctx -> {
									open(StringArgumentType.getString(ctx, "code"));
									return 1;
								}))));
	}

	/**
	 * Returns false to swallow the raw line, having printed a readable one in its place.
	 *
	 * <p>Only ever swallows a line that actually holds a decodable code — a line that merely looks
	 * like one is left alone, because silently eating someone's chat message is far worse than
	 * showing a code we could not read.
	 */
	private static boolean allow(Component message) {
		String raw = message.getString();
		String code = WaypointShare.findCode(raw);
		if (code == null) {
			return true;
		}
		Waypoint wp = WaypointShare.decode(code);
		if (wp == null) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui == null) {
			return true;
		}
		// Keep whatever the server put before the code (the player's name, channel tags) so it is
		// still clear who sent it; only the payload itself is replaced.
		int at = raw.indexOf("[Clientify Waypoint]");
		String lead = at > 0 ? raw.substring(0, at).trim() : "";
		Component line = Component.empty();
		if (!lead.isEmpty()) {
			line = Component.literal(lead + " ").withStyle(ChatFormatting.GRAY);
		}
		int accent = dev.clientify.client.gui.Ui.accent() & 0xFFFFFF;
		var wpName = Component.literal("[" + wp.name + "]").withStyle(s -> s.withColor(accent));
		var tail = Component.literal(" — click to add").withStyle(ChatFormatting.GRAY);
		var full = Component.empty().append(line).append(wpName).append(tail).withStyle(s -> s
				.withClickEvent(new ClickEvent.RunCommand("/" + COMMAND + " " + code))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(
						wp.name + "\n" + coords(wp) + "\nClick to add this waypoint"))));
		mc.gui.hud.getChat().addClientSystemMessage(full);
		return false;
	}

	private static String coords(Waypoint wp) {
		var a = wp.anchor();
		return a.x + ", " + a.y + ", " + a.z;
	}

	/** Opens the confirmation for a code, from a chat click or from the Import button. */
	public static void open(String code) {
		Minecraft mc = Minecraft.getInstance();
		Waypoint wp = WaypointShare.decode(code);
		mc.execute(() -> {
			if (wp == null) {
				mc.gui.hud.getChat().addClientSystemMessage(Component.literal("That is not a Clientify waypoint code.")
						.withStyle(ChatFormatting.RED));
				return;
			}
			mc.gui.setScreen(new WaypointImportScreen(wp, mc.gui.screen()));
		});
	}
}
