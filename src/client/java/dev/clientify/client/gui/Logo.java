package dev.clientify.client.gui;

import dev.clientify.client.ClientifyClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The Clientify mark — the wireframe cube with the C in it — drawn without the dark plate the mod
 * icon sits on, for use inside the menus where there is already a surface behind it.
 *
 * <p>Two cells (cube, letter) rather than one flat image, so the letter can follow the user's accent
 * colour while the cube stays white. Baked by {@code tools/LogoGen.java mark}; the {@code .mcmeta}
 * asks for LINEAR filtering, which is what keeps the strokes clean when the 48px cells are drawn at
 * 20.
 */
public final class Logo {
	public static final Identifier SHEET = ClientifyClient.id("textures/ui/logo.png");
	private static final int CELL = 48;

	private Logo() {
	}

	/** Draws the mark in a {@code size} box at (x, y): white cube, accent letter. */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int size) {
		// The accent's alpha is the user's (the default is half-transparent for panel chrome); the
		// logo is ink, so it takes the hue at full strength.
		draw(g, x, y, size, 0xFFF6F6F8, 0xFF000000 | (Ui.accent() & 0xFFFFFF));
	}

	/** Draws the mark with explicit cube and letter colours. */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int size, int cubeArgb, int letterArgb) {
		blit(g, 0, x, y, size, cubeArgb);
		blit(g, 1, x, y, size, letterArgb);
	}

	private static void blit(GuiGraphicsExtractor g, int cell, int x, int y, int size, int argb) {
		g.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, cell * CELL, 0, size, size,
				CELL, CELL, CELL * 2, CELL, argb);
	}
}
