package dev.clientify.client.gui.widget;

import dev.clientify.client.gui.Textures;
import dev.clientify.client.gui.Ui;
import dev.clientify.client.util.Draw;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * EditBox restyled as a Lunar field: translucent dark card, hairline border, optional
 * magnifier icon, dim placeholder. Vanilla handles editing/IME/selection.
 */
public class GlassEditBox extends EditBox {
	private int cardW;
	private final int cardH;
	private final boolean searchIcon;
	private String placeholder = "";
	/** When on, Shift+Enter types a literal {@code \n} (the HUD line-break escape). */
	public boolean newlineEscape;

	public GlassEditBox(Font font, int x, int y, int w, int h, Component label) {
		this(font, x, y, w, h, label, true);
	}

	public GlassEditBox(Font font, int x, int y, int w, int h, Component label, boolean searchIcon) {
		super(font, x + (searchIcon ? 17 : 5), y + (h - 8) / 2, w - (searchIcon ? 23 : 10), 12, label);
		this.cardW = w;
		this.cardH = h;
		this.searchIcon = searchIcon;
		setBordered(false);
	}

	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
		if (newlineEscape && isFocused() && e.hasShiftDown() && (e.key() == 257 || e.key() == 335)) {
			insertText("\\n");
			return true;
		}
		return super.keyPressed(e);
	}

	/** Resizes by OUTER card width (compact list layouts fit boxes to their cell). */
	public void resizeTo(int width) {
		if (width != cardW) {
			cardW = width;
			setWidth(width - (searchIcon ? 23 : 10));
		}
	}

	/** Repositions by OUTER card coords (the ctor stores inner text offsets). */
	public void moveTo(int x, int y) {
		setX(x + (searchIcon ? 17 : 5));
		setY(y + (cardH - 8) / 2);
	}

	@Override
	public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		int off = searchIcon ? 17 : 5;
		int cx = getX() - off;
		int cy = getY() - (cardH - 8) / 2;
		Draw.smoothRoundedBordered(g, cx, cy, cardW, cardH, 3, 0x33000000,
				isFocused() ? 0x33FFFFFF : Ui.HAIRLINE, 1);
		if (searchIcon && Textures.ensure()) {
			int n = Textures.SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.SEARCH, cx + 5, cy + (cardH - 8) / 2,
					0f, 0f, 8, 8, n, n, n, n, Ui.TEXT_DIM);
		}
		if (!placeholder.isEmpty() && getValue().isEmpty() && !isFocused()) {
			Ui.str(g, placeholder, getX(), cy + (cardH - 9) / 2 + 1, Ui.TEXT_DIM);
		}
		super.renderWidget(g, mouseX, mouseY, partialTick);
	}
}
