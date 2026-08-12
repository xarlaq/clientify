package dev.clientify.client.hud;

import dev.clientify.client.ClientifyClient;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

/** Font selection. Fonts in 1.21.11 are chosen per-Component via Style + FontDescription. */
public final class Fonts {
	/** Clientify's bundled font (Roboto Medium, assets/clientify/font/roboto.json). */
	public static final FontDescription CLIENTIFY_FONT = new FontDescription.Resource(ClientifyClient.id("roboto"));

	private Fonts() {
	}

	public static Style style() {
		return Style.EMPTY.withFont(CLIENTIFY_FONT);
	}
}
