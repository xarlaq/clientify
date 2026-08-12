package dev.clientify.client.gui.widget;

import dev.clientify.client.gui.Ui;
import dev.clientify.client.util.Draw;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public class GlassButton extends AbstractButton {
	/** Surface the button is drawn as. */
	public enum Style {
		/** The faint white card used for secondary buttons. */
		CARD,
		/** Filled with the accent — for the one action a screen is about. */
		PRIMARY,
		/** The menu panel's own surface and hairline, for buttons that read as part of the chrome. */
		PANEL
	}

	private final Runnable action;
	private final Style style;

	public GlassButton(int x, int y, int w, int h, Component label, Runnable action) {
		this(x, y, w, h, label, action, Style.CARD);
	}

	public GlassButton(int x, int y, int w, int h, Component label, Runnable action, Style style) {
		super(x, y, w, h, label);
		this.action = action;
		this.style = style;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	@Override
	protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		boolean hovered = isHoveredOrFocused();
		switch (style) {
			case PRIMARY -> Draw.smoothRounded(g, x, y, w, h, Draw.R_SMALL,
					hovered ? Ui.accent() : Ui.accentDim());
			case PANEL -> {
				Draw.smoothRoundedBordered(g, x, y, w, h, Draw.R_SMALL, Ui.panel(),
						hovered ? Ui.accentSoft() : Ui.HAIRLINE, 1);
				if (hovered) {
					// The panel colour is mostly transparent, so a hover has to come from a wash of
					// its own rather than from brightening the fill.
					Draw.smoothRounded(g, x + 1, y + 1, w - 2, h - 2, Draw.R_SMALL - 1, Ui.card());
				}
			}
			case CARD -> Ui.card(g, x, y, w, h, hovered); // border is built into the hovered card
		}
		String msg = getMessage().getString();
		Ui.str(g, msg, x + (w - Ui.sw(msg)) / 2, y + (h - 8) / 2, active ? Ui.TEXT : Ui.TEXT_DIM);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
