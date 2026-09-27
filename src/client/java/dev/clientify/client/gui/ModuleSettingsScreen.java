package dev.clientify.client.gui;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.widget.GlassEditBox;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudModule.Rect;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.hud.Templated;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * Per-module settings: Scale, GENERAL (shadow, font, background with color/rounding/border),
 * the text template for Templated modules, and COLOR (split toggle + label/value or a single
 * text color). Every row has a reset icon; color rows expand the shared inline picker.
 */
public class ModuleSettingsScreen extends SettingsRowsScreen {
	private final ModListScreen list;
	private final HudModule module;
	private final ModuleSettings defaults;
	private GlassEditBox templateBox;
	private final List<HudModule.TextField> fields = new ArrayList<>();
	private final List<GlassEditBox> fieldBoxes = new ArrayList<>();
	/** The Show Background gear expands the background sub-settings. */
	private boolean bgExpanded;

	public ModuleSettingsScreen(ModListScreen list, HudModule module) {
		super(Component.literal(module.displayName()), list.editorScreen);
		this.list = list;
		this.module = module;
		this.defaults = module.createDefaultSettings();
	}

	@Override
	protected ModListScreen listScreen() {
		return list;
	}

	@Override
	protected Screen backTarget() {
		return list;
	}

	@Override
	protected void onModsTab() {
		ModuleManager.save();
		minecraft.gui.setScreen(list);
	}

	@Override
	protected void initRows() {
		if (module instanceof Templated t) {
			templateBox = new GlassEditBox(font, 0, 0, 150, 14, Component.literal("Text"), false);
			templateBox.setPlaceholder(t.placeholder());
			templateBox.setValue(t.template());
			templateBox.setResponder(t::setTemplate);
			templateBox.visible = false;
			addRenderableWidget(templateBox);
		} else {
			templateBox = null;
		}
		refreshTextFields();
	}

	/** (Re)builds the text-field pool; call after a module adds/removes textFields() entries. */
	public void refreshTextFields() {
		for (GlassEditBox box : fieldBoxes) {
			removeWidget(box);
		}
		fields.clear();
		fieldBoxes.clear();
		for (HudModule.TextField f : module.textFields()) {
			fields.add(f);
			GlassEditBox box = new GlassEditBox(font, 0, 0, 150, 14, Component.literal(f.label()), false);
			box.setValue(f.get().get());
			box.setResponder(f.set());
			box.visible = false;
			box.newlineEscape = true;
			fieldBoxes.add(box);
			addRenderableWidget(box);
		}
	}

	@Override
	protected void onRowsRenderStart() {
		if (templateBox != null) {
			templateBox.visible = false;
		}
		for (GlassEditBox box : fieldBoxes) {
			box.visible = false;
		}
	}

	@Override
	protected boolean anyTextFieldFocused() {
		if (super.anyTextFieldFocused() || (templateBox != null && templateBox.isFocused())) {
			return true;
		}
		for (GlassEditBox box : fieldBoxes) {
			if (box.isFocused()) {
				return true;
			}
		}
		return false;
	}

	private ModuleSettings s() {
		return module.settings();
	}

	// ---- accessors for subclasses with a custom layout (waypoints) ----

	protected HudModule module() {
		return module;
	}

	protected ModuleSettings defaults() {
		return defaults;
	}

	protected boolean bgExpandedState() {
		return bgExpanded;
	}

	protected void toggleBgExpanded() {
		bgExpanded = !bgExpanded;
	}

	/** The pooled edit box for text field {@code index}, or null when out of range. */
	protected GlassEditBox fieldBox(int index) {
		return index >= 0 && index < fieldBoxes.size() ? fieldBoxes.get(index) : null;
	}

	/** True when the click landed on the header's reset-all icon. */
	protected boolean resetAllHit(MouseButtonEvent e) {
		return resetAllRect().contains(e.x(), e.y());
	}

	/** Current row indent (custom rows honor grouped insets). */
	protected int indentOf() {
		return indent;
	}

	@Override
	protected List<Row> buildRows() {
		ModuleSettings s = s();
		List<Row> rows = new ArrayList<>();

		rows.add(toggle("Enabled", () -> s.enabled, v -> s.enabled = v, () -> s.enabled = defaults.enabled));
		// Enabled stays the first thing on the page; a screen with a list of its own (item counter)
		// puts that list right under it rather than below every other setting.
		appendTopRows(rows);

		if (module.hasScaleSlider()) {
			rows.add(sliderRow("Scale", module.minScale(), module.maxScale(), 0.05f,
					() -> s.scale, v -> s.scale = v, "%.2f", () -> s.scale = defaults.scale));
		}

		List<Row> custom = new ArrayList<>();
		module.appendSettings(this, custom);
		if (!custom.isEmpty()) {
			// A module that opens with a heading of its own says better what its first rows are
			// than a generic one would, and two headings in a row read as a mistake.
			if (!custom.get(0).isSection()) {
				rows.add(section("General"));
			}
			rows.addAll(custom);
		}

		// Gear-only fields are drawn by their owner, inside its card. The paired layout already
		// knew that; this one listed them anyway, which put a Text section full of boxes under a
		// module whose boxes all belonged somewhere else.
		boolean anyListed = false;
		for (HudModule.TextField f : fields) {
			if (!f.gearOnly()) {
				anyListed = true;
				break;
			}
		}
		if (anyListed) {
			rows.add(section("Text"));
			if (module.pairTextFields()) {
				addPairedFieldRows(rows);
			} else {
				for (int i = 0; i < fields.size(); i++) {
					HudModule.TextField f = fields.get(i);
					if (f.gearOnly()) {
						continue;
					}
					rows.add(fieldRow(f, fieldBoxes.get(i)));
					if (f.gearOpen() != null && f.gearOpen().getAsBoolean()) {
						int index = i;
						groupCard(rows, group -> module.appendTextFieldGear(this, group, index));
					}
				}
			}
		}

		if (module.hasAppearance()) {
			// Called General as well until now, so every module with settings of its own showed
			// the same heading twice. These rows are about how the module LOOKS.
			rows.add(section("Appearance"));
			addGeneralAppearanceRows(rows, s, defaults, () -> bgExpanded, () -> bgExpanded = !bgExpanded,
					module.hasBackgroundSection());
			if (templateBox != null && module instanceof Templated t) {
				rows.add(templateRow(t));
			}
			if (module.hasColorSection()) {
				addColorAppearanceRows(rows, s, defaults);
			}
		} else if (templateBox != null && module instanceof Templated t) {
			rows.add(templateRow(t));
		}
		return rows;
	}

	/** Rows a subclass wants directly under the Enabled switch. Default: none. */
	protected void appendTopRows(List<Row> rows) {
	}

	/**
	 * Compact list layout: two entries per row as "1. [icon] [name box] [gear] [bin]", with an
	 * expanded entry's option card following its row.
	 */
	private void addPairedFieldRows(List<Row> rows) {
		// Only list-level fields get rows; gear-only fields belong to their owner's card.
		List<Integer> listed = new ArrayList<>();
		for (int i = 0; i < fields.size(); i++) {
			if (!fields.get(i).gearOnly()) {
				listed.add(i);
			}
		}
		for (int i = 0; i < listed.size(); i += 2) {
			int left = listed.get(i);
			int right = i + 1 < listed.size() ? listed.get(i + 1) : -1;
			rows.add(pairedFieldRow(left, right, i / 2 * 2));
			for (int k : right < 0 ? List.of(left) : List.of(left, right)) {
				HudModule.TextField f = fields.get(k);
				if (f.gearOpen() != null && f.gearOpen().getAsBoolean()) {
					groupCard(rows, group -> module.appendTextFieldGear(this, group, k));
				}
			}
		}
	}

	/** A row that positions a gear-only text field's box (used inside gear cards). */
	public Row textFieldRow(int index) {
		if (index < 0 || index >= fields.size()) {
			return new Row(0, (g, y, mx, my) -> {
			}, null);
		}
		return fieldRow(fields.get(index), fieldBoxes.get(index));
	}

	private Row pairedFieldRow(int leftIndex, int rightIndex, int entryBase) {
		return new Row(20, (g, y, mx, my) -> {
			int half = (mainW - 4) / 2;
			drawCompactField(g, leftIndex, mainX + 2, half, y, mx, my, entryBase + 1);
			if (rightIndex >= 0) {
				drawCompactField(g, rightIndex, mainX + 2 + half, half, y, mx, my, entryBase + 2);
			}
		}, (e, y) -> {
			int half = (mainW - 4) / 2;
			if (rightIndex >= 0 && e.x() >= mainX + 2 + half) {
				return clickCompactField(e, rightIndex, mainX + 2 + half, half, y);
			}
			return clickCompactField(e, leftIndex, mainX + 2, half, y);
		});
	}

	/** One half-width entry cell: index, item icon, name box, gear, bin. */
	private void drawCompactField(GuiGraphicsExtractor g, int index, int x, int width, int y, int mx, int my,
			int entry) {
		HudModule.TextField f = fields.get(index);
		GlassEditBox box = fieldBoxes.get(index);
		Ui.str(g, entry + ".", x, y + 6, Ui.TEXT_DIM);
		int cursor = x + 14;
		var icon = module.textFieldIcon(index);
		if (!icon.isEmpty()) {
			g.pose().pushMatrix();
			g.pose().translate(cursor, y + 3);
			g.pose().scale(0.75f, 0.75f);
			g.item(icon, 0, 0);
			g.pose().popMatrix();
		}
		cursor += 14;
		int tail = (f.gearToggle() != null ? 16 : 0) + (f.onRemove() != null ? 14 : 0);
		int boxW = Math.max(40, width - (cursor - x) - tail - 4);
		box.resizeTo(boxW);
		box.moveTo(cursor, y + 3);
		box.visible = true;
		int right = x + width - 4;
		if (f.onRemove() != null && Textures.ensure()) {
			int n = Textures.SIZE;
			boolean hover = mx >= right - 12 && mx < right && my >= y + 4 && my < y + 16
					&& my >= rowY0 && my <= rowY1;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.TRASH, right - 11, y + 5, 0f, 0f, 10, 10, n, n, n, n,
					hover ? 0xFFFF6666 : 0x66EDEDF2);
			right -= 14;
		}
		if (f.gearToggle() != null) {
			boolean open = f.gearOpen() != null && f.gearOpen().getAsBoolean();
			boolean hover = mx >= right - 14 && mx < right && my >= y + 4 && my < y + 16
					&& my >= rowY0 && my <= rowY1;
			drawGear(g, right - 13, y + 4, open || hover);
		}
	}

	private boolean clickCompactField(MouseButtonEvent e, int index, int x, int width, int y) {
		HudModule.TextField f = fields.get(index);
		int right = x + width - 4;
		if (f.onRemove() != null) {
			if (e.x() >= right - 12 && e.x() < right) {
				f.onRemove().run();
				refreshTextFields();
				return true;
			}
			right -= 14;
		}
		if (f.gearToggle() != null && e.x() >= right - 14 && e.x() < right) {
			gearClicked(right - 13, y + 4);
			f.gearToggle().run();
			return true;
		}
		return false;
	}

	/**
	 * Label on the left, editable text box on the right, with a reset-to-default icon.
	 * Optional bin (remove entry) before the label and gear (per-entry options) by the reset.
	 */
	private Row fieldRow(HudModule.TextField f, GlassEditBox box) {
		boolean hasBin = f.onRemove() != null;
		boolean hasGear = f.gearToggle() != null;
		return new Row(20, (g, y, mx, my) -> {
			int lx = mainX + 2;
			if (hasBin && Textures.ensure()) {
				int n = Textures.SIZE;
				boolean hover = mx >= lx - 2 && mx < lx + 12 && my >= y + 3 && my < y + 17
						&& my >= rowY0 && my <= rowY1;
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.TRASH, lx, y + 5, 0f, 0f, 10, 10, n, n, n, n,
						hover ? 0xFFFF6666 : 0x66EDEDF2);
				lx += 14;
			}
			Ui.str(g, f.label(), lx, y + 6, Ui.TEXT);
			int gearX = resetX() - 18;
			box.moveTo((hasGear ? gearX : resetX()) - 154, y + 3);
			box.visible = true;
			if (hasGear) {
				boolean gHover = mx >= gearX - 2 && mx < gearX + 14 && my >= y && my < y + 20
						&& my >= rowY0 && my <= rowY1;
				drawGear(g, gearX, y + 4, (f.gearOpen() != null && f.gearOpen().getAsBoolean()) || gHover);
			}
			drawReset(g, y, 20, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 20)) {
				f.set().accept(f.defaultValue());
				box.setValue(f.defaultValue());
				return true;
			}
			if (hasGear) {
				int gearX = resetX() - 18;
				if (e.x() >= gearX - 2 && e.x() < gearX + 14) {
					gearClicked(gearX, y + 4);
					f.gearToggle().run();
					return true;
				}
			}
			if (hasBin && e.x() >= mainX && e.x() < mainX + 14) {
				f.onRemove().run();
				refreshTextFields();
				return true;
			}
			return false;
		});
	}

	private Row templateRow(Templated t) {
		return new Row(20, (g, y, mx, my) -> {
			Ui.str(g, "Text", mainX + 2, y + 6, Ui.TEXT);
			templateBox.moveTo(controlRight() - 155, y + 3);
			templateBox.visible = true;
			drawReset(g, y, 20, mx, my);
		}, (e, y) -> {
			if (resetHit(e, y, 20)) {
				t.setTemplate(t.defaultTemplate());
				templateBox.setValue(t.defaultTemplate());
				return true;
			}
			return false;
		});
	}

	// ---- header ----

	private Rect backRect() {
		return new Rect(mainX, py + HEADER_H + 6, 16, 16);
	}

	/** Reset-all for the whole module, right-aligned on the title line (Lunar-style). */
	private Rect resetAllRect() {
		return new Rect(mainX + mainW - RESET_W - 2, py + HEADER_H + 8, RESET_W, RESET_W);
	}

	/**
	 * Reset-position, sat just left of reset-all. Only for modules that have a position at all —
	 * on Zoom or Fullbright it would be a button that cannot do anything.
	 */
	private Rect resetPosRect() {
		if (!module.isHudElement()) {
			return null;
		}
		return new Rect(mainX + mainW - RESET_W * 2 - 6, py + HEADER_H + 8, RESET_W, RESET_W);
	}

	@Override
	protected void renderRowsHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Rect br = backRect();
		if (br.contains(mouseX, mouseY)) {
			Draw.smoothRounded(g, (int) br.x(), (int) br.y(), 16, 16, 8, 0x1EFFFFFF);
		}
		Draw.smoothBorder(g, (int) br.x(), (int) br.y(), 16, 16, 8, 0x2AFFFFFF);
		if (Textures.ensure()) {
			int n = Textures.SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.ARROW_LEFT, (int) br.x() + 4, (int) br.y() + 4,
					0f, 0f, 8, 8, n, n, n, n, Ui.TEXT);
		}
		MenuFont.draw(g, module.displayName().toUpperCase(java.util.Locale.ROOT), mainX + 24, py + HEADER_H + 8,
				Ui.TEXT, MenuFont.Size.TITLE, false, 1.2f);

		Rect ra = resetAllRect();
		boolean raHover = ra.contains(mouseX, mouseY);
		if (Textures.ensure()) {
			int n = Textures.SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.RESET, (int) ra.x(), (int) ra.y(),
					0f, 0f, RESET_W, RESET_W, n, n, n, n, raHover ? Ui.TEXT : 0x66EDEDF2);
		}
		if (raHover) {
			setTooltip("Reset all " + module.displayName() + " settings", mouseX, mouseY);
		}

		Rect rp = resetPosRect();
		if (rp != null) {
			boolean rpHover = rp.contains(mouseX, mouseY);
			if (Textures.ensure()) {
				int n = Textures.SIZE;
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.MOVE, (int) rp.x(), (int) rp.y(),
						0f, 0f, RESET_W, RESET_W, n, n, n, n, rpHover ? Ui.TEXT : 0x66EDEDF2);
			}
			if (rpHover) {
				setTooltip("Move " + module.displayName() + " back to its default position", mouseX, mouseY);
			}
		}

		g.fill(mainX, py + HEADER_H + 26, mainX + mainW, py + HEADER_H + 27, Ui.HAIRLINE);
		Ui.str(g, module.description(), mainX + 2, py + HEADER_H + 31, Ui.TEXT_DIM);
	}

	@Override
	protected boolean rowsHeaderClicked(MouseButtonEvent e) {
		if (backRect().contains(e.x(), e.y())) {
			ModuleManager.save();
			minecraft.gui.setScreen(list);
			return true;
		}
		Rect rp = resetPosRect();
		if (rp != null && rp.contains(e.x(), e.y())) {
			module.settings().applyPositionFrom(module.createDefaultSettings());
			ModuleManager.save();
			return true;
		}
		if (resetAllRect().contains(e.x(), e.y())) {
			module.setSettings(module.createDefaultSettings());
			if (module instanceof Templated t && templateBox != null) {
				templateBox.setValue(t.template());
			}
			collapsePicker();
			ModuleManager.save();
			return true;
		}
		return false;
	}
}
