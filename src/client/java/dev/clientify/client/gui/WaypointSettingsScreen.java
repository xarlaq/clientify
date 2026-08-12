package dev.clientify.client.gui;

import dev.clientify.client.config.ClientifyConfig;
import dev.clientify.client.gui.widget.GlassEditBox;
import dev.clientify.client.hud.HudModule.Rect;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.modules.WaypointsModule;
import dev.clientify.client.util.WaypointShare;
import dev.clientify.client.modules.WaypointsModule.IconSuggestion;
import dev.clientify.client.modules.WaypointsModule.Waypoint;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Waypoints settings, laid out as a browser rather than a settings form: the sidebar lists
 * the dimensions and groups (waypoints are world data shared by every profile), and the main
 * panel is a searchable list of waypoint cards — marker, name, distance, coordinates and
 * dimension — each expanding into its own options card.
 */
public class WaypointSettingsScreen extends ModuleSettingsScreen {
	private static final int ROW_H = 17;
	private static final int CARD_H = 28;

	private final WaypointsModule waypoints;
	private double listScroll;
	private boolean waypointsOpen = true;
	private boolean generalOpen;
	private boolean labelOpen = true;
	private boolean blockOpen;

	/** Sidebar filter: null = every saved world on this server, otherwise one world key. */
	private String worldFilter;
	/** Which waypoint's options card is open, or null. */
	private Waypoint expandedWaypoint;

	private GlassEditBox searchBox;

	/** World rename + delete arming, mirroring the profile row flow. */
	private String renamingWorld;
	private String deleteArmedWorld;
	private GlassEditBox worldRenameBox;
	/** Waypoint being renamed in place from its list row. */
	private Waypoint renamingWaypoint;
	/**
	 * The waypoint whose bin has been clicked once. A waypoint costs a walk to replace, unlike the
	 * text boxes whose bins delete on the press, so this one asks first.
	 */
	private Waypoint deleteArmedWp;
	/** When the code was last copied, so the button can say so briefly. */
	private long copiedAt;
	/** Last chat share, to keep the button from tripping server anti-spam. */
	private static long lastShare;
	/** Waypoint whose share popup is open, or null. Anchored to the icon that opened it. */
	private Waypoint sharePopupWp;
	private int sharePopupX;
	private int sharePopupY;

	/** Icon picker overlay state. */
	private boolean iconPickerOpen;
	private GlassEditBox iconSearch;
	private List<IconSuggestion> suggestions = List.of();
	private double suggestionScroll;

	/** Reset confirmation overlay. */
	private boolean confirmOpen;

	public WaypointSettingsScreen(ModListScreen list, WaypointsModule module) {
		super(list, module);
		this.waypoints = module;
	}

	@Override
	protected void initRows() {
		super.initRows();
		searchBox = new GlassEditBox(font, 0, 0, 200, 16, Component.literal("Search"), true);
		searchBox.setPlaceholder("Search waypoints…");
		searchBox.setResponder(q -> {
		});
		searchBox.visible = false;
		addRenderableWidget(searchBox);

		iconSearch = new GlassEditBox(font, 0, 0, 180, 16, Component.literal("Search"), true);
		iconSearch.setPlaceholder("golden, healing, oak…");
		iconSearch.setResponder(q -> {
			suggestions = WaypointsModule.suggestIcons(q, 60);
			suggestionScroll = 0;
		});
		iconSearch.visible = false;
		addRenderableWidget(iconSearch);

		worldRenameBox = new GlassEditBox(font, 0, 0, SIDEBAR_W - 11, 15,
				Component.literal("World name"), false);
		worldRenameBox.visible = false;
		addRenderableWidget(worldRenameBox);
		renamingWorld = null;
	}

	private void startWorldRename(int slot, String key) {
		renamingWorld = key;
		Rect rr = rowRect(slot);
		worldRenameBox.moveTo(px + 6, (int) rr.y());
		worldRenameBox.setValue(WaypointsModule.worldDisplayName(key));
		worldRenameBox.visible = true;
		setFocused(worldRenameBox);
	}

	private void commitWorldRename() {
		if (renamingWorld != null) {
			WaypointsModule.renameWorld(renamingWorld, worldRenameBox.getValue());
			renamingWorld = null;
			worldRenameBox.visible = false;
			setFocused(null);
			ModuleManager.save();
		}
	}

	@Override
	protected void onRowsRenderStart() {
		super.onRowsRenderStart();
		searchBox.visible = false;
		iconSearch.visible = false;
	}

	@Override
	protected boolean anyTextFieldFocused() {
		return super.anyTextFieldFocused()
				|| (searchBox != null && searchBox.isFocused())
				|| (iconSearch != null && iconSearch.isFocused())
				|| (worldRenameBox != null && worldRenameBox.isFocused());
	}

	private List<Waypoint> all() {
		return waypoints.currentList(Minecraft.getInstance());
	}

	/** A waypoint plus the saved world it lives in, so edits know where to write back. */
	private record Held(String worldKey, Waypoint wp) {
	}

	/** Waypoints passing the sidebar world filter and the search box. */
	private List<Held> filtered() {
		String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
		Minecraft mc = Minecraft.getInstance();
		List<Held> out = new ArrayList<>();
		// No world picked = every waypoint saved on this server.
		List<String> keys = worldFilter != null
				? List.of(worldFilter)
				: WaypointsModule.savedWorlds(mc);
		for (String key : keys) {
			for (Waypoint wp : WaypointsModule.waypointsIn(key)) {
				if (!q.isEmpty() && !wp.name.toLowerCase(Locale.ROOT).contains(q)) {
					continue;
				}
				out.add(new Held(key, wp));
			}
		}
		return out;
	}

	/** How many waypoints this server has in total. */
	private int serverCount() {
		int n = 0;
		for (String key : WaypointsModule.savedWorlds(Minecraft.getInstance())) {
			n += WaypointsModule.waypointsIn(key).size();
		}
		return n;
	}

	// ---- sidebar: dimensions + groups ----

	@Override
	protected boolean customSidebar() {
		return true;
	}

	private record Entry(int kind, String label, String value, int count) {
		static final int HEADER_WAYPOINTS = 0;
		static final int WORLD = 1;
	}

	/** Sidebar: the WAYPOINTS header (everything on this server) then each saved world. */
	private List<Entry> entries() {
		Minecraft mc = Minecraft.getInstance();
		List<Entry> out = new ArrayList<>();
		out.add(new Entry(Entry.HEADER_WAYPOINTS, "Waypoints", null, serverCount()));
		if (waypointsOpen) {
			for (String key : WaypointsModule.savedWorlds(mc)) {
				out.add(new Entry(Entry.WORLD, WaypointsModule.worldDisplayName(key), key,
						WaypointsModule.waypointsIn(key).size()));
			}
		}
		return out;
	}

	private Rect rowRect(int slot) {
		return new Rect(px + 2, sidebarTop() + slot * ROW_H - (float) listScroll, SIDEBAR_W - 3, 16);
	}

	@Override
	protected void renderCustomSidebar(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int bottom) {
		List<Entry> rows = entries();
		for (int slot = 0; slot < rows.size(); slot++) {
			Entry en = rows.get(slot);
			Rect rr = rowRect(slot);
			int ry = (int) rr.y();
			if (ry + ROW_H < py + HEADER_H || ry > bottom) {
				continue;
			}
			boolean inRow = rr.contains(mouseX, mouseY) && mouseY < bottom;
			switch (en.kind()) {
				case Entry.HEADER_WAYPOINTS -> {
					// No world picked = showing everything on this server.
					boolean active = worldFilter == null;
					if (active) {
						g.fill(px + 2, ry + 2, px + 4, ry + 14, Ui.accent());
					}
					Ui.caps(g, en.label() + " (" + en.count() + ")", px + 7, ry + 5,
							active || inRow ? Ui.TEXT : Ui.TEXT_DIM, 0.2f);
				}
				default -> {
					boolean active = en.value().equals(worldFilter);
					if (active) {
						g.fill(px + 2, ry + 2, px + 4, ry + 14, Ui.accent());
					}
					// The world's block: grass, netherrack or end stone.
					g.pose().pushMatrix();
					g.pose().translate(px + 8, ry + 4);
					g.pose().scale(0.55f, 0.55f);
					g.renderItem(WaypointsModule.worldIcon(en.value()), 0, 0);
					g.pose().popMatrix();
					if (!en.value().equals(renamingWorld)) {
						boolean armed = en.value().equals(deleteArmedWorld);
						MenuFont.draw(g, trim(en.label(), SIDEBAR_W - (armed ? 64 : 52))
										+ " (" + en.count() + ")",
								px + 22, ry + 4, active ? Ui.TEXT : Ui.TEXT_DIM,
								MenuFont.Size.BODY, false, 0.6f);
						if (Textures.ensure()) {
							int n = Textures.SIZE;
							if (armed) {
								// Right-click armed the row: the bin confirms deletion.
								boolean overBin = inRow && mouseX >= px + SIDEBAR_W - 17;
								g.blit(RenderPipelines.GUI_TEXTURED, Textures.TRASH,
										px + SIDEBAR_W - 14, ry + 4, 0f, 0f, 8, 8, n, n, n, n,
										overBin ? 0xFFFF7A7A : 0xFFD86A6A);
								if (overBin) {
									setTooltip("Delete world and its waypoints", mouseX, mouseY);
								}
							} else {
								boolean overPencil = inRow && mouseX >= px + SIDEBAR_W - 17;
								g.blit(RenderPipelines.GUI_TEXTURED, Textures.PENCIL,
										px + SIDEBAR_W - 14, ry + 4, 0f, 0f, 8, 8, n, n, n, n,
										overPencil ? Ui.TEXT : Ui.TEXT_DIM);
								if (overPencil) {
									setTooltip("Rename world", mouseX, mouseY);
								}
							}
						}
					}
				}
			}
		}

		int content = rows.size() * ROW_H;
		int view = bottom - sidebarTop();
		if (content > view) {
			int barH = Math.max(10, view * view / content);
			int barY = sidebarTop() + (int) ((view - barH) * (listScroll / (content - view)));
			Draw.smoothRounded(g, px + SIDEBAR_W - 4, barY, 2, barH, 1, 0x50FFFFFF);
		}
	}

	/** Worlds are tinted by the dimension in their key, so they read at a glance. */
	private static int worldColor(String key) {
		if (key.contains("the_nether")) {
			return 0xFF8C3A2E;
		}
		if (key.contains("the_end")) {
			return 0xFFD8D8A0;
		}
		return 0xFF4E8C3A;
	}

	private void drawEye(GuiGraphics g, int x, int y, boolean on, boolean hover) {
		if (!Textures.ensure()) {
			return;
		}
		int n = Textures.SIZE;
		g.blit(RenderPipelines.GUI_TEXTURED, on ? Textures.EYE : Textures.EYE_OFF, x, y,
				0f, 0f, 10, 10, n, n, n, n, hover ? Ui.TEXT : (on ? Ui.TEXT_DIM : 0x66EDEDF2));
	}

	private String trim(String s, int maxW) {
		if (MenuFont.width(s, MenuFont.Size.BODY, 0.6f) <= maxW) {
			return s;
		}
		String out = s;
		while (out.length() > 1 && MenuFont.width(out + "…", MenuFont.Size.BODY, 0.6f) > maxW) {
			out = out.substring(0, out.length() - 1);
		}
		return out + "…";
	}

	@Override
	protected void renderCustomSidebarFooter(GuiGraphics g, int mouseX, int mouseY, Rect slot) {
		boolean hover = slot.contains(mouseX, mouseY);
		boolean clear = worldFilter != null;
		String label = clear ? "Show All" : "+ New Waypoint";
		Draw.smoothBorder(g, (int) slot.x(), (int) slot.y(), (int) slot.w(), (int) slot.h(), 3, 0x2AFFFFFF);
		Ui.caps(g, label, (int) (slot.x() + (slot.w() - Ui.capsW(label, 0.2f)) / 2), (int) slot.y() + 3,
				hover ? Ui.TEXT : Ui.TEXT_DIM, 0.2f);
	}

	@Override
	protected boolean clickCustomSidebar(MouseButtonEvent e) {
		if (sidebarFooterRect().contains(e.x(), e.y())) {
			if (worldFilter != null) {
				worldFilter = null;
			} else {
				waypoints.addAtPlayer();
				List<Waypoint> here = all();
				Waypoint added = here.isEmpty() ? null : here.get(here.size() - 1);
				waypoints.setSelected(added);
				expandedWaypoint = added;
			}
			refreshTextFields();
			return true;
		}
		List<Entry> rows = entries();
		for (int slot = 0; slot < rows.size(); slot++) {
			Rect rr = rowRect(slot);
			if (e.y() >= sidebarBottom() || !rr.contains(e.x(), e.y())) {
				continue;
			}
			Entry en = rows.get(slot);
			// Right-click arms the row (bin appears); the pencil renames — same as profiles.
			if (en.kind() == Entry.WORLD && e.button() == 1) {
				deleteArmedWorld = en.value().equals(deleteArmedWorld) ? null : en.value();
				return true;
			}
			if (renamingWorld != null) {
				commitWorldRename();
			}
			if (en.kind() == Entry.WORLD && e.x() >= px + SIDEBAR_W - 17) {
				if (en.value().equals(deleteArmedWorld)) {
					WaypointsModule.deleteWorld(en.value());
					if (en.value().equals(worldFilter)) {
						worldFilter = null;
					}
					deleteArmedWorld = null;
					expandedWaypoint = null;
					waypoints.setSelected(null);
				} else {
					startWorldRename(slot, en.value());
				}
				refreshTextFields();
				return true;
			}
			deleteArmedWorld = null;
			if (en.kind() == Entry.HEADER_WAYPOINTS) {
				// Clicking the header shows every waypoint on the server again.
				if (worldFilter != null) {
					worldFilter = null;
				} else {
					waypointsOpen = !waypointsOpen;
				}
			} else {
				// Clicking the active world unpicks it, back to the whole server.
				worldFilter = en.value().equals(worldFilter) ? null : en.value();
			}
			refreshTextFields();
			return true;
		}
		return false;
	}

	@Override
	protected void scrollCustomSidebar(double delta) {
		int content = entries().size() * ROW_H;
		int view = sidebarBottom() - sidebarTop();
		double max = Math.max(0, content - view);
		listScroll = Math.max(0, Math.min(max, listScroll - delta * ROW_H));
	}

	// ---- main panel ----

	@Override
	protected List<Row> buildRows() {
		List<Row> rows = new ArrayList<>();
		// Module toggle and the general settings share one line: the gear opens the card.
		rows.add(toggleGear("Waypoints Enabled", () -> module().settings().enabled,
				v -> module().settings().enabled = v,
				() -> generalOpen = !generalOpen, () -> generalOpen,
				() -> module().settings().enabled = false));
		if (generalOpen) {
			groupCard(rows, group -> {
				waypoints.appendKeybindRows(this, group);
				addGeneralAppearanceRows(group, module().settings(), defaults(),
						this::bgExpandedState, this::toggleBgExpanded);
			});
		}

		rows.add(searchRow());

		List<Held> shown = filtered();
		if (shown.isEmpty()) {
			rows.add(infoRow("No waypoints", () -> worldFilter != null
					? "none in this world" : "press the waypoint key"));
			return rows;
		}
		for (Held held : shown) {
			Waypoint wp = held.wp();
			rows.add(cardRow(held));
			if (wp == expandedWaypoint) {
				// The editor mirrors wWaypoints' shape: identity row, then Label and Block
				// Display as separate cards with their own enable toggles.
				groupCard(rows, group -> {
					group.add(waypointHeaderRow(wp));
					group.add(toggleGear("Label", () -> wp.labelEnabled, v -> wp.labelEnabled = v,
							() -> labelOpen = !labelOpen, () -> labelOpen, () -> wp.labelEnabled = true));
					if (labelOpen) {
						groupCard(group, inner -> waypoints.appendLabelRows(this, inner, wp));
					}
					group.add(toggleGear("Block Display", () -> wp.highlight, v -> wp.highlight = v,
							() -> blockOpen = !blockOpen, () -> blockOpen, () -> wp.highlight = true));
					if (blockOpen) {
						groupCard(group, inner -> waypoints.appendBlockRows(this, inner, wp));
					}
					waypoints.appendPlacementRows(this, group, wp);
					group.add(deleteRow(held));
				});
			}
		}
		return rows;
	}

	/** Red delete action at the bottom of the editor, like the reference's Delete button. */
	private Row deleteRow(Held held) {
		return new Row(24, (g, y, mx, my) -> {
			int w = 110;
			int x = mainX + (mainW - w) / 2;
			boolean hover = mx >= x && mx < x + w && my >= y + 4 && my < y + 22
					&& my >= rowY0 && my <= rowY1;
			Draw.smoothRounded(g, x, y + 4, w, 18, 4, hover ? 0xE0C43D3D : 0xB2C43D3D);
			Ui.caps(g, "Delete Waypoint", x + (w - Ui.capsW("Delete Waypoint", 0.2f)) / 2, y + 9,
					0xFFFFFFFF, 0.2f);
		}, (e, y) -> {
			int w = 110;
			int x = mainX + (mainW - w) / 2;
			if (e.x() >= x && e.x() < x + w && e.y() >= y + 4 && e.y() < y + 22) {
				WaypointsModule.waypointsIn(held.worldKey()).remove(held.wp());
				expandedWaypoint = null;
				waypoints.setSelected(null);
				refreshTextFields();
				return true;
			}
			return false;
		});
	}

	/** Import sits beside the search rather than inside it: a search box that also does a
	 * non-search thing is a small trap, and this one is reached from outside the game anyway. */
	private static final int IMPORT_W = 20;

	private Row searchRow() {
		return new Row(22, (g, y, mx, my) -> {
			searchBox.resizeTo(mainW - 4 - IMPORT_W - 4);
			searchBox.moveTo(mainX + 2, y + 3);
			searchBox.visible = true;
			searchBox.setPlaceholder("Search " + all().size() + " waypoints…");
			int bx = mainX + mainW - IMPORT_W - 2;
			boolean over = mx >= bx && mx < bx + IMPORT_W && my >= y + 3 && my < y + 19
					&& my >= rowY0 && my <= rowY1;
			Draw.smoothRounded(g, bx, y + 3, IMPORT_W, 16, 4, over ? 0x24FFFFFF : 0x14FFFFFF);
			if (Textures.ensure()) {
				int n = Textures.SIZE;
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.IMPORT, bx + (IMPORT_W - 10) / 2, y + 6,
						0f, 0f, 10, 10, n, n, n, n, over ? Ui.TEXT : Ui.TEXT_DIM);
			}
			if (over) {
				setTooltip("Add a waypoint from a copied code", mx, my);
			}
		}, (e, y) -> {
			int bx = mainX + mainW - IMPORT_W - 2;
			if (e.x() >= bx && e.x() < bx + IMPORT_W && e.y() >= y + 3 && e.y() < y + 19) {
				String clip = WaypointShare.fromClipboard(Minecraft.getInstance());
				String code = WaypointShare.findCode(clip);
				dev.clientify.client.util.WaypointShareHandler.open(code == null ? clip : code);
				return true;
			}
			return false;
		});
	}

	/** A waypoint card: marker, name, then distance · coords · world. */
	private Row cardRow(Held held) {
		Waypoint wp = held.wp();
		return new Row(CARD_H, (g, y, mx, my) -> {
			boolean hover = mx >= mainX && mx < mainX + mainW && my >= y && my < y + CARD_H
					&& my >= rowY0 && my <= rowY1;
			boolean open = wp == expandedWaypoint;
			if (hover || open) {
				Draw.smoothRounded(g, mainX, y + 1, mainW, CARD_H - 3, 4, open ? 0x1EFFFFFF : 0x14FFFFFF);
			}
			// Icon and the two text lines are centred together in the card.
			int mid = y + CARD_H / 2 - 1;
			ItemStack icon = WaypointsModule.iconStack(wp.icon);
			if (!icon.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(mainX + 5, mid - 6);
				g.pose().scale(0.75f, 0.75f);
				g.renderItem(icon, 0, 0);
				g.pose().popMatrix();
			} else {
				g.fill(mainX + 7, mid - 4, mainX + 15, mid + 4, wp.color.chrome());
			}
			int nameX = mainX + 21;
			if (wp == renamingWaypoint) {
				// Rename in place using the waypoint's own Name field, so typing writes through.
				GlassEditBox nameBox = fieldBox(0);
				if (nameBox != null) {
					// One slot wider than it was: the bin took a fourth 16px strip off the right.
					nameBox.resizeTo(Math.min(150, controlRight() - nameX - 66));
					nameBox.moveTo(nameX, mid - 11);
					nameBox.visible = true;
				}
			} else {
				MenuFont.draw(g, wp.name, nameX, mid - 9, wp.visible ? Ui.TEXT : Ui.TEXT_DIM,
						MenuFont.Size.BODY, false, 0.6f);
			}
			var a = wp.anchor();
			String meta = distanceText(wp) + "  •  " + a.x + ", " + a.y + ", " + a.z
					+ "  •  " + WaypointsModule.worldDisplayName(held.worldKey());
			Ui.str(g, meta, nameX, mid + 2, Ui.TEXT_DIM);

			// Bin, pencil, eye, gear — all centred on the same line, in shared right-aligned slots.
			if (Textures.ensure()) {
				int n = Textures.SIZE;
				int pencilX = iconSlotX(SLOT_PENCIL);
				boolean overPencil = iconSlotHit(mx, SLOT_PENCIL) && hover;
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.PENCIL, pencilX, mid - 5,
						0f, 0f, 10, 10, n, n, n, n, overPencil ? Ui.TEXT : Ui.TEXT_DIM);
				if (overPencil) {
					setTooltip("Rename waypoint", mx, my);
				}
				int trashX = iconSlotX(SLOT_TRASH);
				boolean overTrash = iconSlotHit(mx, SLOT_TRASH) && hover;
				boolean armed = wp == deleteArmedWp;
				// Armed stays lit whether or not the cursor is on it, so the row that is one click
				// from going is obvious even after the mouse has wandered off.
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.TRASH, trashX, mid - 5,
						0f, 0f, 10, 10, n, n, n, n,
						armed ? 0xFFFF4A4A : (overTrash ? 0xFFFF6666 : Ui.TEXT_DIM));
				if (overTrash || armed) {
					setTooltip(armed ? "Click again to delete" : "Delete waypoint", mx, my);
				}
			}
			drawEye(g, iconSlotX(SLOT_EYE), mid - 5, wp.visible, iconSlotHit(mx, SLOT_EYE) && hover);
			int gearX = iconSlotX(SLOT_GEAR);
			// The gear glyph is 12px rather than 10, so it owns a slightly wider strip.
			boolean overGear = mx >= gearX - 2 && mx < gearX + 14 && hover;
			drawGear(g, gearX, mid - 6, open || overGear);
		}, (e, y) -> {
			if (iconSlotHit(e.x(), SLOT_TRASH)) {
				if (wp != deleteArmedWp) {
					deleteArmedWp = wp; // first click only arms it
					return true;
				}
				deleteArmedWp = null;
				WaypointsModule.waypointsIn(held.worldKey()).remove(wp);
				if (expandedWaypoint == wp) {
					expandedWaypoint = null;
				}
				if (renamingWaypoint == wp) {
					renamingWaypoint = null;
				}
				waypoints.setSelected(null);
				refreshTextFields();
				return true;
			}
			deleteArmedWp = null; // any other click on any row disarms
			if (iconSlotHit(e.x(), SLOT_PENCIL)) {
				// Select it first so its Name field exists, then focus that field in place.
				waypoints.setSelected(wp);
				refreshTextFields();
				renamingWaypoint = wp;
				GlassEditBox nameBox = fieldBox(0);
				if (nameBox != null) {
					nameBox.visible = true;
					setFocused(nameBox);
					nameBox.moveCursorToEnd(false);
				}
				return true;
			}
			if (iconSlotHit(e.x(), SLOT_EYE)) {
				wp.visible = !wp.visible;
				return true;
			}
			renamingWaypoint = null;
			// Anywhere else on the card opens or closes its options, but only a press on the gear
			// itself turns it — the whole card being clickable does not make the whole card a gear.
			int gearX = iconSlotX(SLOT_GEAR);
			if (e.x() >= gearX - 2 && e.x() < gearX + 14) {
				gearClicked(gearX, y + CARD_H / 2 - 7);
			}
			expandedWaypoint = expandedWaypoint == wp ? null : wp;
			waypoints.setSelected(expandedWaypoint);
			refreshTextFields();
			collapsePicker();
			return true;
		});
	}

	private String distanceText(Waypoint wp) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return "-";
		}
		var a = wp.anchor();
		double d = Math.sqrt(mc.player.distanceToSqr(a.x + 0.5, a.y + 0.5, a.z + 0.5));
		return (int) d + " blocks away";
	}

	private Waypoint expanded() {
		return expandedWaypoint;
	}

	/**
	 * The compact identity row: visibility pill, the icon (click to pick), X/Y/Z boxes and a
	 * copy-to-clipboard button.
	 */
	private Row waypointHeaderRow(Waypoint wp) {
		boolean multi = wp.blocks.size() > 1;
		return new Row(22, (g, y, mx, my) -> {
			int x = mainX + 8 + indentOf();
			// Same eye as the list, so visibility reads the same way in both places.
			boolean overEye = mx >= x - 2 && mx < x + 14 && my >= y + 3 && my < y + 19
					&& my >= rowY0 && my <= rowY1;
			drawEye(g, x, y + 6, wp.visible, overEye);
			x += 20;
			boolean overIcon = mx >= x && mx < x + 16 && my >= y + 2 && my < y + 18
					&& my >= rowY0 && my <= rowY1;
			Draw.smoothRounded(g, x, y + 3, 16, 16, 3, overIcon ? 0x33FFFFFF : 0x1AFFFFFF);
			ItemStack icon = WaypointsModule.iconStack(wp.icon);
			if (!icon.isEmpty() && !overIcon) {
				g.renderItem(icon, x, y + 3);
			} else if (Textures.ensure()) {
				int n = Textures.SIZE;
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.PENCIL, x + 4, y + 7, 0f, 0f, 8, 8, n, n, n, n,
						overIcon ? Ui.TEXT : Ui.TEXT_DIM);
			}
			if (overIcon) {
				setTooltip("Choose icon", mx, my);
			}
			x += 22;
			int actionsW = 40; // copy + share, 16px apart
			int boxW = Math.max(28, (controlRight() - x - actionsW - 10) / 3);
			for (int i = 0; i < 3; i++) {
				GlassEditBox box = fieldBox(2 + i);
				if (box != null) {
					box.resizeTo(boxW);
					box.moveTo(x + i * (boxW + 3), y + 3);
					box.visible = true;
				}
			}
			if (multi) {
				setTooltipIfOver(mx, my, x, y + 3, boxW * 3 + 6, 16,
						"Moves the whole selection; the shape is kept");
			}
			int shareX = controlRight() - 14;
			int copyX = shareX - 18;
			boolean overCopy = mx >= copyX - 2 && mx < copyX + 12 && my >= y + 3 && my < y + 19
					&& my >= rowY0 && my <= rowY1;
			boolean overShare = mx >= shareX - 2 && mx < shareX + 12 && my >= y + 3 && my < y + 19
					&& my >= rowY0 && my <= rowY1;
			if (Textures.ensure()) {
				int n = Textures.SIZE;
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.COPY, copyX, y + 6, 0f, 0f, 10, 10,
						n, n, n, n, overCopy ? Ui.TEXT : Ui.TEXT_DIM);
				g.blit(RenderPipelines.GUI_TEXTURED, Textures.SHARE, shareX, y + 6, 0f, 0f, 10, 10,
						n, n, n, n, sharePopupWp == wp || overShare ? Ui.accent() : Ui.TEXT_DIM);
			}
			if (overCopy) {
				// Named precisely, because the share popup also offers a copy and the two put
				// different things on the clipboard.
				setTooltip("Copy coordinates", mx, my);
			} else if (overShare) {
				setTooltip("Share this waypoint", mx, my);
			}
		}, (e, y) -> {
			int x = mainX + 8 + indentOf();
			if (e.x() < x + 18) {
				wp.visible = !wp.visible;
				return true;
			}
			x += 20;
			if (e.x() >= x && e.x() < x + 16) {
				openIconPicker();
				return true;
			}
			int shareX = controlRight() - 14;
			int copyX = shareX - 18;
			if (e.x() >= copyX - 2 && e.x() < copyX + 12) {
				var a = wp.anchor();
				minecraft.keyboardHandler.setClipboard(a.x + " " + a.y + " " + a.z);
				return true;
			}
			if (e.x() >= shareX - 2 && e.x() < shareX + 12) {
				sharePopupWp = sharePopupWp == wp ? null : wp;
				sharePopupX = shareX;
				sharePopupY = (int) e.y();
				return true;
			}
			return false;
		});
	}

	private static final int POPUP_W = 104;
	private static final int POPUP_H = 38;

	/** Where the popup sits: under its icon, nudged left so it stays inside the panel. */
	private Rect sharePopupRect() {
		int x = Math.min(sharePopupX + 12 - POPUP_W, px + pw - POPUP_W - 6);
		int y = Math.min(sharePopupY + 10, py + ph - POPUP_H - 6);
		return new Rect(x, y, POPUP_W, POPUP_H);
	}

	private void renderSharePopup(GuiGraphics g, int mouseX, int mouseY) {
		Rect r = sharePopupRect();
		int x = (int) r.x();
		int y = (int) r.y();
		Draw.smoothRoundedBordered(g, x, y, POPUP_W, POPUP_H, 4, Ui.panel(), Ui.HAIRLINE, 1);
		boolean onChat = mouseX >= x + 2 && mouseX < x + POPUP_W - 2
				&& mouseY >= y + 3 && mouseY < y + 18;
		boolean onCopy = mouseX >= x + 2 && mouseX < x + POPUP_W - 2
				&& mouseY >= y + 19 && mouseY < y + 34;
		if (onChat) {
			Draw.smoothRounded(g, x + 2, y + 3, POPUP_W - 4, 15, 3, 0x1EFFFFFF);
		}
		if (onCopy) {
			Draw.smoothRounded(g, x + 2, y + 19, POPUP_W - 4, 15, 3, 0x1EFFFFFF);
		}
		Ui.str(g, "Share in chat", x + 8, y + 8, onChat ? Ui.TEXT : Ui.TEXT_DIM);
		Ui.str(g, "Copy to clipboard", x + 8, y + 24, onCopy ? Ui.TEXT : Ui.TEXT_DIM);
	}

	/** Returns true when the click belonged to the popup (including dismissing it). */
	private boolean sharePopupClicked(double mx, double my) {
		Rect r = sharePopupRect();
		if (!r.contains(mx, my)) {
			sharePopupWp = null;
			return false; // let the click through; it was aimed at whatever is behind
		}
		Waypoint wp = sharePopupWp;
		Minecraft mc = Minecraft.getInstance();
		int y = (int) r.y();
		if (my >= y + 3 && my < y + 18) {
			String line = WaypointShare.chatLine(wp);
			// Needs a connection and a gap since the last one: chat sent faster than a person
			// could type it is exactly what server anti-spam watches for.
			if (line != null && mc.player != null && mc.player.connection != null
					&& System.currentTimeMillis() - lastShare > 2000) {
				lastShare = System.currentTimeMillis();
				mc.player.connection.sendChat(line);
				sharePopupWp = null;
				onClose(); // so the message can actually be seen landing
				return true;
			}
			sharePopupWp = null;
			return true;
		}
		if (my >= y + 19 && my < y + 34) {
			WaypointShare.copyToClipboard(mc, WaypointShare.encode(wp));
			copiedAt = System.currentTimeMillis();
			sharePopupWp = null;
			return true;
		}
		return true;
	}

	private void setTooltipIfOver(int mx, int my, int x, int y, int w, int h, String text) {
		if (mx >= x && mx < x + w && my >= y && my < y + h && my >= rowY0 && my <= rowY1) {
			setTooltip(text, mx, my);
		}
	}

	private void openIconPicker() {
		iconPickerOpen = true;
		suggestions = WaypointsModule.suggestIcons(iconSearch.getValue(), 60);
		setFocused(iconSearch);
	}

	// ---- overlays: icon picker + reset confirmation ----

	@Override
	protected void renderMain(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderMain(g, mouseX, mouseY, partialTick);
		if (iconPickerOpen || confirmOpen) {
			// The rows just made their edit boxes visible; widgets draw after us, so they would
			// punch through the overlay. Hide them while a dialog owns the screen.
			hideRowWidgets();
		}
		if (iconPickerOpen) {
			renderIconPicker(g, mouseX, mouseY);
		}
		if (confirmOpen) {
			renderConfirm(g, mouseX, mouseY);
		}
		if (sharePopupWp != null) {
			renderSharePopup(g, mouseX, mouseY);
		}
	}

	/** Hides every pooled row widget (coord boxes, search) so an overlay stays on top. */
	private void hideRowWidgets() {
		searchBox.visible = false;
		worldRenameBox.visible = false;
		for (int i = 0; i < 8; i++) {
			GlassEditBox box = fieldBox(i);
			if (box != null) {
				box.visible = false;
			}
		}
	}

	private Rect pickerRect() {
		return new Rect(mainX + 6, py + HEADER_H + 20, mainW - 12, ph - HEADER_H - 50);
	}

	private void renderIconPicker(GuiGraphics g, int mouseX, int mouseY) {
		Rect r = pickerRect();
		// Same rounded sheet as the menu, lifted a shade so it reads as the top layer.
		Draw.smoothRoundedBordered(g, (int) r.x(), (int) r.y(), (int) r.w(), (int) r.h(),
				Draw.R_SHEET, Ui.panel(), Ui.HAIRLINE, 1);
		Draw.smoothRounded(g, (int) r.x() + 1, (int) r.y() + 1, (int) r.w() - 2, (int) r.h() - 2,
				Draw.R_SHEET - 1, 0x24FFFFFF);
		Ui.caps(g, "Choose Icon", (int) r.x() + 10, (int) r.y() + 8, Ui.TEXT, 0.2f);
		Rect close = new Rect(r.x() + r.w() - 22, r.y() + 6, 16, 16);
		boolean overClose = close.contains(mouseX, mouseY);
		if (overClose) {
			Draw.smoothRounded(g, (int) close.x(), (int) close.y(), 16, 16, 4, 0x59C43D3D);
		}
		Draw.smoothBorder(g, (int) close.x(), (int) close.y(), 16, 16, 4, 0x2AFFFFFF);
		if (Textures.ensure()) {
			int n = Textures.SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.CLOSE, (int) close.x() + 4, (int) close.y() + 4,
					0f, 0f, 8, 8, n, n, n, n, Ui.TEXT);
		}
		g.fill((int) r.x() + 1, (int) r.y() + 26, (int) (r.x() + r.w()) - 1, (int) r.y() + 27, Ui.HAIRLINE);

		iconSearch.resizeTo((int) r.w() - 16);
		iconSearch.moveTo((int) r.x() + 8, (int) r.y() + 32);
		iconSearch.visible = true;

		int listTop = (int) r.y() + 54;
		int listBottom = (int) (r.y() + r.h()) - 8;
		g.enableScissor((int) r.x() + 4, listTop, (int) (r.x() + r.w()) - 4, listBottom);
		int y = listTop - (int) suggestionScroll;
		for (IconSuggestion s : suggestions) {
			if (y + 18 >= listTop && y <= listBottom) {
				boolean hover = mouseX >= r.x() + 6 && mouseX < r.x() + r.w() - 6
						&& mouseY >= y && mouseY < y + 18 && mouseY >= listTop && mouseY < listBottom;
				if (hover) {
					Draw.smoothRounded(g, (int) r.x() + 6, y, (int) r.w() - 12, 18, 3, 0x22FFFFFF);
				}
				g.renderItem(s.stack(), (int) r.x() + 10, y + 1);
				Ui.str(g, s.label(), (int) r.x() + 32, y + 5, hover ? Ui.TEXT : Ui.TEXT_DIM);
			}
			y += 18;
		}
		g.disableScissor();
		if (suggestions.isEmpty()) {
			Ui.str(g, "Type to search items and potions", (int) r.x() + 10, listTop + 4, Ui.TEXT_DIM);
		}
	}

	private Rect confirmRect() {
		return new Rect(mainX + 10, py + ph / 2 - 45, mainW - 20, 90);
	}

	private void renderConfirm(GuiGraphics g, int mouseX, int mouseY) {
		Rect r = confirmRect();
		Draw.smoothRoundedBordered(g, (int) r.x(), (int) r.y(), (int) r.w(), (int) r.h(), 6,
				0xF2101014, Ui.HAIRLINE, 1);
		Ui.str(g, "Reset waypoints module?", (int) r.x() + 10, (int) r.y() + 10, Ui.TEXT);
		Ui.str(g, "Deleting removes EVERY waypoint in every world.", (int) r.x() + 10,
				(int) r.y() + 24, Ui.TEXT_DIM);
		int by = (int) (r.y() + r.h()) - 26;
		int bw = ((int) r.w() - 30) / 3;
		drawConfirmButton(g, (int) r.x() + 10, by, bw, "Cancel", 0x33FFFFFF, mouseX, mouseY);
		drawConfirmButton(g, (int) r.x() + 15 + bw, by, bw, "Visuals Only", Ui.accentDim(), mouseX, mouseY);
		drawConfirmButton(g, (int) r.x() + 20 + bw * 2, by, bw, "Delete All", 0xB2C43D3D, mouseX, mouseY);
	}

	private void drawConfirmButton(GuiGraphics g, int x, int y, int w, String label, int color,
			int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 18;
		Draw.smoothRounded(g, x, y, w, 18, 4, hover ? brighten(color) : color);
		Ui.caps(g, label, x + (w - Ui.capsW(label, 0.2f)) / 2, y + 6, 0xFFFFFFFF, 0.2f);
	}

	private static int brighten(int argb) {
		int a = argb >>> 24;
		return (Math.min(255, a + 40) << 24) | (argb & 0xFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		// The popup is on top of everything, so it gets the click first. A miss dismisses it and
		// falls through, which is what makes clicking straight onto another control feel right.
		if (sharePopupWp != null && sharePopupClicked(e.x(), e.y())) {
			return true;
		}
		if (confirmOpen) {
			Rect r = confirmRect();
			int by = (int) (r.y() + r.h()) - 26;
			int bw = ((int) r.w() - 30) / 3;
			if (e.y() >= by && e.y() < by + 18) {
				if (e.x() >= r.x() + 20 + bw * 2 && e.x() < r.x() + 20 + bw * 3) {
					ClientifyConfig.resetWaypoints();
					waypoints.setSelected(null);
					expandedWaypoint = null;
				} else if (e.x() >= r.x() + 15 + bw && e.x() < r.x() + 15 + bw * 2) {
					module().setSettings(module().createDefaultSettings()); // visuals only
				}
			}
			confirmOpen = false;
			ModuleManager.save();
			refreshTextFields();
			return true;
		}
		if (iconPickerOpen) {
			Rect r = pickerRect();
			if (!r.contains(e.x(), e.y())) {
				iconPickerOpen = false;
				return true; // clicks outside dismiss, and never reach the rows behind
			}
			Rect close = new Rect(r.x() + r.w() - 22, r.y() + 6, 16, 16);
			if (close.contains(e.x(), e.y())) {
				iconPickerOpen = false;
				return true;
			}
			// Only the dialog's own field may take the click — going through super would let
			// the rows and sidebar behind the dialog handle it.
			if (iconSearch.isMouseOver(e.x(), e.y())) {
				setFocused(iconSearch);
				iconSearch.mouseClicked(e, doubleClick);
				return true;
			}
			int listTop = (int) r.y() + 54;
			int listBottom = (int) (r.y() + r.h()) - 8;
			int y = listTop - (int) suggestionScroll;
			for (IconSuggestion s : suggestions) {
				if (e.y() >= y && e.y() < y + 18 && e.y() >= listTop && e.y() < listBottom) {
					Waypoint wp = expanded();
					if (wp != null) {
						wp.icon = s.value();
						refreshTextFields();
					}
					iconPickerOpen = false;
					return true;
				}
				y += 18;
			}
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hDelta, double vDelta) {
		if (iconPickerOpen && pickerRect().contains(mx, my)) {
			Rect r = pickerRect();
			int view = (int) r.h() - 40;
			double max = Math.max(0, suggestions.size() * 18 - view);
			suggestionScroll = Math.max(0, Math.min(max, suggestionScroll - vDelta * 18));
			return true;
		}
		return super.mouseScrolled(mx, my, hDelta, vDelta);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
		if ((iconPickerOpen || confirmOpen) && e.isEscape()) {
			iconPickerOpen = false;
			confirmOpen = false;
			return true;
		}
		if (renamingWaypoint != null && (e.isEscape() || e.key() == 257 || e.key() == 335)) {
			renamingWaypoint = null;
			setFocused(null);
			ModuleManager.save();
			return true;
		}
		if (renamingWorld != null) {
			if (e.isEscape()) {
				renamingWorld = null;
				worldRenameBox.visible = false;
				setFocused(null);
				return true;
			}
			if (e.key() == 257 || e.key() == 335) {
				commitWorldRename();
				return true;
			}
		}
		return super.keyPressed(e);
	}

	/** The module reset asks first — waypoints are world data, not just styling. */
	@Override
	protected boolean rowsHeaderClicked(MouseButtonEvent e) {
		if (resetAllHit(e)) {
			confirmOpen = true;
			return true;
		}
		return super.rowsHeaderClicked(e);
	}
}
