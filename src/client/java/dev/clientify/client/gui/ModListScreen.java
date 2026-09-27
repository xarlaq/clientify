package dev.clientify.client.gui;

import dev.clientify.client.config.ClientifyConfig;
import dev.clientify.client.gui.widget.GlassEditBox;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The mods list: filter chips + search, and a 3-column card grid where the border color
 * carries state (green = enabled, muted red = disabled). Card click toggles; the gear opens
 * the module's settings view.
 */
public class ModListScreen extends PanelScreen {
	private static final int CARD_H = 20;
	private static final int GAP = 6;
	private static final int COLS = 3;

	private String activeCategory = "ALL";
	private String query = "";
	private GlassEditBox searchBox;

	private int gridY0, gridY1, cardW;
	private double scroll;
	private List<String> categories = new ArrayList<>();

	public ModListScreen(Screen editorScreen) {
		super(Component.literal("Clientify Mods"), editorScreen);
	}

	@Override
	protected ModListScreen listScreen() {
		return this;
	}

	@Override
	protected void initMain() {
		cardW = (mainW - (COLS - 1) * GAP) / COLS;
		gridY0 = py + HEADER_H + 26;
		gridY1 = py + ph - 8;
		scroll = 0;

		LinkedHashSet<String> cats = new LinkedHashSet<>();
		cats.add("ALL");
		for (HudModule m : ModuleManager.all()) {
			cats.add(m.category().toUpperCase(Locale.ROOT));
		}
		categories = new ArrayList<>(cats);
		if (!categories.contains(activeCategory)) {
			activeCategory = "ALL";
		}

		int searchW = 110;
		searchBox = new GlassEditBox(font, mainX + mainW - searchW, py + HEADER_H + 6, searchW, 14,
				Component.literal("Search"));
		searchBox.setPlaceholder("Search");
		searchBox.setValue(query);
		searchBox.setResponder(v -> {
			query = v;
			scroll = 0;
		});
		addRenderableWidget(searchBox);
	}

	@Override
	protected Screen backTarget() {
		return editorScreen;
	}

	@Override
	protected boolean anyTextFieldFocused() {
		return super.anyTextFieldFocused() || (searchBox != null && searchBox.isFocused());
	}

	/** What each module can be found by, built once per module. */
	private static final java.util.Map<String, String> SEARCH_INDEX = new java.util.HashMap<>();

	/**
	 * True when a module answers to what was typed.
	 *
	 * <p>Matched against its settings as well as its name, so searching for a feature finds the
	 * module holding it -- "fishing" reaches Overlay, which never says the word itself. The
	 * settings names come from the fields of its settings class, so the index is whatever the
	 * module actually has rather than a list written beside it that goes stale.
	 */
	private static boolean matches(HudModule m, String q) {
		return SEARCH_INDEX.computeIfAbsent(m.id(), k -> {
			StringBuilder terms = new StringBuilder();
			terms.append(m.displayName()).append(' ').append(m.id()).append(' ')
					.append(m.category()).append(' ').append(m.description());
			for (java.lang.reflect.Field f : m.settingsClass().getFields()) {
				// hideFoliage becomes "hide foliage": the words are what someone would type.
				terms.append(' ').append(f.getName().replaceAll("([a-z])([A-Z])", "$1 $2"));
			}
			return terms.toString().toLowerCase(Locale.ROOT);
		}).contains(q);
	}

	private List<HudModule> filtered() {
		List<HudModule> out = new ArrayList<>();
		String q = query.trim().toLowerCase(Locale.ROOT);
		for (HudModule m : ModuleManager.all()) {
			if (!activeCategory.equals("ALL") && !m.category().toUpperCase(Locale.ROOT).equals(activeCategory)) {
				continue;
			}
			if (!q.isEmpty() && !matches(m, q)) {
				continue;
			}
			out.add(m);
		}
		// Default is the order the modules were registered in, which groups them by kind.
		if (sort().equals("A-Z")) {
			out.sort(java.util.Comparator.comparing(x -> x.displayName().toLowerCase(Locale.ROOT)));
		} else if (sort().equals("Z-A")) {
			out.sort(java.util.Comparator.comparing((HudModule x) ->
					x.displayName().toLowerCase(Locale.ROOT)).reversed());
		} else if (sort().equals("CUSTOM")) {
			// Anything never dragged keeps to the end, in the order it already had.
			out.sort(java.util.Comparator.comparingInt(x -> {
				int at = order().indexOf(x.id());
				return at < 0 ? Integer.MAX_VALUE : at;
			}));
		} else if (sort().equals("LAST USED")) {
			// Never touched sorts last rather than first, and keeps its usual order down there.
			out.sort(java.util.Comparator.comparingLong(
					(HudModule x) -> -lastUsed().getOrDefault(x.id(), 0L)));
		}
		return out;
	}

	private static final String[] SORTS = {"DEFAULT", "A-Z", "Z-A", "LAST USED", "CUSTOM"};

	// How the list is arranged lives in the active profile, so it survives a restart and a
	// PvP layout and a building layout can order their modules differently. Read through these
	// rather than cached: switching profile has to change the answer immediately.

	private static String sort() {
		return ClientifyConfig.listPrefs().sort;
	}

	/** The order the user dragged cards into, by module id. */
	private static List<String> order() {
		return ClientifyConfig.listPrefs().order;
	}

	/** When each module was last opened, so the ones being worked on come first. */
	private static java.util.Map<String, Long> lastUsed() {
		return ClientifyConfig.listPrefs().lastUsed;
	}

	/** The card under the press, until it is known whether this is a drag or a click. */
	private HudModule pressed;
	private double pressX, pressY;
	/** Where in the card the press landed, so it hangs from the cursor where it was taken. */
	private double grabDx, grabDy;
	private boolean pressedGear;
	private boolean dragging;
	/** Where the cursor was last frame, and how far the card is leaning because of it. */
	private double lastCarryX;
	private float swing;
	private int dropAt = -1;
	/** Far enough that a shaky click is not a drag, near enough that a drag feels immediate. */
	private static final double DRAG_SLOP = 4;

	private static void used(HudModule m) {
		lastUsed().put(m.id(), System.currentTimeMillis());
		ModuleManager.save();
	}

	/** Left of the search box, square: the mode is worth a word, not a permanent one. */
	private HudModule.Rect sortRect() {
		return new HudModule.Rect(mainX + mainW - 110 - 6 - 14, py + HEADER_H + 6, 14, 14);
	}

	private HudModule.Rect chipRect(int index) {
		int x = mainX;
		for (int i = 0; i < index; i++) {
			x += Ui.capsW(categories.get(i), 0.6f) + 12 + 5;
		}
		return new HudModule.Rect(x, py + HEADER_H + 6, Ui.capsW(categories.get(index), 0.6f) + 12, 14);
	}

	private int cardX(int i) {
		return mainX + (i % COLS) * (cardW + GAP);
	}

	private int cardY(int i) {
		return gridY0 + (i / COLS) * (CARD_H + GAP) - (int) scroll;
	}

	private int contentHeight(int count) {
		int rows = (count + COLS - 1) / COLS;
		return Math.max(0, rows * (CARD_H + GAP) - GAP);
	}

	@Override
	protected void renderMain(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		for (int i = 0; i < categories.size(); i++) {
			HudModule.Rect cr = chipRect(i);
			boolean active = categories.get(i).equals(activeCategory);
			boolean hover = cr.contains(mouseX, mouseY);
			int fill = active ? Ui.accentDim() : (hover ? 0x1EFFFFFF : 0x14FFFFFF);
			Draw.smoothRounded(g, (int) cr.x(), (int) cr.y(), (int) cr.w(), (int) cr.h(), 3, fill);
			Ui.caps(g, categories.get(i), (int) cr.x() + 6, (int) cr.y() + 3,
					active ? 0xFFFFFFFF : 0xFFC2C2CC, 0.6f);
		}
		HudModule.Rect sr = sortRect();
		boolean sortHover = sr.contains(mouseX, mouseY);
		Draw.smoothRounded(g, (int) sr.x(), (int) sr.y(), (int) sr.w(), (int) sr.h(), 3,
				sortHover ? 0x1EFFFFFF : 0x14FFFFFF);
		// Three bars, longest first: the shape everything else uses for sorting, drawn rather
		// than added to the icon atlas for one glyph.
		int bx = (int) sr.x() + 4;
		int by = (int) sr.y() + 4;
		int tint = sortHover ? 0xFFFFFFFF : 0xFFC2C2CC;
		for (int b = 0; b < 3; b++) {
			g.fill(bx, by + b * 3, bx + 6 - b * 2, by + b * 3 + 1, tint);
		}
		if (sortHover) {
			setTooltip("Sort: " + sort(), mouseX, mouseY);
		}

	}

	/** Where the carried card would land: its shape in outline, with nothing inside it. */
	private void drawGap(GuiGraphicsExtractor g, int x, int y) {
		int edge = Ui.accent();
		// The cards own corner, so the space reads as one of them rather than a box in the grid.
		int r = 4;
		int dash = 4;
		int gapLen = 3;
		// Each dash rounded like the cards themselves, rather than a hard bar.
		int cap = 1;
		for (int i = x + r; i < x + cardW - r; i += dash + gapLen) {
			int w = Math.min(dash, x + cardW - r - i);
			Draw.smoothRounded(g, i, y, w, 1, cap, edge);
			Draw.smoothRounded(g, i, y + CARD_H - 1, w, 1, cap, edge);
		}
		for (int i = y + r; i < y + CARD_H - r; i += dash + gapLen) {
			int h = Math.min(dash, y + CARD_H - r - i);
			Draw.smoothRounded(g, x, i, 1, h, cap, edge);
			Draw.smoothRounded(g, x + cardW - 1, i, 1, h, cap, edge);
		}
		// The corners themselves as short steps, so the rounding is there without a curve to dash.
		int[][] corner = {{x + 1, y + r - 2, 1, 2}, {x + r - 2, y + 1, 2, 1},
				{x + cardW - 2, y + r - 2, 1, 2}, {x + cardW - r, y + 1, 2, 1},
				{x + 1, y + CARD_H - r, 1, 2}, {x + r - 2, y + CARD_H - 2, 2, 1},
				{x + cardW - 2, y + CARD_H - r, 1, 2}, {x + cardW - r, y + CARD_H - 2, 2, 1}};
		for (int[] c : corner) {
			Draw.smoothRounded(g, c[0], c[1], c[2], c[3], cap, edge);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(g, mouseX, mouseY, partialTick);

		List<HudModule> mods = filtered();
		boolean carrying = dragging && pressed != null;
		if (carrying) {
			// The card being carried leaves the list, and the others close up around a gap
			// where it would land: what you are looking at is the answer, not a hint at it.
			mods = withoutDragged(pressed);
			int at = Math.max(0, Math.min(dropAt, mods.size()));
			mods.add(at, null);
		}
		g.enableScissor(mainX, gridY0, mainX + mainW, gridY1);
		for (int i = 0; i < mods.size(); i++) {
			int x = cardX(i);
			int y = cardY(i);
			if (y + CARD_H < gridY0 || y > gridY1) {
				continue;
			}
			HudModule m = mods.get(i);
			if (m == null) {
				drawGap(g, x, y);
			} else {
				renderCard(g, m, x, y, mouseX, mouseY);
			}
		}
		g.disableScissor();
		if (carrying) {
			// Drawn last and outside the scissor, so it is over everything and can be taken
			// past the edge of the list without being clipped in half.
			// Leaning into the direction of travel, easing back to level when it stops: the
			// weight of the thing is what says it is being carried rather than just drawn.
			float lean = (float) (mouseX - lastCarryX) * 2.2f;
			swing += (Math.max(-14f, Math.min(14f, lean)) - swing) * 0.35f;
			lastCarryX = mouseX;
			int cx = (int) (mouseX - grabDx);
			int cy = (int) (mouseY - grabDy);
			g.pose().pushMatrix();
			g.pose().rotateAbout((float) Math.toRadians(swing), cx + cardW / 2f, cy + CARD_H / 2f);
			renderCard(g, pressed, cx, cy, -1, -1);
			g.pose().popMatrix();
		}

		int viewH = gridY1 - gridY0;
		int content = contentHeight(mods.size());
		if (content > viewH) {
			int barH = Math.max(14, viewH * viewH / content);
			int barY = gridY0 + (int) ((viewH - barH) * (scroll / (content - viewH)));
			Draw.smoothRounded(g, px + pw - 6, barY, 2, barH, 1, 0x50FFFFFF);
		}
	}

	private void renderCard(GuiGraphicsExtractor g, HudModule m, int x, int y, int mouseX, int mouseY) {
		boolean on = m.isEnabled();
		boolean inGrid = mouseY >= gridY0 && mouseY <= gridY1;
		boolean hover = inGrid && mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + CARD_H;

		int fill = hover ? 0x3D0A0A0C : 0x2E000000;
		int border = on ? Ui.GOOD_BORDER : Ui.BAD_BORDER;
		Draw.smoothRoundedBordered(g, x, y, cardW, CARD_H, 4, fill, border, 1);

		// Icon slot: the module's own icon, or its initial if it has not been given one. Drawn at
		// 14px — at 10px a 2px stroke lands under a pixel and the icons go to grey mush.
		int iconTint = on ? Ui.TEXT : Ui.TEXT_DIM;
		int textX = x + 22;
		if (!ModuleIcons.draw(g, m.id(), x + 4, y + (CARD_H - 14) / 2, 14, iconTint)) {
			Ui.str(g, m.displayName().substring(0, 1).toUpperCase(Locale.ROOT), x + 7,
					y + (CARD_H - 9) / 2 + 1, iconTint);
			textX = x + 17;
		}

		int gearX = x + cardW - 15;
		String name = trim(m.displayName(), gearX - 7 - textX);
		Ui.str(g, name, textX, y + (CARD_H - 9) / 2 + 1, on ? Ui.TEXT : Ui.TEXT_DIM);

		g.fill(gearX - 5, y + 5, gearX - 4, y + CARD_H - 5, 0x1AFFFFFF);
		if (Textures.ensure()) {
			int n = Textures.SIZE;
			boolean overGear = hover && mouseX >= gearX - 4;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.GEAR, gearX, y + (CARD_H - 10) / 2,
					0f, 0f, 10, 10, n, n, n, n, overGear ? 0xFFFFFFFF : (on ? Ui.GOOD : 0xFF62626B));
		}
	}

	private String trim(String s, int maxW) {
		if (Ui.sw(s) <= maxW) {
			return s;
		}
		while (s.length() > 1 && Ui.sw(s + "…") > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (pressed != null && !pressedGear && sort().equals("CUSTOM")
				&& (Math.abs(e.x() - pressX) > DRAG_SLOP || Math.abs(e.y() - pressY) > DRAG_SLOP)) {
			if (!dragging) {
				lastCarryX = e.x();
				swing = 0f;
			}
			dragging = true;
		}
		if (dragging) {
			dropAt = dropIndexAt(e.x(), e.y());
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		HudModule m = pressed;
		boolean wasDrag = dragging;
		int at = dropAt;
		pressed = null;
		dragging = false;
		dropAt = -1;
		if (m == null) {
			return super.mouseReleased(e);
		}
		if (wasDrag && at >= 0) {
			moveTo(m, at);
			return true;
		}
		// Never moved, so it was a click after all.
		used(m);
		if (pressedGear) {
			minecraft.gui.setScreen(m.settingsScreen(this));
		} else {
			m.settings().enabled = !m.settings().enabled;
		}
		return true;
	}

	/**
	 * Which slot the pointer is over, clamped to the list.
	 *
	 * <p>Dragged past the end or off the side it lands at the nearest real place rather than
	 * nowhere: a card can be moved but never lost.
	 */
	private int dropIndexAt(double mx, double my) {
		List<HudModule> mods = withoutDragged(pressed);
		if (mods.isEmpty()) {
			return -1;
		}
		// The slot the cursor is in, measured off the grid itself.
		//
		// Not the nearest card, which is what this used to do: while a card is being carried the
		// list is drawn with a GAP at the landing place, so every card past that gap is drawn one
		// slot on from where a gapless measurement puts it. Hit-testing against the gapless
		// positions while the player looks at the gapped ones is why moving a card one place to
		// the right meant dropping it two places along, and why the other direction — where the
		// gap falls after the cursor and nothing shifts — behaved.
		int col = Math.max(0, Math.min(COLS - 1,
				(int) Math.floor((mx - mainX) / (double) (cardW + GAP))));
		int row = Math.max(0, (int) Math.floor((my - gridY0 + scroll) / (double) (CARD_H + GAP)));
		return Math.max(0, Math.min(row * COLS + col, mods.size()));
	}

	/**
	 * The list as it is drawn while carrying one: everything but the card in hand.
	 *
	 * <p>The carried card is passed in rather than read off the pressed field. mouseReleased
	 * clears that field before it works out where the card goes, so this used to be handed a null
	 * and remove nothing -- leaving the dragged card still in the list it was being placed into.
	 * Moving a card forwards then landed it one slot short, because its own presence pushed the
	 * target along by one; moving backwards was unaffected, since the card sits after the target
	 * either way. That is a field lifetime to get wrong once, so there is no longer one to read.
	 */
	private List<HudModule> withoutDragged(HudModule dragged) {
		List<HudModule> mods = new ArrayList<>(filtered());
		mods.remove(dragged);
		return mods;
	}

	/** Puts a module at a place in the users own order, switching to it if this is the first. */
	private void moveTo(HudModule m, int index) {
		List<HudModule> mods = withoutDragged(m);
		if (order().isEmpty()) {
			// Seeded from what is on screen, so the first drag moves one card rather than
			// rearranging everything around it.
			for (HudModule x : ModuleManager.all()) {
				order().add(x.id());
			}
		}
		String target = index < mods.size() ? mods.get(index).id() : null;
		// Read against the same list the gap was found in, or the card lands a place out.
		List<String> order = order();
		order.remove(m.id());
		int at = target == null ? order.size() : Math.max(0, order.indexOf(target));
		order.add(Math.min(at, order.size()), m.id());
		ClientifyConfig.listPrefs().sort = "CUSTOM";
		ModuleManager.save();
	}

	@Override
	protected boolean mainClicked(MouseButtonEvent e) {
		if (sortRect().contains(e.x(), e.y())) {
			int at = java.util.Arrays.asList(SORTS).indexOf(sort());
			ClientifyConfig.listPrefs().sort = SORTS[(at + 1) % SORTS.length];
			ModuleManager.save();
			scroll = 0;
			return true;
		}
		for (int i = 0; i < categories.size(); i++) {
			if (chipRect(i).contains(e.x(), e.y())) {
				activeCategory = categories.get(i);
				scroll = 0;
				return true;
			}
		}
		if (e.y() >= gridY0 && e.y() <= gridY1) {
			List<HudModule> mods = filtered();
			for (int i = 0; i < mods.size(); i++) {
				int x = cardX(i);
				int y = cardY(i);
				if (e.x() >= x && e.x() < x + cardW && e.y() >= y && e.y() < y + CARD_H) {
					// Held rather than acted on: this press might turn out to be a drag, and
					// toggling on the way into one would be maddening.
					pressed = mods.get(i);
					pressedGear = e.x() >= x + cardW - 19;
					pressX = e.x();
					pressY = e.y();
					grabDx = e.x() - x;
					grabDy = e.y() - y;
					dragging = false;
					dropAt = -1;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hDelta, double vDelta) {
		if (super.mouseScrolled(mx, my, hDelta, vDelta)) {
			return true;
		}
		if (my >= gridY0 && my <= gridY1) {
			int viewH = gridY1 - gridY0;
			double max = Math.max(0, contentHeight(filtered().size()) - viewH);
			scroll = Math.max(0, Math.min(max, scroll - vDelta * 20));
			return true;
		}
		return false;
	}
}
