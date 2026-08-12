package dev.clientify.client.gui;

import dev.clientify.client.gui.widget.GlassEditBox;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.modules.ItemCounterModule;
import dev.clientify.client.modules.WaypointsModule;
import dev.clientify.client.modules.WaypointsModule.IconSuggestion;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Item Counter settings, opening on its list the way the waypoints screen does rather than on a
 * form: a search box, the matches under it as you type, then the items being counted. The search
 * sits in the page instead of a popup — picking items is the main thing you come here to do, so it
 * should not need a dialog opened first.
 */
public class ItemCounterSettingsScreen extends ModuleSettingsScreen {
	private static final int MAX_SUGGESTIONS = 6;

	private final ItemCounterModule counter;
	private GlassEditBox searchBox;
	private List<IconSuggestion> suggestions = List.of();
	/** Which counted item has its options card open, or -1. */
	private int expanded = -1;

	public ItemCounterSettingsScreen(ModListScreen list, ItemCounterModule module) {
		super(list, module);
		this.counter = module;
	}

	private ItemCounterModule.Settings s() {
		return (ItemCounterModule.Settings) counter.settings();
	}

	@Override
	protected void initRows() {
		super.initRows();
		searchBox = new GlassEditBox(font, 0, 0, 180, 16, Component.literal("Search"), true);
		searchBox.setPlaceholder("diamond, golden apple…");
		searchBox.setResponder(q -> suggestions = WaypointsModule.suggestIcons(q, 40));
		searchBox.visible = false;
		addRenderableWidget(searchBox);
	}

	@Override
	protected void onRowsRenderStart() {
		super.onRowsRenderStart();
		searchBox.visible = false;
	}

	@Override
	protected boolean anyTextFieldFocused() {
		return super.anyTextFieldFocused() || searchBox.isFocused();
	}

	@Override
	protected void appendTopRows(List<Row> rows) {
		rows.add(section("Items"));
		rows.add(searchRow());
		int shown = Math.min(MAX_SUGGESTIONS, suggestions.size());
		for (int i = 0; i < shown; i++) {
			rows.add(suggestionRow(suggestions.get(i)));
		}
		List<ItemCounterModule.Entry> entries = s().entries;
		if (entries.isEmpty() && shown == 0) {
			rows.add(hintRow("Search above to start counting an item."));
		}
		for (int i = 0; i < entries.size(); i++) {
			rows.add(trackedRow(i));
			if (expanded == i) {
				int index = i;
				groupCard(rows, group -> counter.appendEntryRows(this, group, index));
			}
		}
	}

	/** The always-present search field; typing refreshes the suggestions under it. */
	private Row searchRow() {
		int ind = indentOf();
		return new Row(22, (g, y, mx, my) -> {
			int x = mainX + 2 + ind;
			searchBox.resizeTo(Math.min(200, controlRight() - x));
			searchBox.moveTo(x, y + 3);
			searchBox.visible = true;
		}, (e, y) -> false); // the widget takes its own clicks
	}

	private Row hintRow(String text) {
		int ind = indentOf();
		return new Row(16, (g, y, mx, my) -> Ui.str(g, text, mainX + 4 + ind, y + 4, Ui.TEXT_DIM), null);
	}

	/** A search hit: click anywhere on it to start counting that item. */
	private Row suggestionRow(IconSuggestion hit) {
		int ind = indentOf();
		return new Row(18, (g, y, mx, my) -> {
			int x = mainX + 4 + ind;
			boolean hover = mx >= mainX && mx < controlRight() && my >= y && my < y + 18
					&& my >= rowY0 && my <= rowY1;
			if (hover) {
				Draw.smoothRounded(g, mainX + 2, y, mainW - 4, 18, 3, 0x1EFFFFFF);
			}
			drawStack(g, hit.stack(), x, y + 1);
			Ui.str(g, hit.label(), x + 20, y + 5, hover ? Ui.TEXT : Ui.TEXT_DIM);
			if (hover) {
				Ui.caps(g, "Add", controlRight() - Ui.capsW("Add", 0.3f) - 2, y + 6, Ui.accent(), 0.3f);
			}
		}, (e, y) -> {
			if (e.x() >= mainX && e.x() < controlRight()) {
				List<ItemCounterModule.Entry> entries = s().entries;
				boolean known = entries.stream().anyMatch(en -> en.item.equals(hit.value()));
				if (!known) {
					// Stagger new chips so a second one does not land exactly on the first.
					entries.add(new ItemCounterModule.Entry(hit.value(),
							5 + entries.size() * 6, 5 + entries.size() * 20));
				}
				searchBox.setValue("");
				suggestions = List.of();
				setFocused(null);
				ModuleManager.save();
				return true;
			}
			return false;
		});
	}

	/** One counted item: position, icon, name, a gear for its own settings and a bin. */
	private Row trackedRow(int index) {
		int ind = indentOf();
		return new Row(18, (g, y, mx, my) -> {
			List<ItemCounterModule.Entry> entries = s().entries;
			if (index >= entries.size()) {
				return;
			}
			ItemCounterModule.Entry entry = entries.get(index);
			ItemStack stack = WaypointsModule.iconStack(entry.item);
			int x = mainX + 2 + ind;
			Ui.str(g, (index + 1) + ".", x, y + 5, Ui.TEXT_DIM);
			drawStack(g, stack, x + 14, y + 1);
			String label = stack.isEmpty() ? entry.item + " (unknown)" : stack.getHoverName().getString();
			Ui.str(g, label, x + 34, y + 5, stack.isEmpty() ? 0xFFD86A6A : Ui.TEXT);
			boolean overGear = iconSlotHit(mx, SLOT_EYE) && my >= y && my < y + 18;
			drawGear(g, iconSlotX(SLOT_EYE), y + 3, expanded == index || overGear);
			if (Textures.ensure()) {
				int n = Textures.SIZE;
				boolean overBin = iconSlotHit(mx, SLOT_GEAR) && my >= y && my < y + 18;
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.TRASH, iconSlotX(SLOT_GEAR), y + 4,
						0f, 0f, 10, 10, n, n, n, n, overBin ? 0xFFD86A6A : Ui.TEXT_DIM);
			}
		}, (e, y) -> {
			if (iconSlotHit(e.x(), SLOT_GEAR)) {
				if (index < s().entries.size()) {
					s().entries.remove(index);
					if (expanded == index) {
						expanded = -1;
					} else if (expanded > index) {
						expanded--;
					}
					collapsePicker();
					ModuleManager.save();
				}
				return true;
			}
			if (iconSlotHit(e.x(), SLOT_EYE)) {
				gearClicked(iconSlotX(SLOT_EYE), y + 3);
				expanded = expanded == index ? -1 : index;
				collapsePicker();
				return true;
			}
			return false;
		});
	}

	private void drawStack(net.minecraft.client.gui.GuiGraphics g, ItemStack stack, int x, int y) {
		if (stack.isEmpty()) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.9f, 0.9f);
		g.renderItem(stack, 0, 0);
		g.pose().popMatrix();
	}
}
