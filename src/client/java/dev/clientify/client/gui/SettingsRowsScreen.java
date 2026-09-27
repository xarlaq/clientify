package dev.clientify.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.widget.GlassEditBox;
import dev.clientify.client.hud.HudModule.Rect;
import dev.clientify.client.util.Colors;
import dev.clientify.client.util.Draw;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Shared machinery for settings views: an immediate-mode row list (rebuilt every frame; the
 * click walk mirrors the render walk), toggles, sliders, sections, per-row reset icons with
 * a "Reset to defaults" tooltip, and the inline ColorSpec picker (SV square + hue strip +
 * hex + mode cycler + chroma type + speed + A/B swatches). Subclasses provide the rows and
 * the header above them.
 */
public abstract class SettingsRowsScreen extends PanelScreen {
	protected static final int RESET_W = 12;

	public interface RowDraw {
		void draw(GuiGraphicsExtractor g, int y, int mx, int my);
	}

	public interface RowClick {
		boolean click(MouseButtonEvent e, int y);
	}

	public record Row(int h, RowDraw draw, RowClick click, boolean isSection) {
		/** Every row but a heading; the short form is what nearly all of them use. */
		public Row(int h, RowDraw draw, RowClick click) {
			this(h, draw, click, false);
		}

	}

	protected GlassEditBox hexBox;
	private boolean syncingHex;
	private ModuleSettings.ColorSpec expandedSpec;
	/**
	 * A colour whose opacity is not the user's to set, so the picker does not offer it.
	 *
	 * <p>An ore outline is painted into a solid block: a see-through one is a hole through the
	 * stone. Ignoring the slider silently would leave it sitting there doing nothing, which is
	 * its own kind of wrong.
	 */
	private final java.util.Set<ModuleSettings.ColorSpec> opaqueOnly =
			java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

	/** Marks a colour as one the picker should show without its opacity slider. */
	public void alwaysOpaque(ModuleSettings.ColorSpec spec) {
		opaqueOnly.add(spec);
	}
	private boolean editB;
	private float pickerHue;
	protected BiConsumer<Double, Double> dragApply;

	protected int rowY0, rowY1;
	protected double scroll;
	/** Left indent applied to rows built while it is set (grouped/inset rows). */
	protected int indent;

	protected SettingsRowsScreen(Component title, Screen editorScreen) {
		super(title, editorScreen);
	}

	@Override
	protected void initMain() {
		rowY0 = py + HEADER_H + 44;
		rowY1 = py + ph - 8;
		scroll = 0;
		expandedSpec = null;
		editB = false;
		dragApply = null;

		hexBox = new GlassEditBox(font, 0, 0, 84, 13, Component.literal("Hex"), false);
		hexBox.setResponder(v -> {
			if (!syncingHex) {
				applyHex(v);
			}
		});
		hexBox.visible = false;
		addRenderableWidget(hexBox);
		initRows();
	}

	/** Extra subclass widgets (template boxes, …). */
	protected void initRows() {
	}

	/** Reset the visibility of subclass row widgets before the row walk. */
	protected void onRowsRenderStart() {
	}

	protected abstract List<Row> buildRows();

	/** Header above the rows (back button, title, description). */
	protected abstract void renderRowsHeader(GuiGraphicsExtractor g, int mouseX, int mouseY);

	protected boolean rowsHeaderClicked(MouseButtonEvent e) {
		return false;
	}

	@Override
	protected boolean anyTextFieldFocused() {
		return super.anyTextFieldFocused() || (hexBox != null && hexBox.isFocused());
	}

	// ---- layout ----

	protected int resetX() {
		return mainX + mainW - RESET_W - 2;
	}

	/** Right edge for row controls (left of the reset icon column). */
	protected int controlRight() {
		return resetX() - 6;
	}

	// ---- shared widgets ----

	protected void drawReset(GuiGraphicsExtractor g, int y, int rowH, int mx, int my) {
		int ry = y + (rowH - RESET_W) / 2;
		boolean hover = mx >= resetX() && mx < resetX() + RESET_W && my >= ry && my < ry + RESET_W
				&& my >= rowY0 && my <= rowY1;
		if (Textures.ensure()) {
			int n = Textures.SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.RESET, resetX(), ry, 0f, 0f, RESET_W, RESET_W,
					n, n, n, n, hover ? Ui.TEXT : 0x66EDEDF2);
		}
		if (hover) {
			setTooltip("Reset to defaults", mx, my);
		}
	}

	protected boolean resetHit(MouseButtonEvent e, int y, int rowH) {
		int ry = y + (rowH - RESET_W) / 2;
		return e.x() >= resetX() && e.x() < resetX() + RESET_W && e.y() >= ry && e.y() < ry + RESET_W;
	}

	/** Closes any open color picker (after group/global resets). */
	protected void collapsePicker() {
		expandedSpec = null;
		editB = false;
		if (hexBox != null) {
			hexBox.visible = false;
		}
	}

	/**
	 * Wraps a row so hovering it explains itself. For settings whose name cannot carry the whole
	 * meaning — the row still reads as before, the sentence only appears when asked for.
	 */
	public Row withTooltip(Row row, String text) {
		return new Row(row.h(), (g, y, mx, my) -> {
			row.draw().draw(g, y, mx, my);
			// Bounded to the visible list as well as the row: rows scrolled out from under the
			// pointer are still drawn inside the scissor, and would otherwise answer for it.
			if (mx >= mainX && mx < mainX + mainW && my >= y && my < y + row.h()
					&& my >= rowY0 && my <= rowY1) {
				setTooltip(text, mx, my);
			}
		}, row.click());
	}

	/**
	 * The same for a paired row, one sentence per setting.
	 *
	 * <p>A pair is two settings sharing a line, and one tooltip over both of them means hovering
	 * the switch you were asking about tells you as much about the other one. Either side may be
	 * null, for a pair where only one of them needs explaining.
	 *
	 * <p>Split where {@code dualToggle} splits it, indent and all, so the sentence changes exactly
	 * where the thing you are pointing at does.
	 */
	public Row withTooltip(Row row, String left, String right) {
		int ind = indent;
		return new Row(row.h(), (g, y, mx, my) -> {
			row.draw().draw(g, y, mx, my);
			if (mx < mainX || mx >= mainX + mainW || my < y || my >= y + row.h()
					|| my < rowY0 || my > rowY1) {
				return;
			}
			String text = mx < mainX + 2 + ind + (mainW - 4 - ind) / 2 ? left : right;
			if (text != null) {
				setTooltip(text, mx, my);
			}
		}, row.click(), row.isSection());
	}

	/**
	 * A row that cannot do anything here, struck through and greyed, saying why when hovered.
	 *
	 * <p>Shown rather than hidden on purpose: a setting that quietly vanishes on one setup reads
	 * as a mod that lost it, and a setting that is there and does nothing is worse still.
	 */
	public Row unsupported(Row row, String why) {
		return unsupported(row, why, 0);
	}

	/**
	 * The same, over one half of a paired row: 0 the whole row, 1 the left, 2 the right.
	 *
	 * <p>Half matters because a pair is two settings, and striking both to explain one says the
	 * wrong thing about the other.
	 */
	public Row unsupported(Row row, String why, int half) {
		return new Row(row.h(), (g, y, mx, my) -> {
			row.draw().draw(g, y, mx, my);
			int x0 = half == 2 ? mainX + mainW / 2 : mainX;
			int x1 = half == 1 ? mainX + mainW / 2 : mainX + mainW;
			g.fill(x0, y, x1, y + row.h(), 0x99101014);
			Draw.smoothRounded(g, x0 + 2, y + row.h() / 2, x1 - x0 - 4, 1, 0, 0x59FFFFFF);
			if (mx >= x0 && mx < x1 && my >= y && my < y + row.h() && my >= rowY0 && my <= rowY1) {
				setTooltip(why, mx, my);
			}
		}, (e, y) -> {
			// Only the struck part is dead; the rest of the row answers as it always did.
			int x0 = half == 2 ? mainX + mainW / 2 : mainX;
			int x1 = half == 1 ? mainX + mainW / 2 : mainX + mainW;
			if (e.x() >= x0 && e.x() < x1) {
				return true;
			}
			return row.click() != null && row.click().click(e, y);
		}, row.isSection());
	}

	/** A line of dim explanatory text — something the setting above it needs said, not a control. */
	public Row note(java.util.function.Supplier<String> text) {
		int ind = indent;
		return new Row(12, (g, y, mx, my) -> Ui.str(g, text.get(), mainX + 2 + ind, y + 1, Ui.TEXT_DIM),
				(e, y) -> false);
	}

	public Row section(String label) {
		int ind = indent;
		// The same grey as the rest of the text, just larger: brighter than it was, since a
		// heading dimmer than its own rows reads as switched off, but still not a colour.
		return new Row(16, (g, y, mx, my) -> Ui.caps(g, label, mainX + 2 + ind, y + 5,
				Ui.TEXT, 0.9f), null, true);
	}

	public Row toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			drawPill(g, mainX + 2 + ind, y + 3, get.get());
			Ui.str(g, label, mainX + 34 + ind, y + 5, Ui.TEXT);
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			if (e.x() < mainX + 40 + ind + Ui.sw(label)) {
				set.accept(!get.get());
				return true;
			}
			return false;
		});
	}

	/**
	 * A switch and a slider on one line, for the common case where one scopes the other and the
	 * pair reads as a single setting. The slider is always shown — a control that vanishes when
	 * its switch goes off costs a click to check what it was set to.
	 */
	public Row toggleSliderRow(String label, Supplier<Boolean> get, Consumer<Boolean> set,
			float min, float max, float step, Supplier<Float> value, Consumer<Float> setValue,
			String fmt, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			drawPill(g, mainX + 2 + ind, y + 3, get.get());
			Ui.str(g, label, mainX + 34 + ind, y + 5, Ui.TEXT);
			drawSlider(g, radiusTrack(y), (value.get() - min) / (max - min),
					String.format(fmt, value.get()));
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			Rect t = radiusTrack(y);
			if (grow(t).contains(e.x(), e.y())) {
				dragApply = (mx, my) -> {
					float frac = clamp01((float) ((mx - t.x()) / t.w()));
					float v = min + frac * (max - min);
					setValue.accept(Math.round(v / step) * step);
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			if (e.x() < mainX + 40 + ind + Ui.sw(label)) {
				set.accept(!get.get());
				return true;
			}
			return false;
		});
	}

	public Row sliderRow(String label, float min, float max, float step, Supplier<Float> get,
			Consumer<Float> set, String fmt, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			Ui.str(g, label, mainX + 2 + ind, y + 5, Ui.TEXT);
			drawSlider(g, sliderTrack(y), (get.get() - min) / (max - min), String.format(fmt, get.get()));
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			Rect t = sliderTrack(y);
			if (valueHit(e, t, String.format(fmt, get.get()))) {
				beginTyping((int) t.y(), min, max, step, set, numeric(String.format(fmt, get.get())));
				return true;
			}
			if (grow(t).contains(e.x(), e.y())) {
				dragApply = (mx, my) -> {
					float frac = clamp01((float) ((mx - t.x()) / t.w()));
					float v = min + frac * (max - min);
					set.accept(Math.round(v / step) * step);
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			return false;
		});
	}

	/**
	 * A slider with part of its track marked out — the range that actually does what the setting is
	 * for, when the rest of the range is legal but pointless.
	 */
	public Row sliderRowMarked(String label, float min, float max, float step, Supplier<Float> get,
			Consumer<Float> set, String fmt, Runnable reset, float markFrom, float markTo) {
		Row row = sliderRow(label, min, max, step, get, set, fmt, reset);
		return new Row(row.h(), (g, y, mx, my) -> {
			Rect t = sliderTrack(y);
			float a = clamp01((markFrom - min) / (max - min));
			float b = clamp01((markTo - min) / (max - min));
			int x0 = (int) (t.x() + a * t.w());
			int x1 = (int) (t.x() + b * t.w());
			Draw.smoothRounded(g, x0, (int) t.y() - 2, Math.max(1, x1 - x0), 8, 2, 0x26FFFFFF);
			row.draw().draw(g, y, mx, my);
		}, row.click());
	}

	/**
	 * A switch this setup cannot honour: drawn off and dimmed, ignoring clicks, saying why on hover.
	 *
	 * <p>Better than hiding it. A missing switch reads as a missing feature and gets asked about;
	 * one that is present and explains itself answers the question before it is asked.
	 */
	public Row unavailable(String label, String reason) {
		int ind = indent;
		return withTooltip(new Row(18, (g, y, mx, my) -> {
			drawPillDim(g, mainX + 2 + ind, y + 3);
			Ui.str(g, label, mainX + 34 + ind, y + 5, Ui.TEXT_DIM);
		}, (e, y) -> true), reason); // swallowed, so a click does not fall through to the row behind
	}

	/**
	 * The blur switch, or an explanation when the renderer cannot blur.
	 *
	 * <p>Every blur switch in the mod goes through here, so a renderer that cannot do it is said
	 * once and reads the same everywhere.
	 */
	public Row blurToggle(String label, Supplier<Boolean> get, Consumer<Boolean> set, Runnable reset) {
		String why = dev.clientify.client.hud.BlurBackdrop.unsupportedReason();
		return why == null ? toggle(label, get, set, reset) : unavailable(label, why);
	}

	/**
	 * Slider with one recommended value ticked on the track, in the accent colour.
	 *
	 * <p>A band says "anywhere in here"; a tick says "this one". For settings where there is a
	 * specific right answer that the default is deliberately not sitting on.
	 *
	 * <p>Drawn after the row rather than before it, so the tick reads on top of the track instead of
	 * under the fill.
	 */
	public Row sliderRowRecommended(String label, float min, float max, float step,
			Supplier<Float> get, Consumer<Float> set, String fmt, Runnable reset, float recommended) {
		Row row = sliderRow(label, min, max, step, get, set, fmt, reset);
		return new Row(row.h(), (g, y, mx, my) -> {
			row.draw().draw(g, y, mx, my);
			Rect t = sliderTrack(y);
			float f = clamp01((recommended - min) / (max - min));
			int x = (int) (t.x() + f * t.w());
			Draw.smoothRounded(g, x - 1, (int) t.y() - 3, 2, 10, 1, Ui.accent());
		}, row.click());
	}

	/**
	 * The track, reaching back to about the middle of the row.
	 *
	 * <p>Long enough to aim with: at 120 wide it sat in the right-hand quarter, which made every
	 * step of a nought-to-one slider about a pixel and a half of travel.
	 */
	private Rect sliderTrack(int y) {
		int width = Math.max(120, Math.min(170, controlRight() - mainX - mainW / 2 + 40));
		return new Rect(controlRight() - width, y + 6, width, 4);
	}

	/**
	 * Slider with small marker icons under the track's start / middle / end (time-of-day
	 * sun/moon hints). Same behavior as {@link #sliderRow} otherwise.
	 */
	public Row sliderRowIcons(String label, float min, float max, float step, Supplier<Float> get,
			Consumer<Float> set, String fmt, Runnable reset, net.minecraft.resources.Identifier[] icons) {
		int ind = indent;
		return new Row(28, (g, y, mx, my) -> {
			Ui.str(g, label, mainX + 2 + ind, y + 5, Ui.TEXT);
			Rect t = sliderTrack(y);
			drawSlider(g, t, (get.get() - min) / (max - min), String.format(fmt, get.get()));
			if (icons != null && Textures.ensure()) {
				int n = Textures.SIZE;
				for (int i = 0; i < icons.length; i++) {
					if (icons[i] == null) {
						continue;
					}
					int cx = (int) (t.x() + t.w() * i / (icons.length - 1f)) - 4;
					g.blit(RenderPipelines.GUI_TEXTURED, icons[i], cx, y + 13, 0f, 0f, 8, 8, n, n, n, n,
							0xB2EDEDF2);
				}
			}
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			Rect t = sliderTrack(y);
			if (grow(t).contains(e.x(), e.y())) {
				dragApply = (mx, my) -> {
					float frac = clamp01((float) ((mx - t.x()) / t.w()));
					float v = min + frac * (max - min);
					set.accept(Math.round(v / step) * step);
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			return false;
		});
	}

	/** Per-tile painter for {@link #tiles}: draw the tile's content inside (x,y,size,size). */
	public interface TileDraw {
		void draw(GuiGraphicsExtractor g, int index, int x, int y, int size, boolean hover, boolean selected);
	}

	/** A row of selectable square tiles (image pickers); the selected one gets an accent ring. */
	public Row tiles(int count, int tile, int gap, java.util.function.IntSupplier selected,
			TileDraw drawTile, java.util.function.IntConsumer click) {
		int totalW = count * tile + (count - 1) * gap;
		return new Row(tile + 10, (g, y, mx, my) -> {
			int x0 = mainX + (mainW - totalW) / 2;
			for (int i = 0; i < count; i++) {
				int x = x0 + i * (tile + gap);
				boolean hover = mx >= x && mx < x + tile && my >= y + 5 && my < y + 5 + tile
						&& my >= rowY0 && my <= rowY1;
				boolean sel = selected.getAsInt() == i;
				Draw.smoothRounded(g, x, y + 5, tile, tile, 4, hover && !sel ? 0x28FFFFFF : 0x14FFFFFF);
				if (sel) {
					Draw.smoothBorder(g, x, y + 5, tile, tile, 4, Ui.accent());
				}
				drawTile.draw(g, i, x, y + 5, tile, hover, sel);
			}
		}, (e, y) -> {
			int x0 = mainX + (mainW - totalW) / 2;
			for (int i = 0; i < count; i++) {
				int x = x0 + i * (tile + gap);
				if (e.x() >= x && e.x() < x + tile && e.y() >= y + 5 && e.y() < y + 5 + tile) {
					click.accept(i);
					return true;
				}
			}
			return false;
		});
	}

	/** Adds a color row (+ its expanded picker row) for a ColorSpec. */
	/** When a gear was last clicked, and which one, so only that one turns. */
	private static long gearClickAt;
	private static int gearClickX;
	private static int gearClickY;
	/** Long enough to notice, short enough not to be waited on. */
	private static final long GEAR_SPIN_MS = 260;

	/** Called when a gear is pressed, so the drawing side knows to turn it. */
	protected static void gearClicked(int x, int y) {
		gearClickAt = System.currentTimeMillis();
		gearClickX = x;
		gearClickY = y;
	}

	protected void drawGear(GuiGraphicsExtractor g, int x, int y, boolean lit) {
		if (!Textures.ensure()) {
			return;
		}
		int n = Textures.SIZE;
		// A quarter turn anticlockwise, easing out: enough to say the press landed without
		// becoming something to sit and watch. Only the gear that was pressed moves.
		long since = System.currentTimeMillis() - gearClickAt;
		boolean mine = Math.abs(x - gearClickX) <= 2 && Math.abs(y - gearClickY) <= 2;
		float spin = 0f;
		if (mine && since >= 0 && since < GEAR_SPIN_MS) {
			float progress = since / (float) GEAR_SPIN_MS;
			float eased = 1f - (1f - progress) * (1f - progress);
			spin = -90f * (1f - eased);
		}
		if (spin != 0f) {
			g.pose().pushMatrix();
			g.pose().rotateAbout(spin * ((float) Math.PI / 180f), x + 6f, y + 6f);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, Textures.GEAR, x, y, 0f, 0f, 12, 12, n, n, n, n,
				lit ? Ui.TEXT : 0x66EDEDF2);
		if (spin != 0f) {
			g.pose().popMatrix();
		}
	}

	/**
	 * Two toggles side by side, each with a gear (opens that item's options). Pass
	 * {@code rLabel == null} for a single left-only cell. Lunar-style coordinate settings.
	 */
	public Row dualToggleGear(
			String lLabel, Supplier<Boolean> lGet, Consumer<Boolean> lSet, Runnable lGear, BooleanSupplier lOpen,
			String rLabel, Supplier<Boolean> rGet, Consumer<Boolean> rSet, Runnable rGear, BooleanSupplier rOpen) {
		return new Row(18, (g, y, mx, my) -> {
			int half = mainW / 2;
			drawPill(g, mainX + 2, y + 3, lGet.get());
			Ui.str(g, lLabel, mainX + 32, y + 5, Ui.TEXT);
			if (lGear != null) {
				int lgx = mainX + half - 16;
				boolean lgHover = mx >= lgx - 2 && mx < lgx + 14 && my >= y && my < y + 18;
				drawGear(g, lgx, y + 3, lOpen.getAsBoolean() || lgHover);
			}
			if (rLabel != null) {
				drawPill(g, mainX + half + 2, y + 3, rGet.get());
				Ui.str(g, rLabel, mainX + half + 32, y + 5, Ui.TEXT);
				int rgx = mainX + mainW - 16;
				boolean rgHover = mx >= rgx - 2 && mx < rgx + 14 && my >= y && my < y + 18;
				drawGear(g, rgx, y + 3, rOpen.getAsBoolean() || rgHover);
			}
		}, (e, y) -> {
			int half = mainW / 2;
			int lgx = mainX + half - 16;
			if (lGear != null && e.x() >= lgx - 2 && e.x() < lgx + 14) {
				gearClicked(lgx, y + 3);
				lGear.run();
				return true;
			}
			if (rLabel != null) {
				int rgx = mainX + mainW - 16;
				if (e.x() >= rgx - 2 && e.x() < rgx + 14) {
					gearClicked(rgx, y + 3);
					rGear.run();
					return true;
				}
			}
			if (e.x() < mainX + half - 18) {
				lSet.accept(!lGet.get());
				return true;
			}
			if (rLabel != null && e.x() >= mainX + half && e.x() < mainX + mainW - 18) {
				rSet.accept(!rGet.get());
				return true;
			}
			return false;
		});
	}

	/** A single toggle with a gear that expands its options, plus a reset icon. */
	public Row toggleGear(String label, Supplier<Boolean> get, Consumer<Boolean> set, Runnable gear,
			BooleanSupplier open, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			drawPill(g, mainX + 2 + ind, y + 3, get.get());
			Ui.str(g, label, mainX + 34 + ind, y + 5, Ui.TEXT);
			// Where the left gear of a paired row sits. Hard against the reset icon it was an
			// arm length from the label it belongs to, with nothing in between.
			int gx = mainX + mainW / 2 - 16;
			boolean gHover = mx >= gx - 2 && mx < gx + 14 && my >= y && my < y + 18 && my >= rowY0 && my <= rowY1;
			drawGear(g, gx, y + 3, open.getAsBoolean() || gHover);
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			int gx = mainX + mainW / 2 - 16;
			if (e.x() >= gx - 2 && e.x() < gx + 14) {
				gearClicked(gx, y + 3);
				gear.run();
				return true;
			}
			if (e.x() < mainX + 34 + ind + Ui.sw(label)) {
				set.accept(!get.get());
				return true;
			}
			return false;
		});
	}

	/** Centered accent action button. */
	public Row button(String label, Runnable onClick) {
		return new Row(24, (g, y, mx, my) -> {
			int w = Math.max(110, Ui.capsW(label, 0.4f) + 24);
			int x = mainX + (mainW - w) / 2;
			boolean hover = mx >= x && mx < x + w && my >= y + 4 && my < y + 22 && my >= rowY0 && my <= rowY1;
			Draw.smoothRounded(g, x, y + 4, w, 18, 4, hover ? Ui.accent() : Ui.accentDim());
			Ui.caps(g, label, x + (w - Ui.capsW(label, 0.4f)) / 2, y + 9, 0xFFFFFFFF, 0.4f);
		}, (e, y) -> {
			int w = Math.max(110, Ui.capsW(label, 0.4f) + 24);
			int x = mainX + (mainW - w) / 2;
			if (e.x() >= x && e.x() < x + w && e.y() >= y + 4 && e.y() < y + 22) {
				onClick.run();
				return true;
			}
			return false;
		});
	}

	/**
	 * Wraps a group of sub-rows (a dropdown's contents) on an inset card with the rows
	 * indented under their parent — the Lunar-style expansion used for every gear dropdown.
	 */
	public void groupCard(List<Row> rows, Consumer<List<Row>> builder) {
		int saved = indent;
		indent = saved + 8;
		List<Row> group = new java.util.ArrayList<>();
		builder.accept(group);
		indent = saved;
		int sum = 8;
		for (Row r : group) {
			sum += r.h();
		}
		int cardH = sum;
		int cardX = mainX + 2 + saved;
		int cardW = mainW - 4 - saved;
		rows.add(new Row(4, (g, y, mx, my) -> Draw.smoothRounded(g, cardX, y + 2, cardW, cardH, 4, 0x1E000000), null));
		rows.addAll(group);
		rows.add(new Row(6, (g, y, mx, my) -> {
		}, null));
	}

	/** Two plain toggles side by side (no gears) — compact paired options. */
	public Row dualToggle(String lLabel, Supplier<Boolean> lGet, Consumer<Boolean> lSet,
			String rLabel, Supplier<Boolean> rGet, Consumer<Boolean> rSet) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			int half = (mainW - 4 - ind) / 2;
			drawPill(g, mainX + 2 + ind, y + 3, lGet.get());
			Ui.str(g, lLabel, mainX + 34 + ind, y + 5, Ui.TEXT);
			if (rLabel != null) {
				drawPill(g, mainX + 2 + ind + half, y + 3, rGet.get());
				Ui.str(g, rLabel, mainX + 34 + ind + half, y + 5, Ui.TEXT);
			}
		}, (e, y) -> {
			int half = (mainW - 4 - ind) / 2;
			if (rLabel != null && e.x() >= mainX + 2 + ind + half) {
				rSet.accept(!rGet.get());
				return true;
			}
			if (e.x() < mainX + 2 + ind + half) {
				lSet.accept(!lGet.get());
				return true;
			}
			return false;
		});
	}

	/** The keybind row currently listening for a key press, or null. */
	protected KeyMapping listeningKey;

	/**
	 * A keybind row bound to a real {@link KeyMapping}, so it stays in sync with Minecraft's
	 * own Controls screen: click to listen, press a key (Escape clears), right-click resets.
	 */
	public Row keybindRow(String label, KeyMapping mapping, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			Ui.str(g, label, mainX + 2 + ind, y + 5, Ui.TEXT);
			boolean listening = listeningKey == mapping;
			String text = listening ? "> ... <" : mapping.getTranslatedKeyMessage().getString();
			int w = Math.max(54, Ui.sw(text) + 14);
			int x = controlRight() - w;
			boolean hover = mx >= x && mx < x + w && my >= y + 2 && my < y + 16 && my >= rowY0 && my <= rowY1;
			Draw.smoothRounded(g, x, y + 2, w, 14, 3, listening ? Ui.accentDim() : (hover ? 0x28FFFFFF : 0x18FFFFFF));
			Ui.str(g, text, x + (w - Ui.sw(text)) / 2, y + 5, listening ? 0xFFFFFFFF : Ui.TEXT);
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			String text = listeningKey == mapping ? "> ... <" : mapping.getTranslatedKeyMessage().getString();
			int w = Math.max(54, Ui.sw(text) + 14);
			int x = controlRight() - w;
			if (e.x() >= x && e.x() < x + w) {
				listeningKey = listeningKey == mapping ? null : mapping;
				return true;
			}
			return false;
		});
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
		if (captureKeybind(e)) {
			return true;
		}
		if (typed != null) {
			if (e.isEscape()) {
				endTyping(false);
				return true;
			}
			if (e.input() == InputConstants.KEY_RETURN || e.input() == InputConstants.KEY_NUMPADENTER) {
				endTyping(true);
				return true;
			}
			if (e.input() == InputConstants.KEY_BACKSPACE) {
				typed = typed.isEmpty() ? typed : typed.substring(0, typed.length() - 1);
				return true;
			}
			// Everything else is swallowed: Escape would otherwise close the whole panel out
			// from under a half-typed number.
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
		if (typed != null) {
			char c = (char) e.codepoint();
			// Only what can be part of a number, and one point or sign at most.
			if (Character.isDigit(c) || (c == '.' && !typed.contains("."))
					|| (c == '-' && typed.isEmpty())) {
				typed = typed + c;
			}
			return true;
		}
		return super.charTyped(e);
	}

	/** Applies a pending keybind capture (Escape clears the binding). */
	/** The raw-key row listening, held by the token it was given, and where to put the answer. */
	protected Object listeningRaw;
	private Consumer<String> listeningRawSet;

	/**
	 * A row that watches a key on its own rather than through a {@link KeyMapping}.
	 *
	 * <p>For picking a key that need not be bound to anything — the keystrokes module shows keys
	 * you choose, and choosing one should not mean rebinding whatever the game had on it. The key
	 * is stored by name, the same string vanilla saves its own binds as, so it survives a restart
	 * and reads plainly in the config.
	 */
	public Row rawKeybindRow(String label, Object token, Supplier<String> get, Consumer<String> set,
			Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			Ui.str(g, label, mainX + 2 + ind, y + 5, Ui.TEXT);
			boolean listening = listeningRaw == token;
			String text = listening ? "> ... <" : rawKeyName(get.get());
			int w = Math.max(54, Ui.sw(text) + 14);
			int x = controlRight() - w;
			boolean hover = mx >= x && mx < x + w && my >= y + 2 && my < y + 16 && my >= rowY0 && my <= rowY1;
			Draw.smoothRounded(g, x, y + 2, w, 14, 3,
					listening ? Ui.accentDim() : (hover ? 0x28FFFFFF : 0x18FFFFFF));
			Ui.str(g, text, x + (w - Ui.sw(text)) / 2, y + 5, listening ? 0xFFFFFFFF : Ui.TEXT);
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			String text = listeningRaw == token ? "> ... <" : rawKeyName(get.get());
			int w = Math.max(54, Ui.sw(text) + 14);
			int x = controlRight() - w;
			if (e.x() >= x && e.x() < x + w) {
				boolean already = listeningRaw == token;
				listeningRaw = already ? null : token;
				listeningRawSet = already ? null : set;
				return true;
			}
			return false;
		});
	}

	/** What a stored key name reads as on screen, and what an empty one reads as. */
	public static String rawKeyName(String stored) {
		if (stored == null || stored.isEmpty()) {
			return "NONE";
		}
		try {
			return com.mojang.blaze3d.platform.InputConstants.getKey(stored).getDisplayName().getString();
		} catch (RuntimeException ex) {
			return "NONE";
		}
	}

	/**
	 * A mouse button offered to a listening raw-key row.
	 *
	 * <p>Mouse buttons are keys you can watch, and they never arrive through {@code keyPressed}.
	 * Escape still clears, the same as for a key — clicking again cannot mean "never mind", since
	 * the click is the answer.
	 */
	@Override
	protected boolean captureRawMouse(MouseButtonEvent e) {
		if (listeningRaw == null) {
			return false;
		}
		listeningRawSet.accept(com.mojang.blaze3d.platform.InputConstants.Type.MOUSE
				.getOrCreate(e.button()).getName());
		listeningRaw = null;
		listeningRawSet = null;
		return true;
	}

	protected boolean captureKeybind(net.minecraft.client.input.KeyEvent e) {
		if (listeningRaw != null) {
			listeningRawSet.accept(e.isEscape() ? ""
					: com.mojang.blaze3d.platform.InputConstants.getKey(e).getName());
			listeningRaw = null;
			listeningRawSet = null;
			return true;
		}
		if (listeningKey == null) {
			return false;
		}
		listeningKey.setKey(e.isEscape()
				? com.mojang.blaze3d.platform.InputConstants.UNKNOWN
				: com.mojang.blaze3d.platform.InputConstants.getKey(e));
		KeyMapping.resetMapping();
		Minecraft.getInstance().options.save();
		listeningKey = null;
		return true;
	}

	// ---- right-aligned icon slots ----------------------------------------------------------
	// A row's trailing icons (pencil / eye / gear / bin) sit on a 16px pitch running right to
	// left. Drawing and hit-testing used to each hardcode these offsets, and every time the two
	// drifted an icon rendered at the wrong height or stopped taking clicks. Both now come from
	// here, so they cannot disagree.

	/** Slot indices for the icon order used by list rows: gear, then eye, then pencil. */
	protected static final int SLOT_GEAR = 0;
	protected static final int SLOT_EYE = 1;
	protected static final int SLOT_PENCIL = 2;
	protected static final int SLOT_TRASH = 3;

	/** Left edge of icon slot {@code index}, counting 0 from the right-hand end of the row. */
	public int iconSlotX(int index) {
		return controlRight() - 14 - index * 16;
	}

	/** True when {@code mx} falls in icon slot {@code index} (a touch wider than the glyph). */
	public boolean iconSlotHit(double mx, int index) {
		int x = iconSlotX(index);
		return mx >= x - 2 && mx < x + 12;
	}

	/** Non-interactive label + right-aligned dim value (waypoint coordinates and the like). */
	public Row infoRow(String label, Supplier<String> value) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			Ui.str(g, label, mainX + 2 + ind, y + 5, Ui.TEXT);
			String v = value.get();
			Ui.str(g, v, controlRight() - Ui.sw(v), y + 5, Ui.TEXT_DIM);
		}, null);
	}

	/** Pixel setter for {@link #pixelGrid}: paint cell (x, y) to {@code on}. */
	public interface PixelSet {
		void set(int x, int y, boolean on);
	}

	/**
	 * A paintable n×n pixel grid (crosshair designer): checkerboard backdrop, white = set.
	 * Hold the mouse button to paint; the stroke paints the opposite of the first cell hit.
	 * Cell size adapts to the panel so large grids still fit.
	 */
	public Row pixelGrid(int n, java.util.function.BiPredicate<Integer, Integer> get, PixelSet set) {
		// Fit the whole canvas into the visible row area: width AND view height constrain cells.
		int availH = Math.max(40, rowY1 - rowY0 - 14);
		int cell = Math.max(2, Math.min(8, Math.min((mainW - 12) / n, availH / n)));
		int gridW = n * cell;
		int gap = cell >= 4 ? 1 : 0;
		return new Row(gridW + 10, (g, y, mx, my) -> {
			int gx = mainX + (mainW - gridW) / 2;
			int gy = y + 5;
			for (int px = 0; px < n; px++) {
				for (int py2 = 0; py2 < n; py2++) {
					int cx = gx + px * cell;
					int cy = gy + py2 * cell;
					boolean on = get.test(px, py2);
					boolean hover = mx >= cx && mx < cx + cell && my >= cy && my < cy + cell
							&& my >= rowY0 && my <= rowY1;
					int color;
					if (on) {
						color = 0xFFFFFFFF;
					} else if (hover) {
						color = 0x40FFFFFF;
					} else {
						// Checkerboard with the center row/column marked for symmetry reference.
						boolean centerLine = px == n / 2 || py2 == n / 2;
						color = (px + py2) % 2 == 0
								? (centerLine ? 0x28FFFFFF : 0x16FFFFFF)
								: (centerLine ? 0x20FFFFFF : 0x0CFFFFFF);
					}
					g.fill(cx, cy, cx + cell - gap, cy + cell - gap, color);
				}
			}
		}, (e, y) -> {
			int gx = mainX + (mainW - gridW) / 2;
			int gy = y + 5;
			if (e.x() < gx || e.y() < gy) {
				return false;
			}
			int px = (int) ((e.x() - gx) / cell);
			int py2 = (int) ((e.y() - gy) / cell);
			if (px >= 0 && px < n && py2 >= 0 && py2 < n) {
				boolean paint = !get.test(px, py2);
				set.set(px, py2, paint);
				// Keep painting the same value while the button is held.
				dragApply = (dx, dy) -> {
					int qx = (int) ((dx - gx) / cell);
					int qy = (int) ((dy - gy) / cell);
					if (dx >= gx && dy >= gy && qx >= 0 && qx < n && qy >= 0 && qy < n) {
						set.set(qx, qy, paint);
					}
				};
				return true;
			}
			return false;
		});
	}

	/** ‹ value › cycler row for enums/options, with a reset icon. */
	public Row cycleRow(String label, Supplier<String> value, Runnable prev, Runnable next, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			Ui.str(g, label, mainX + 2 + ind, y + 5, Ui.TEXT);
			String name = value.get();
			int x0 = controlRight() - 104;
			Ui.str(g, "‹", x0, y + 5, Ui.TEXT);
			Ui.str(g, name, x0 + 12 + (76 - Ui.sw(name)) / 2, y + 5, Ui.TEXT);
			Ui.str(g, "›", x0 + 94, y + 5, Ui.TEXT);
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			if (e.x() >= controlRight() - 108 && e.x() < controlRight() - 50) {
				prev.run();
				return true;
			}
			if (e.x() >= controlRight() - 50 && e.x() <= controlRight()) {
				next.run();
				return true;
			}
			return false;
		});
	}

	public void addColorRows(List<Row> rows, String label, Supplier<ModuleSettings.ColorSpec> spec,
			Runnable reset) {
		rows.add(colorRow(label, spec, reset));
		if (expandedSpec != null && expandedSpec == spec.get()) {
			rows.add(pickerRow(spec));
		}
	}

	/**
	 * Two color pickers side by side. Clicking either swatch expands the shared picker below
	 * the pair, so a compact editor keeps both colors on one line.
	 */
	public void addDualColorRows(List<Row> rows, String lLabel, Supplier<ModuleSettings.ColorSpec> lSpec,
			Runnable lReset, String rLabel, Supplier<ModuleSettings.ColorSpec> rSpec, Runnable rReset) {
		int ind = indent;
		rows.add(new Row(18, (g, y, mx, my) -> {
			int half = (mainW - 4 - ind) / 2;
			drawColorCell(g, lLabel, lSpec.get(), mainX + 2 + ind, y, half);
			drawColorCell(g, rLabel, rSpec.get(), mainX + 2 + ind + half, y, half);
		}, (e, y) -> {
			int half = (mainW - 4 - ind) / 2;
			boolean right = e.x() >= mainX + 2 + ind + half;
			ModuleSettings.ColorSpec spec = right ? rSpec.get() : lSpec.get();
			expandedSpec = expandedSpec == spec ? null : spec;
			editB = false;
			if (expandedSpec != null) {
				syncHexBox();
			} else {
				hexBox.visible = false;
			}
			return true;
		}));
		if (expandedSpec != null && (expandedSpec == lSpec.get() || expandedSpec == rSpec.get())) {
			rows.add(pickerRow(expandedSpec == lSpec.get() ? lSpec : rSpec));
		}
	}

	private void drawColorCell(GuiGraphicsExtractor g, String label, ModuleSettings.ColorSpec spec, int x, int y,
			int width) {
		boolean open = expandedSpec == spec;
		Draw.smoothRoundedBordered(g, x, y + 3, 12, 12, 3, 0xFF000000 | spec.colorAt(0, 1),
				open ? Ui.accent() : 0x2AFFFFFF, 1);
		Ui.str(g, label, x + 18, y + 5, open ? Ui.TEXT : Ui.TEXT_DIM);
	}

	private Row colorRow(String label, Supplier<ModuleSettings.ColorSpec> specGet, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			ModuleSettings.ColorSpec spec = specGet.get();
			Ui.str(g, label, mainX + 2 + ind, y + 5, Ui.TEXT);
			boolean open = expandedSpec == spec;
			Draw.smoothRoundedBordered(g, controlRight() - 96, y + 3, 12, 12, 3,
					0xFF000000 | spec.colorAt(0, 1), open ? Ui.accent() : 0x2AFFFFFF, 1);
			Ui.str(g, editB && open ? spec.b : spec.a, controlRight() - 78, y + 5, Ui.TEXT_DIM);
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				if (expandedSpec == specGet.get()) {
					syncHexBox();
				}
				return true;
			}
			if (e.x() >= controlRight() - 100) {
				ModuleSettings.ColorSpec spec = specGet.get();
				expandedSpec = expandedSpec == spec ? null : spec;
				editB = false;
				if (expandedSpec != null) {
					syncHexBox();
				} else {
					hexBox.visible = false;
				}
				return true;
			}
			return false;
		});
	}

	private Row pickerRow(Supplier<ModuleSettings.ColorSpec> specGet) {
		int ind = indent;
		return new Row(104, (g, y, mx, my) -> {
			ModuleSettings.ColorSpec spec = specGet.get();
			Draw.smoothRounded(g, mainX + 2 + ind, y + 2, mainW - 8 - ind, 100, 4, 0x22000000);
			Rect sv = svRect(y, ind);
			Rect hue = hueRect(y, ind);

			// SV square: white→hue horizontally, →black vertically
			int hueBase = Colors.hsv(pickerHue, 1f, 1f);
			for (int i = 0; i < 56; i++) {
				int top = Colors.lerp(0xFFFFFFFF, hueBase, i / 55f);
				g.fillGradient((int) sv.x() + i, (int) sv.y(), (int) sv.x() + i + 1,
						(int) (sv.y() + sv.h()), top, 0xFF000000);
			}
			float[] cur = Colors.rgbToHsv(activeRgb());
			int cx = (int) (sv.x() + cur[1] * 55);
			int cy = (int) (sv.y() + (1 - cur[2]) * 55);
			g.outline(cx - 1, cy - 1, 4, 4, 0xFFFFFFFF);

			for (int j = 0; j < 56; j++) {
				g.fill((int) hue.x(), (int) hue.y() + j, (int) (hue.x() + hue.w()), (int) hue.y() + j + 1,
						Colors.hsv(j / 55f, 1f, 1f));
			}
			g.outline((int) hue.x() - 1, (int) (hue.y() + pickerHue * 55) - 1, 10, 3, 0xFFFFFFFF);

			// Opacity of the active color (the alpha byte of its hex)
			if (!opaqueOnly.contains(specGet.get())) {
				int alpha = (activeRgb() >>> 24) & 0xFF;
				Ui.str(g, "Opacity", mainX + 10 + ind, y + 88, Ui.TEXT_DIM);
				drawSlider(g, opacityTrack(y, ind), alpha / 255f,
						Math.round(alpha / 255f * 100f) + "%");
			}

			// Right column flows top-down so no mode leaves gaps or overlaps.
			int x0 = mainX + 92 + ind;
			int[] lay = pickerColumnLayout(spec, y);
			Ui.str(g, "‹", x0, lay[0], Ui.TEXT);
			String mode = spec.mode.label();
			Ui.str(g, mode, x0 + 12 + (72 - Ui.sw(mode)) / 2, lay[0], Ui.TEXT);
			Ui.str(g, "›", x0 + 90, lay[0], Ui.TEXT);

			if (lay[1] >= 0) {
				Ui.str(g, "Type", x0, lay[1], Ui.TEXT_DIM);
				String type = spec.chromaType == ModuleSettings.ColorSpec.ChromaType.SHIFT ? "Shift" : "Wave";
				Ui.str(g, "‹ " + type + " ›", x0 + 34, lay[1], Ui.TEXT);
			}
			if (lay[2] >= 0) {
				Ui.str(g, "Speed", x0, lay[2], Ui.TEXT_DIM);
				drawSlider(g, miniTrack(x0, lay[2]), (spec.speed - 0.1f) / 4.9f, null);
				Ui.str(g, String.format("%.1f", spec.speed), x0 + 34, lay[2], Ui.TEXT_DIM);
			}
			if (lay[3] >= 0) {
				Ui.str(g, "Spread", x0, lay[3], Ui.TEXT_DIM);
				drawSlider(g, miniTrack(x0, lay[3]), (spec.spread - 0.1f) / 1.9f, null);
				Ui.str(g, String.format("%.1f", spec.spread), x0 + 34, lay[3], Ui.TEXT_DIM);
			}
			if (lay[4] >= 0) {
				Draw.smoothRoundedBordered(g, x0, lay[4], 14, 14, 3, Colors.parse(spec.a, 0xFFFFFFFF),
						editB ? 0x2AFFFFFF : Ui.accent(), 1);
				Draw.smoothRoundedBordered(g, x0 + 20, lay[4], 14, 14, 3, Colors.parse(spec.b, 0xFFF35D12),
						editB ? Ui.accent() : 0x2AFFFFFF, 1);
				Ui.str(g, editB ? "Editing B" : "Editing A", x0 + 42, lay[4] + 3, Ui.TEXT_DIM);
			}
			hexBox.moveTo(x0, lay[5]);
			hexBox.visible = true;
		}, (e, y) -> {
			ModuleSettings.ColorSpec spec = specGet.get();
			Rect sv = svRect(y, ind);
			Rect hue = hueRect(y, ind);
			if (sv.contains(e.x(), e.y())) {
				dragApply = (mx, my) -> {
					float sat = clamp01((float) ((mx - sv.x()) / 55f));
					float val = 1f - clamp01((float) ((my - sv.y()) / 55f));
					writeActive(Colors.hsv(pickerHue, sat, val));
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			if (grow(hue).contains(e.x(), e.y())) {
				dragApply = (mx, my) -> {
					pickerHue = clamp01((float) ((my - hue.y()) / 55f));
					float[] cur = Colors.rgbToHsv(activeRgb());
					writeActive(Colors.hsv(pickerHue, Math.max(cur[1], 0.02f), cur[2]));
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			if (!opaqueOnly.contains(specGet.get()) && grow(opacityTrack(y, ind)).contains(e.x(), e.y())) {
				Rect t = opacityTrack(y, ind);
				dragApply = (mx, my) -> {
					float frac = clamp01((float) ((mx - t.x()) / t.w()));
					writeAlpha(Math.round(frac * 255f));
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			int x0 = mainX + 92 + ind;
			int[] lay = pickerColumnLayout(spec, y);
			if (e.y() >= lay[0] - 4 && e.y() < lay[0] + 12) {
				if (e.x() >= x0 - 2 && e.x() < x0 + 10) {
					cycleMode(spec, -1);
					return true;
				}
				if (e.x() >= x0 + 84 && e.x() < x0 + 98) {
					cycleMode(spec, 1);
					return true;
				}
			}
			if (lay[1] >= 0 && e.y() >= lay[1] - 3 && e.y() < lay[1] + 12 && e.x() >= x0 + 30) {
				spec.chromaType = spec.chromaType == ModuleSettings.ColorSpec.ChromaType.SHIFT
						? ModuleSettings.ColorSpec.ChromaType.WAVE
						: ModuleSettings.ColorSpec.ChromaType.SHIFT;
				return true;
			}
			if (lay[2] >= 0 && grow(miniTrack(x0, lay[2])).contains(e.x(), e.y())) {
				Rect t = miniTrack(x0, lay[2]);
				dragApply = (mx, my) -> {
					float frac = clamp01((float) ((mx - t.x()) / t.w()));
					spec.speed = Math.round((0.1f + frac * 4.9f) * 10f) / 10f;
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			if (lay[3] >= 0 && grow(miniTrack(x0, lay[3])).contains(e.x(), e.y())) {
				Rect t = miniTrack(x0, lay[3]);
				dragApply = (mx, my) -> {
					float frac = clamp01((float) ((mx - t.x()) / t.w()));
					spec.spread = Math.round((0.1f + frac * 1.9f) * 10f) / 10f;
				};
				dragApply.accept(e.x(), e.y());
				return true;
			}
			if (lay[4] >= 0 && e.y() >= lay[4] - 2 && e.y() < lay[4] + 16) {
				if (e.x() >= x0 && e.x() < x0 + 16) {
					editB = false;
					syncHexBox();
					return true;
				}
				if (e.x() >= x0 + 18 && e.x() < x0 + 36) {
					editB = true;
					syncHexBox();
					return true;
				}
			}
			// Swallow clicks anywhere on the picker card so they don't fall through.
			return e.x() >= mainX && e.x() < mainX + mainW && e.y() >= y && e.y() < y + 104;
		});
	}

	protected static boolean gradient(ModuleSettings.ColorSpec spec) {
		return spec.mode == ModuleSettings.ColorSpec.Mode.GRADIENT
				|| spec.mode == ModuleSettings.ColorSpec.Mode.GRADIENT_WAVE;
	}

	private static boolean speedVisible(ModuleSettings.ColorSpec spec) {
		return spec.mode == ModuleSettings.ColorSpec.Mode.CHROMA
				|| spec.mode == ModuleSettings.ColorSpec.Mode.GRADIENT_WAVE;
	}

	private static boolean spreadVisible(ModuleSettings.ColorSpec spec) {
		return spec.mode == ModuleSettings.ColorSpec.Mode.GRADIENT_WAVE
				|| (spec.mode == ModuleSettings.ColorSpec.Mode.CHROMA
						&& spec.chromaType == ModuleSettings.ColorSpec.ChromaType.WAVE);
	}

	private void cycleMode(ModuleSettings.ColorSpec spec, int dir) {
		ModuleSettings.ColorSpec.Mode[] modes = ModuleSettings.ColorSpec.Mode.values();
		spec.mode = modes[(spec.mode.ordinal() + dir + modes.length) % modes.length];
		// Per-mode spread defaults (user-tuned sweet spots).
		if (spec.mode == ModuleSettings.ColorSpec.Mode.CHROMA) {
			spec.spread = 0.3f;
		} else if (spec.mode == ModuleSettings.ColorSpec.Mode.GRADIENT_WAVE) {
			spec.spread = 0.8f;
		}
		if (!gradient(spec)) {
			editB = false;
		}
		syncHexBox();
	}

	/** {modeY, typeY, speedY, spreadY, swatchY, hexY}; -1 = row absent for this mode. */
	private static int[] pickerColumnLayout(ModuleSettings.ColorSpec spec, int y) {
		int cy = y + 6;
		int modeY = cy;
		cy += 18;
		int typeY = -1;
		int speedY = -1;
		int spreadY = -1;
		int swatchY = -1;
		if (spec.mode == ModuleSettings.ColorSpec.Mode.CHROMA) {
			typeY = cy;
			cy += 16;
		}
		if (speedVisible(spec)) {
			speedY = cy;
			cy += 15;
		}
		if (spreadVisible(spec)) {
			spreadY = cy;
			cy += 15;
		}
		if (gradient(spec)) {
			swatchY = cy;
			cy += 18;
		}
		return new int[]{modeY, typeY, speedY, spreadY, swatchY, cy};
	}

	private static Rect miniTrack(int x0, int rowY) {
		return new Rect(x0 + 60, rowY + 2, 52, 4);
	}

	private Rect svRect(int y, int ind) {
		return new Rect(mainX + 10 + ind, y + 8, 56, 56);
	}

	private Rect hueRect(int y, int ind) {
		return new Rect(mainX + 72 + ind, y + 8, 8, 56);
	}

	private Rect opacityTrack(int y, int ind) {
		return new Rect(mainX + 88 + ind, y + 90, controlRight() - (mainX + 96 + ind), 4);
	}

	protected static Rect grow(Rect r) {
		return new Rect(r.x() - 2, r.y() - 4, r.w() + 4, r.h() + 8);
	}

	// ---- picker color plumbing ----

	private String activeHex() {
		return editB ? expandedSpec.b : expandedSpec.a;
	}

	private int activeRgb() {
		return expandedSpec == null ? 0xFFFFFFFF : Colors.parse(activeHex(), 0xFFFFFFFF);
	}

	/** Writes a new alpha byte into the active slot, keeping its RGB. */
	private void writeAlpha(int alpha) {
		if (expandedSpec == null) {
			return;
		}
		int rgb = Colors.parse(activeHex(), 0xFFFFFFFF) & 0xFFFFFF;
		String v = Colors.format(((alpha & 0xFF) << 24) | rgb);
		if (editB) {
			expandedSpec.b = v;
		} else {
			expandedSpec.a = v;
		}
		syncingHex = true;
		hexBox.setValue(v);
		syncingHex = false;
	}

	/** Writes RGB into the active slot, keeping the slot's existing alpha (opacity survives). */
	private void writeActive(int rgb) {
		if (expandedSpec == null) {
			return;
		}
		int alpha = Colors.parse(activeHex(), 0xFFFFFFFF) & 0xFF000000;
		String v = Colors.format(alpha | (rgb & 0xFFFFFF));
		if (editB) {
			expandedSpec.b = v;
		} else {
			expandedSpec.a = v;
		}
		syncingHex = true;
		hexBox.setValue(v);
		syncingHex = false;
	}

	private void syncHexBox() {
		if (expandedSpec == null) {
			return;
		}
		syncingHex = true;
		hexBox.setValue(activeHex());
		syncingHex = false;
		float[] f = Colors.rgbToHsv(activeRgb());
		if (f[1] > 0.001f) {
			pickerHue = f[0];
		}
	}

	private void applyHex(String v) {
		if (expandedSpec == null) {
			return;
		}
		// Valid iff parsing is fallback-independent.
		if (Colors.parse(v, 1) != Colors.parse(v, 2)) {
			return;
		}
		int c = Colors.parse(v, 0xFFFFFFFF);
		String t = v.startsWith("#") ? v.substring(1) : v;
		int alpha = t.length() == 8 ? (c & 0xFF000000)
				: (Colors.parse(activeHex(), 0xFFFFFFFF) & 0xFF000000);
		String out = Colors.format(alpha | (c & 0xFFFFFF));
		if (editB) {
			expandedSpec.b = out;
		} else {
			expandedSpec.a = out;
		}
		float[] f = Colors.rgbToHsv(c);
		if (f[1] > 0.001f) {
			pickerHue = f[0];
		}
	}

	protected static float clamp01(float f) {
		return f < 0 ? 0 : (f > 1 ? 1 : f);
	}

	// ---- shared module-appearance rows (module settings AND the SETTINGS module-defaults) ----

	/** Text Shadow + Font, and the Show Background gear group (color/size/blur/rounding/border). */
	protected void addGeneralAppearanceRows(java.util.List<Row> rows, ModuleSettings s, ModuleSettings def,
			Supplier<Boolean> bgExpandedGet, Runnable bgExpandedToggle) {
		addGeneralAppearanceRows(rows, s, def, bgExpandedGet, bgExpandedToggle, true);
	}

	/**
	 * The same, with the background and border left out.
	 *
	 * <p>A module that paints its own — keystrokes gives every key a colour and an outline — has
	 * nothing for a module-wide background to sit on, and offering one is offering a dead switch.
	 * The font and shadow rows still apply, so only the background half goes.
	 */
	protected void addGeneralAppearanceRows(java.util.List<Row> rows, ModuleSettings s, ModuleSettings def,
			Supplier<Boolean> bgExpandedGet, Runnable bgExpandedToggle, boolean withBackground) {
		rows.add(shadowFontRow(s, def));
		if (!withBackground) {
			return;
		}
		rows.add(showBackgroundRow(s, def, bgExpandedGet, bgExpandedToggle));
		if (bgExpandedGet.get()) {
			// The background group renders on an inset card (Lunar-style expansion).
			groupCard(rows, group -> {
				addColorRows(group, "Background Color", () -> s.bgColor,
						() -> s.bgColor.copyFrom(def.bgColor));
				group.add(sliderRow("Extra Width", 0f, 40f, 1f, () -> (float) s.bgWidth,
						v -> s.bgWidth = Math.round(v), "%.0f", () -> s.bgWidth = def.bgWidth));
				group.add(sliderRow("Extra Height", 0f, 20f, 1f, () -> (float) s.bgHeight,
						v -> s.bgHeight = Math.round(v), "%.0f", () -> s.bgHeight = def.bgHeight));
				group.add(blurToggle("Background Blur", () -> s.bgBlur, v -> s.bgBlur = v,
						() -> s.bgBlur = def.bgBlur));
				group.add(roundedCornersRow(s, def));
				group.add(borderRow(s, def));
				if (s.border) {
					addColorRows(group, "Border Color", () -> s.borderColor,
							() -> s.borderColor.copyFrom(def.borderColor));
				}
			});
		}
	}

	/** The COLOR section: Split Colors + label/value (or a single text color). */
	protected void addColorAppearanceRows(java.util.List<Row> rows, ModuleSettings s, ModuleSettings def) {
		addColorAppearanceRows(rows, s, def, true);
	}

	/**
	 * The same, optionally without the Split Colors toggle.
	 *
	 * <p>The APPLY TO ALL template leaves the toggle out and always offers both colours. Whether a
	 * module shows one colour or two is that module's own choice, and applying the template does not
	 * touch it — so a switch here would only decide which of the two boxes the template let you
	 * fill in, while every module went on using whichever it was already set to.
	 */
	protected void addColorAppearanceRows(java.util.List<Row> rows, ModuleSettings s, ModuleSettings def,
			boolean withSplitToggle) {
		rows.add(section("Color"));
		if (!withSplitToggle) {
			addColorRows(rows, "Label Color", () -> s.labelColor,
					() -> s.labelColor.copyFrom(def.labelColor));
			addColorRows(rows, "Value Color", () -> s.valueColor,
					() -> s.valueColor.copyFrom(def.valueColor));
			return;
		}
		rows.add(toggle("Split Colors", () -> s.splitColors, v -> s.splitColors = v,
				() -> s.splitColors = def.splitColors));
		if (s.splitColors) {
			addColorRows(rows, "Label Color", () -> s.labelColor,
					() -> s.labelColor.copyFrom(def.labelColor));
			addColorRows(rows, "Value Color", () -> s.valueColor,
					() -> s.valueColor.copyFrom(def.valueColor));
		} else {
			addColorRows(rows, "Text Color", () -> s.labelColor,
					() -> s.labelColor.copyFrom(def.labelColor));
		}
	}

	/** Text Shadow pill on the left, the Font cycler on the right of the same line. */
	private Row shadowFontRow(ModuleSettings s, ModuleSettings def) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			drawPill(g, mainX + 2 + ind, y + 3, s.textShadow);
			Ui.str(g, "Text Shadow", mainX + 34 + ind, y + 5, Ui.TEXT);
			String name = s.font.label();
			int x0 = controlRight() - 104;
			Ui.str(g, "Font", x0 - Ui.sw("Font") - 8, y + 5, Ui.TEXT_DIM);
			Ui.str(g, "‹", x0, y + 5, Ui.TEXT);
			Ui.str(g, name, x0 + 12 + (76 - Ui.sw(name)) / 2, y + 5, Ui.TEXT);
			Ui.str(g, "›", x0 + 94, y + 5, Ui.TEXT);
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				s.textShadow = def.textShadow;
				s.font = def.font;
				return true;
			}
			if (e.x() >= controlRight() - 108 && e.x() <= controlRight()) {
				// Three fonts now, so the arrows step through them rather than flipping a pair;
				// the left half steps back, the right half forward.
				ModuleSettings.FontMode[] fonts = ModuleSettings.FontMode.values();
				int dir = e.x() < controlRight() - 54 ? -1 : 1;
				s.font = fonts[(s.font.ordinal() + dir + fonts.length) % fonts.length];
				return true;
			}
			if (e.x() < mainX + 40 + ind + Ui.sw("Text Shadow")) {
				s.textShadow = !s.textShadow;
				return true;
			}
			return false;
		});
	}

	/** Border toggle with the thickness slider on the same line (visible when on). */
	private Row borderRow(ModuleSettings s, ModuleSettings def) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			drawPill(g, mainX + 2 + ind, y + 3, s.border);
			Ui.str(g, "Border", mainX + 34 + ind, y + 5, Ui.TEXT);
			if (s.border) {
				Rect t = radiusTrack(y);
				String val = Integer.toString(s.borderThickness);
				Ui.str(g, "Border Thickness",
						(int) t.x() - 10 - Ui.sw(val) - 6 - Ui.sw("Border Thickness"),
						(int) t.y() - 3, Ui.TEXT_DIM);
				drawSlider(g, t, (s.borderThickness - 1) / 4f, val);
			}
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				s.border = def.border;
				s.borderThickness = def.borderThickness;
				return true;
			}
			if (s.border) {
				Rect t = radiusTrack(y);
				if (grow(t).contains(e.x(), e.y())) {
					dragApply = (mx, my) -> {
						float frac = clamp01((float) ((mx - t.x()) / t.w()));
						s.borderThickness = 1 + Math.round(frac * 4f);
					};
					dragApply.accept(e.x(), e.y());
					return true;
				}
			}
			if (e.x() < mainX + 40 + ind + Ui.sw("Border")) {
				s.border = !s.border;
				return true;
			}
			return false;
		});
	}

	/** Show Background: pill + label + a gear (right next to the label) that expands the group. */
	private Row showBackgroundRow(ModuleSettings s, ModuleSettings def,
			Supplier<Boolean> bgExpandedGet, Runnable bgExpandedToggle) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			drawPill(g, mainX + 2 + ind, y + 3, s.background);
			Ui.str(g, "Show Background", mainX + 34 + ind, y + 5, Ui.TEXT);
			int gx = mainX + 34 + ind + Ui.sw("Show Background") + 8;
			boolean gHover = mx >= gx && mx < gx + 12 && my >= y + 3 && my < y + 15
					&& my >= rowY0 && my <= rowY1;
			drawGear(g, gx, y + 3, bgExpandedGet.get() || gHover);
			if (gHover) {
				setTooltip("Background options", mx, my);
			}
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				resetBackgroundGroup(s, def);
				return true;
			}
			int gx = mainX + 34 + ind + Ui.sw("Show Background") + 8;
			if (e.x() >= gx - 2 && e.x() < gx + 14) {
				gearClicked(gx, y + 3);
				bgExpandedToggle.run();
				return true;
			}
			if (e.x() < mainX + 34 + ind + Ui.sw("Show Background")) {
				s.background = !s.background;
				return true;
			}
			return false;
		});
	}

	/** Rounded Corners toggle with the radius slider on the same line (visible when on). */
	private Row roundedCornersRow(ModuleSettings s, ModuleSettings def) {
		return roundedRow(() -> s.bgRounded, v -> s.bgRounded = v, () -> s.bgRadius,
				v -> s.bgRadius = v, () -> {
					s.bgRounded = def.bgRounded;
					s.bgRadius = def.bgRadius;
				});
	}

	/**
	 * The same row for anything with its own rounding — custom text boxes carry their formatting
	 * per box rather than in a ModuleSettings, and a second copy of this would be a second set of
	 * hit boxes to keep in step with the drawing.
	 */
	public Row roundedRow(Supplier<Boolean> get, Consumer<Boolean> set, Supplier<Integer> radius,
			Consumer<Integer> setRadius, Runnable reset) {
		int ind = indent;
		return new Row(18, (g, y, mx, my) -> {
			drawPill(g, mainX + 2 + ind, y + 3, get.get());
			Ui.str(g, "Rounded Corners", mainX + 34 + ind, y + 5, Ui.TEXT);
			if (get.get()) {
				drawSlider(g, radiusTrack(y), radius.get() / 12f, Integer.toString(radius.get()));
			}
			drawReset(g, y, 18, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 18)) {
				reset.run();
				return true;
			}
			if (get.get()) {
				Rect t = radiusTrack(y);
				if (grow(t).contains(e.x(), e.y())) {
					dragApply = (mx, my) -> {
						float frac = clamp01((float) ((mx - t.x()) / t.w()));
						setRadius.accept(Math.round(frac * 12f));
					};
					dragApply.accept(e.x(), e.y());
					return true;
				}
			}
			if (e.x() < mainX + 40 + ind + Ui.sw("Rounded Corners")) {
				set.accept(!get.get());
				return true;
			}
			return false;
		});
	}

	private Rect radiusTrack(int y) {
		return new Rect(controlRight() - 70, y + 6, 64, 4);
	}

	/** The Show Background reset clears the whole background group. */
	protected void resetBackgroundGroup(ModuleSettings s, ModuleSettings def) {
		s.background = def.background;
		s.bgBlur = def.bgBlur;
		s.bgColor.copyFrom(def.bgColor);
		s.bgRounded = def.bgRounded;
		s.bgRadius = def.bgRadius;
		s.border = def.border;
		s.borderColor.copyFrom(def.borderColor);
		collapsePicker();
	}

	// ---- shared drawing ----

	protected void drawPill(GuiGraphicsExtractor g, int x, int y, boolean on) {
		Draw.smoothRounded(g, x, y, 26, 13, 6, on ? Ui.accent() : 0x30FFFFFF);
		int knob = 9;
		int kx = on ? x + 26 - knob - 2 : x + 2;
		Draw.smoothRounded(g, kx, y + 2, knob, knob, 4, 0xFFF4F4F6);
		if (on) {
			MenuFont.draw(g, "ON", x + 4.5f, y + 3.5f, 0xFFFFFFFF, 5f, false, 0.3f);
		} else {
			MenuFont.draw(g, "OFF", x + 12.5f, y + 3.5f, 0xFFD86A6A, 5f, false, 0.2f);
		}
	}

	/**
	 * The pill for a switch that cannot be used: off, and greyed rather than red, so it reads as
	 * "not available here" rather than as something that is merely turned off.
	 */
	protected void drawPillDim(GuiGraphicsExtractor g, int x, int y) {
		Draw.smoothRounded(g, x, y, 26, 13, 6, 0x18FFFFFF);
		Draw.smoothRounded(g, x + 2, y + 2, 9, 9, 4, 0x60F4F4F6);
		MenuFont.draw(g, "N/A", x + 12.5f, y + 3.5f, 0x80EDEDF2, 5f, false, 0.2f);
	}

	/**
	 * The slider whose number is being typed into, named by where its track was drawn.
	 *
	 * <p>The rows are rebuilt every frame and have no identity of their own, so the position is
	 * what there is to go on — and it is enough, since only one number can be typed at a time.
	 */
	private int typingAt = Integer.MIN_VALUE;
	private String typed;
	private Consumer<Float> typedSet;
	private float typedMin, typedMax, typedStep;

	/** True while a number is being typed, so the caller can leave the drag alone. */
	protected boolean typingValue() {
		return typed != null;
	}

	/** Just the number out of a formatted value: "1.50s" types on as "1.50". */
	private static String numeric(String formatted) {
		StringBuilder out = new StringBuilder();
		for (char c : formatted.toCharArray()) {
			if (Character.isDigit(c) || c == '.' || (c == '-' && out.length() == 0)) {
				out.append(c);
			}
		}
		return out.toString();
	}

	private void beginTyping(int at, float min, float max, float step, Consumer<Float> set,
			String current) {
		typingAt = at;
		typed = current;
		typedSet = set;
		typedMin = min;
		typedMax = max;
		typedStep = step;
	}

	/** Commits what was typed, if it is a number at all, and stops typing either way. */
	protected void endTyping(boolean keep) {
		if (typed != null && keep && typedSet != null) {
			try {
				float v = Float.parseFloat(typed.trim());
				v = Math.max(typedMin, Math.min(typedMax, v));
				// Through the same clamp and step the drag uses, so a typed value cannot be
				// something the slider could never have reached.
				typedSet.accept(typedStep > 0 ? Math.round(v / typedStep) * typedStep : v);
			} catch (NumberFormatException ignored) {
				// Nonsense is dropped rather than guessed at.
			}
		}
		typed = null;
		typedSet = null;
		typingAt = Integer.MIN_VALUE;
	}

	/** The number as drawn: what is typed so far while typing, the value otherwise. */
	private String valueLabel(Rect track, String valueText) {
		if (typed == null || typingAt != (int) track.y()) {
			return valueText;
		}
		// A space rather than nothing on the off beat: the number would jump a pixel wide
		// otherwise, twice a second, which is worse than no caret at all.
		return typed + (System.currentTimeMillis() % 1000 < 500 ? "_" : " ");
	}

	/** The number sits to the left of the track; that is what you click to type into it. */
	private boolean valueHit(MouseButtonEvent e, Rect track, String valueText) {
		if (valueText == null) {
			return false;
		}
		double left = track.x() - Ui.sw(valueText) - 12;
		return e.x() >= left && e.x() < track.x() - 4
				&& e.y() >= track.y() - 6 && e.y() < track.y() + 10;
	}

	protected void drawSlider(GuiGraphicsExtractor g, Rect track, float frac, String valueText) {
		frac = clamp01(frac);
		int x = (int) track.x();
		int y = (int) track.y();
		int w = (int) track.w();
		String shown = valueLabel(track, valueText);
		if (shown != null) {
			Ui.str(g, shown, x - Ui.sw(shown) - 10, y - 3,
					typed != null && typingAt == y ? Ui.TEXT : Ui.TEXT_DIM);
		}
		Draw.smoothRounded(g, x, y, w, 4, 2, Ui.TRACK);
		Draw.smoothRounded(g, x, y, Math.max(2, Math.round(frac * w)), 4, 2, Ui.accent());
		int kx = x + Math.round(frac * w) - 3;
		Draw.smoothRounded(g, kx, y - 2, 7, 8, 3, 0xFFF4F4F6);
	}

	// ---- render + input ----

	@Override
	protected void renderMain(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		renderRowsHeader(g, mouseX, mouseY);
		hexBox.visible = false;
		onRowsRenderStart();

		List<Row> rows = buildRows();
		g.enableScissor(mainX, rowY0, mainX + mainW, rowY1);
		int y = rowY0 - (int) scroll;
		for (Row r : rows) {
			if (y + r.h() >= rowY0 && y <= rowY1) {
				r.draw().draw(g, y, mouseX, mouseY);
			}
			y += r.h();
		}
		g.disableScissor();

		int viewH = rowY1 - rowY0;
		int content = contentHeight(rows);
		if (content > viewH) {
			int barH = Math.max(14, viewH * viewH / content);
			int barY = rowY0 + (int) ((viewH - barH) * (scroll / (content - viewH)));
			Draw.smoothRounded(g, px + pw - 6, barY, 2, barH, 1, 0x50FFFFFF);
		}
	}

	private int contentHeight(List<Row> rows) {
		int h = 0;
		for (Row r : rows) {
			h += r.h();
		}
		return h;
	}

	@Override
	protected boolean mainClicked(MouseButtonEvent e) {
		if (rowsHeaderClicked(e)) {
			return true;
		}
		// Row clicks only count inside the main column (stray clicks in the gutter do nothing).
		// Anything else clicked commits what was typed, before the click is acted on: the row
		// walk below may start typing into another number, and two at once is not a state.
		endTyping(true);
		if (e.y() >= rowY0 && e.y() <= rowY1 && e.x() >= mainX && e.x() < mainX + mainW) {
			List<Row> rows = buildRows();
			int y = rowY0 - (int) scroll;
			for (Row r : rows) {
				if (e.y() >= y && e.y() < y + r.h() && r.click() != null && r.click().click(e, y)) {
					// One place for the click, so every row in every module gets it: a row only
					// reports true when it actually did something with the press.
					clickSound();
					return true;
				}
				y += r.h();
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragApply != null) {
			dragApply.accept(e.x(), e.y());
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	/**
	 * Told when a slider is let go of, for settings that cannot be applied while it moves.
	 *
	 * <p>One listener, not a list: the only thing that needs this is a texture that has to be
	 * loaded again, and doing that once per drag rather than once per pixel of it is the point.
	 */
	private static Runnable dragFinished;

	public static void onDragFinished(Runnable listener) {
		dragFinished = listener;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragApply != null) {
			dragApply = null;
			if (dragFinished != null) {
				dragFinished.run();
			}
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hDelta, double vDelta) {
		if (super.mouseScrolled(mx, my, hDelta, vDelta)) {
			return true;
		}
		if (my >= rowY0 && my <= rowY1) {
			int viewH = rowY1 - rowY0;
			double max = Math.max(0, contentHeight(buildRows()) - viewH);
			scroll = Math.max(0, Math.min(max, scroll - vDelta * 20));
			return true;
		}
		return false;
	}
}
