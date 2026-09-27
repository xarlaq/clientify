package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.config.ModuleSettings.FontMode;
import dev.clientify.client.gui.ItemCounterSettingsScreen;
import dev.clientify.client.gui.ModListScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.BlurBackdrop;
import dev.clientify.client.hud.ChromeMask;
import dev.clientify.client.hud.HudFrame;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Counts chosen items in your inventory. Each item is its own chip — dragged and formatted
 * separately, the way custom text boxes are — showing any of the item's icon, its name and how
 * many you are carrying. "Inventory Style" puts the count on the icon the way a slot does, and is
 * only offered when there is both an icon to put it on and a count to show.
 */
public class ItemCounterModule extends HudModule {
	private static final int ICON = 16;
	private static final int GAP = 3;
	private static final int PAD = 4;

	/** Vertical step between entries when their positions are reset, so they do not stack. */
	private static final float RESET_STRIDE = 12f;

	/** One counted item, with its own place on screen and its own formatting. */
	public static class Entry {
		public String item = "";
		public ModuleSettings.PartPos pos = new ModuleSettings.PartPos();
		public float scale = 1f;
		public FontMode font = FontMode.MINECRAFT;
		public boolean shadow = true;
		public boolean showIcon = true;
		public boolean showName = false;
		public boolean showCount = true;
		public boolean inventoryStyle = true;
		public boolean hideEmpty = false;
		public ColorSpec nameColor = new ColorSpec("#FFFFFF");
		public ColorSpec countColor = new ColorSpec("#FFFFFF");
		public boolean background = true;
		public ColorSpec bgColor = new ColorSpec("#8C101014");
		public int bgWidth;
		public int bgHeight;
		public boolean bgRounded;
		public int bgRadius = 6;
		public boolean bgBlur;
		/** A line round the chip, drawn outward the way every other module draws one. */
		public boolean border;
		public float borderThickness = 1f;
		public ColorSpec borderColor = new ColorSpec("#F35D12");

		public Entry() {
		}

		public Entry(String item, float ox, float oy) {
			this.item = item;
			pos.ox = ox;
			pos.oy = oy;
		}

		/** True when the count belongs on the icon rather than beside the text. */
		public boolean countOnIcon() {
			return inventoryStyle && showIcon && showCount;
		}
	}

	public static class Settings extends ModuleSettings {
		public List<Entry> entries = new ArrayList<>();

		/**
		 * Every counted item takes the module defaults, since an entry is where this module's
		 * appearance lives.
		 *
		 * <p>Without this, Apply To All Modules wrote into the module-wide fields that this module
		 * does not read, and the button appeared to do nothing here at all.
		 *
		 * <p>An entry has a name colour and a count colour, which is the same pair the defaults
		 * hold as label and value, so those carry across as themselves.
		 */
		@Override
		public void applyAppearanceFrom(ModuleSettings o) {
			super.applyAppearanceFrom(o);
			for (Entry entry : entries) {
				entry.font = o.font;
				entry.shadow = o.textShadow;
				entry.background = o.background;
				entry.bgColor.copyFrom(o.bgColor);
				entry.bgWidth = o.bgWidth;
				entry.bgHeight = o.bgHeight;
				entry.bgRounded = o.bgRounded;
				entry.bgRadius = o.bgRadius;
				entry.bgBlur = o.bgBlur;
				entry.border = o.border;
				entry.borderThickness = o.borderThickness;
				entry.borderColor.copyFrom(o.borderColor);
				entry.nameColor.copyFrom(o.labelColor);
				entry.countColor.copyFrom(o.valueColor);
			}
		}

		/**
		 * Each entry carries its own position and the module offset is unused, so without this the
		 * button would do nothing here. They are stacked down from the default spot rather than all
		 * dropped on it, which would read as the reset having merged them.
		 */
		@Override
		public void applyPositionFrom(ModuleSettings o) {
			super.applyPositionFrom(o);
			ModuleSettings.PartPos start = new Entry().pos;
			for (int i = 0; i < entries.size(); i++) {
				ModuleSettings.PartPos p = entries.get(i).pos;
				p.copyPositionFrom(start);
				p.oy += i * RESET_STRIDE;
			}
		}
	}

	/** Scratch settings view so HudText draws with an entry's own font, scale and shadow. */
	private final ModuleSettings scratch = new ModuleSettings();
	private int countToken = -1;
	private final java.util.Map<String, Integer> counts = new java.util.HashMap<>();

	public ItemCounterModule() {
		super("itemcounter", "Item Counter");
	}

	@Override
	public String description() {
		return "Counts chosen items in your inventory.";
	}

	@Override
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	@Override
	public boolean hasAppearance() {
		return false; // every entry carries its own
	}

	/**
	 * Blur is per counted entry here, and the module-wide background fields are not shown.
	 *
	 * <p>This has to agree with what render actually blurs. wantsBlur does not gate the capture --
	 * it decides whether the blur RADIUS is overridden for the frame -- so a module that draws a
	 * blurred chip while reporting false gets the captured world unblurred, a sharp copy of the
	 * scene. That only looked right when some other module happened to want blur too.
	 */
	@Override
	public boolean wantsBlur() {
		for (Entry entry : ((Settings) settings()).entries) {
			if (entry.background && entry.bgBlur) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean hasScaleSlider() {
		return false;
	}

	@Override
	public net.minecraft.client.gui.screens.Screen settingsScreen(ModListScreen list) {
		return new ItemCounterSettingsScreen(list, this);
	}

	private Settings settings2() {
		return (Settings) settings();
	}

	private ModuleSettings view(Entry entry) {
		scratch.font = entry.font;
		scratch.scale = entry.scale;
		scratch.textShadow = entry.shadow;
		return scratch;
	}

	/** An entry with nothing drawn behind it measures its content exactly — no dead margin. */
	private static boolean padded(Entry entry) {
		return entry.background || entry.border;
	}

	// ---- counting ----

	/** True when an inventory stack is the item being tracked (potions match by their brew). */
	private static boolean matches(ItemStack held, ItemStack target) {
		if (held.isEmpty() || held.getItem() != target.getItem()) {
			return false;
		}
		var wanted = target.get(DataComponents.POTION_CONTENTS);
		return wanted == null || wanted.equals(held.get(DataComponents.POTION_CONTENTS));
	}

	/** How many of {@code entry} are carried. Counted once per render pass, not per measurement. */
	private int count(Minecraft mc, Entry entry) {
		int token = HudFrame.token();
		if (token != countToken) {
			counts.clear();
			countToken = token;
		}
		Integer cached = counts.get(entry.item);
		if (cached != null) {
			return cached;
		}
		int total = 0;
		ItemStack target = WaypointsModule.iconStack(entry.item);
		if (mc.player != null && !target.isEmpty()) {
			Inventory inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (matches(stack, target)) {
					total += stack.getCount();
				}
			}
		}
		counts.put(entry.item, total);
		return total;
	}

	private String countText(Minecraft mc, Entry entry) {
		return Integer.toString(count(mc, entry));
	}

	// ---- layout ----

	/**
	 * Width of the text beside the icon. The vanilla font's advance runs a pixel past the last
	 * glyph, so measuring by advance and then padding both sides evenly would sit the text left of
	 * centre — the same slack {@link HudText#trailing} exists for. Only the final overhang is
	 * dropped; the ones between words are real spacing.
	 */
	private float textWidth(Minecraft mc, Entry entry, ModuleSettings v, float scale) {
		float w = 0;
		if (entry.showName) {
			w += HudText.width(mc, v, displayName(entry), scale);
		}
		if (entry.showCount && !entry.countOnIcon()) {
			if (entry.showName) {
				w += HudText.width(mc, v, " ", scale);
			}
			w += HudText.width(mc, v, countText(mc, entry), scale);
		}
		return w > 0 ? w - HudText.trailing(v, scale) : 0;
	}

	private float textHeight(Minecraft mc, ModuleSettings v, float scale) {
		return HudText.lineHeight(mc, v, scale) - HudText.trailing(v, scale);
	}

	/** Screen-space size of one entry's chip, at that entry's own scale. */
	private float[] chipSize(Minecraft mc, Entry entry) {
		ModuleSettings v = view(entry);
		float scale = entry.scale;
		float w = 0;
		float h = 0;
		if (entry.showIcon) {
			w += ICON * scale;
			h = ICON * scale;
		}
		float textW = textWidth(mc, entry, v, scale);
		if (textW > 0) {
			w += (entry.showIcon ? GAP * scale : 0) + textW;
			h = Math.max(h, textHeight(mc, v, scale));
		}
		return new float[] {w + ModuleSettings.extra(padded(entry), PAD, entry.bgWidth) * scale,
				h + ModuleSettings.extra(padded(entry), PAD, entry.bgHeight) * scale};
	}

	private String displayName(Entry entry) {
		ItemStack stack = WaypointsModule.iconStack(entry.item);
		return stack.isEmpty() ? entry.item : stack.getHoverName().getString();
	}

	private boolean visible(Minecraft mc, Entry entry) {
		if (WaypointsModule.iconStack(entry.item).isEmpty()) {
			return false;
		}
		return !entry.hideEmpty || count(mc, entry) > 0
				|| mc.gui.screen() instanceof dev.clientify.client.gui.HudEditorScreen;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		return 0; // positioned per entry
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return 0;
	}

	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		Settings s = settings2();
		List<Draggable> out = new ArrayList<>(s.entries.size());
		for (Entry entry : s.entries) {
			if (!visible(mc, entry)) {
				continue;
			}
			float[] size = chipSize(mc, entry);
			out.add(new Draggable() {
				@Override
				public Rect bounds() {
					return partBounds(entry.pos, size[0], size[1], screenW, screenH);
				}

				@Override
				public void moveTo(float x, float y) {
					partMoveTo(entry.pos, x, y, size[0], size[1], screenW, screenH);
				}

				@Override
				public void scaleBy(float delta) {
					entry.scale = clampScale(entry.scale + delta);
				}
			});
		}
		return out;
	}

	// ---- settings rows ----

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		// Everything is per entry; the list itself lives in ItemCounterSettingsScreen.
	}

	/** The options card for one counted item, two switches to a line to keep it short. */
	public void appendEntryRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows, int index) {
		Settings s = settings2();
		if (index >= s.entries.size()) {
			return;
		}
		Entry e = s.entries.get(index);
		Entry d = new Entry();
		rows.add(screen.dualToggle("Show Icon", () -> e.showIcon, v -> e.showIcon = v,
				"Show Name", () -> e.showName, v -> e.showName = v));
		// Inventory Style only makes sense with an icon to put the number on.
		boolean canStack = e.showIcon && e.showCount;
		rows.add(screen.dualToggle("Show Count", () -> e.showCount, v -> e.showCount = v,
				canStack ? "Inventory Style" : null, () -> e.inventoryStyle, v -> e.inventoryStyle = v));
		rows.add(screen.dualToggle("Text Shadow", () -> e.shadow, v -> e.shadow = v,
				"Hide At Zero", () -> e.hideEmpty, v -> e.hideEmpty = v));
		rows.add(screen.sliderRow("Scale", 0.5f, 3f, 0.05f, () -> e.scale, v -> e.scale = v, "%.2f",
				() -> e.scale = d.scale));
		rows.add(screen.cycleRow("Font", () -> e.font.label(),
				() -> e.font = cycle(e.font, -1), () -> e.font = cycle(e.font, 1),
				() -> e.font = d.font));
		if (e.showName) {
			screen.addColorRows(rows, "Name Color", () -> e.nameColor, () -> e.nameColor.copyFrom(d.nameColor));
		}
		if (e.showCount) {
			screen.addColorRows(rows, "Count Color", () -> e.countColor, () -> e.countColor.copyFrom(d.countColor));
		}
		rows.add(screen.toggle("Background", () -> e.background, v -> e.background = v,
				() -> e.background = d.background));
		if (e.background) {
			screen.addColorRows(rows, "Background Color", () -> e.bgColor, () -> e.bgColor.copyFrom(d.bgColor));
			rows.add(screen.sliderRow("Extra Width", 0f, 40f, 1f, () -> (float) e.bgWidth,
					v -> e.bgWidth = Math.round(v), "%.0f", () -> e.bgWidth = d.bgWidth));
			rows.add(screen.sliderRow("Extra Height", 0f, 20f, 1f, () -> (float) e.bgHeight,
					v -> e.bgHeight = Math.round(v), "%.0f", () -> e.bgHeight = d.bgHeight));
			rows.add(screen.blurToggle("Background Blur", () -> e.bgBlur, v -> e.bgBlur = v,
					() -> e.bgBlur = d.bgBlur));
			rows.add(screen.roundedRow(() -> e.bgRounded, v -> e.bgRounded = v, () -> e.bgRadius,
					v -> e.bgRadius = v, () -> {
						e.bgRounded = d.bgRounded;
						e.bgRadius = d.bgRadius;
					}));
		}
		// Outside the background block on purpose: a line with nothing behind it is a look,
		// and every other module lets you have it.
		rows.add(screen.sliderRow("Border Thickness", 0f, 5f, 1f,
				() -> e.border ? e.borderThickness : 0f, v -> {
					e.border = v >= 1f;
					if (e.border) {
						e.borderThickness = v;
					}
				}, "%.0f", () -> {
					e.border = d.border;
					e.borderThickness = d.borderThickness;
				}));
		if (e.border) {
			screen.addColorRows(rows, "Border Color", () -> e.borderColor,
					() -> e.borderColor.copyFrom(d.borderColor));
		}
	}

	// ---- render ----

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		for (Entry entry : settings2().entries) {
			if (!visible(mc, entry)) {
				continue;
			}
			float[] size = chipSize(mc, entry);
			Rect r = partBounds(entry.pos, size[0], size[1], g.guiWidth(), g.guiHeight());
			drawChip(g, mc, entry, r);
		}
	}

	private void drawChip(GuiGraphicsExtractor g, Minecraft mc, Entry entry, Rect r) {
		// Content is laid out inside the background as DRAWN, not as measured: the fill lands on
		// whole pixels, and centring against the unrounded rect leaves it half a pixel off.
		int bx = Math.round(r.x());
		int by = Math.round(r.y());
		int bw = Math.round(r.w());
		int bh = Math.round(r.h());
		if (entry.background || entry.border) {
			int rad = entry.bgRounded ? Math.round(Math.max(0, entry.bgRadius) * entry.scale) : 0;
			int argb = entry.bgColor.argbAt(0, 1);
			int bc = entry.borderColor.argbAt(0, 1);
			int t = entry.border ? Math.max(1, Math.round(entry.borderThickness * entry.scale)) : 0;
			boolean blur = entry.background && entry.bgBlur && BlurBackdrop.prepare(mc);
			boolean fill = entry.background;
			Runnable paint = () -> {
				if (fill) {
					paintChip(g, bx, by, bw, bh, rad, argb, blur);
				}
				if (t > 0) {
					// Outward, so the line sits around the chip rather than eating into it.
					Draw.thickBorder(g, bx - t, by - t, bw + t * 2, bh + t * 2,
							rad == 0 ? 0 : rad + t, t, bc);
				}
			};
			if (ChromeMask.enabled()) {
				ChromeMask.draw(g, r.x() - t, r.y() - t, r.w() + t * 2, r.h() + t * 2,
						rad == 0 ? 0 : rad + t, paint);
			} else {
				paint.run();
			}
		}

		ModuleSettings v = view(entry);
		float scale = entry.scale;
		ItemStack stack = WaypointsModule.iconStack(entry.item);
		String count = countText(mc, entry);
		float padX = ModuleSettings.inset(padded(entry), PAD, entry.bgWidth) * scale;
		float padY = ModuleSettings.inset(padded(entry), PAD, entry.bgHeight) * scale;
		float x = bx + padX;
		float contentH = bh - ModuleSettings.extra(padded(entry), PAD, entry.bgHeight) * scale;

		if (entry.showIcon) {
			float iconY = by + padY + (contentH - ICON * scale) / 2f;
			g.pose().pushMatrix();
			g.pose().translate(x, iconY);
			g.pose().scale(scale, scale);
			g.item(stack, 0, 0);
			g.pose().popMatrix();
			if (entry.countOnIcon()) {
				// Bottom-right of the icon like a slot, but the number always shows: a count of one
				// is exactly what someone tracking an item wants to see, and vanilla hides it.
				float tw = HudText.width(mc, v, count, scale);
				HudText.draw(g, mc, v, count, x + ICON * scale - tw,
						iconY + (ICON - 8) * scale, entry.countColor, 0, count.length(), scale);
			}
			x += (ICON + GAP) * scale;
		}

		float textY = by + padY + (contentH - textHeight(mc, v, scale)) / 2f;
		if (entry.showName) {
			String name = displayName(entry);
			HudText.draw(g, mc, v, name, x, textY, entry.nameColor, 0, name.length(), scale);
			x += HudText.width(mc, v, name, scale)
					+ (entry.showCount && !entry.countOnIcon() ? HudText.width(mc, v, " ", scale) : 0);
		}
		if (entry.showCount && !entry.countOnIcon()) {
			HudText.draw(g, mc, v, count, x, textY, entry.countColor, 0, count.length(), scale);
		}
	}

	private void paintChip(GuiGraphicsExtractor g, int x, int y, int w, int h, int rad, int argb, boolean blur) {
		if (blur) {
			Draw.backdropRounded(g, BlurBackdrop.TEXTURE_ID, x, y, w, h, rad,
					0, 0, 1f, g.guiWidth(), g.guiHeight(), BlurBackdrop.vFlip());
		}
		Draw.smoothRounded(g, x, y, w, h, rad, argb);
	}
}
