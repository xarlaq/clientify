package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Worn armor (+ optional hands) with durability text, an optional item name, an optional
 * vanilla durability bar, and rich coloring: per-segment (current / "/" / max / "%"), static
 * per-damage-tier value colors, or a single value color — all through the module font with
 * working wave/gradient effects.
 */
public class ArmorHudModule extends HudModule {
	public enum Layout {
		VERTICAL, HORIZONTAL
	}

	public enum DuraFormat {
		PERCENT, REMAINING, FRACTION, NONE
	}

	/** Where the text sits relative to the icon (vertical layout only). */
	public enum TextPos {
		RIGHT, LEFT, ABOVE, UNDER
	}

	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final int PAD = 3;
	private static final int ICON = 16;
	/**
	 * One slot of the hotbar, borders included: 3px border, 16px interior, 3px border.
	 */
	private static final int SLOT = 22;
	/**
	 * How far one slot advances the next in a connected strip.
	 *
	 * <p>Twenty, not twenty-two: adjacent slots share their divider. The sprite is 182 wide for nine
	 * slots, and 2 + 9*20 = 182 exactly.
	 */
	private static final int PITCH = 20;
	private static final int HOTBAR_W = 182;
	private static final int HOTBAR_H = 22;
	/** The 1px end cap at the right of the sprite, which closes a strip off. */
	private static final int CAP = 1;
	/**
	 * The hotbar itself, sliced.
	 *
	 * <p>Tiling a standalone box instead — the gamemode switcher slot was the first attempt — cannot
	 * look like this: two bevelled boxes meeting give a doubled divider and leave their corner pixels
	 * showing as notches. Slicing the real bar means the dividers and end caps ARE vanilla's, and a
	 * resource pack restyles them with the hotbar it already restyles.
	 */
	private static final net.minecraft.resources.Identifier HOTBAR =
			net.minecraft.resources.Identifier.withDefaultNamespace("hud/hotbar");
	private static final int GAP = 3;
	/**
	 * The ghost outlines vanilla puts in an empty armour slot, in ARMOR order then main / off hand.
	 * Sixteen square, the same as an item, so they sit exactly where the item would.
	 */
	private static final net.minecraft.resources.Identifier[] EMPTY_ARMOR = {
			slotSprite("helmet"), slotSprite("chestplate"), slotSprite("leggings"), slotSprite("boots")
	};
	private static final net.minecraft.resources.Identifier EMPTY_MAIN = slotSprite("sword");
	private static final net.minecraft.resources.Identifier EMPTY_OFF = slotSprite("shield");

	private static net.minecraft.resources.Identifier slotSprite(String name) {
		return net.minecraft.resources.Identifier.withDefaultNamespace("container/slot/" + name);
	}

	public static class Settings extends ModuleSettings {
		public Layout layout = Layout.VERTICAL;
		public DuraFormat durability = DuraFormat.REMAINING;
		public TextPos textPos = TextPos.RIGHT;
		public boolean durabilityBar = true;
		public boolean hideUnbreakable = false;
		/**
		 * A vanilla slot frame behind every piece, the way the hotbar draws one.
		 *
		 * <p>Separate from the module background, which is one chip behind the lot: this is one frame
		 * per piece, and it comes from a vanilla sprite so a resource pack restyles it too.
		 */
		public boolean slotBackground = false;
		/**
		 * Frames touching, the way the hotbar runs them together, rather than spaced apart.
		 *
		 * <p>Only means anything with frames on, and cannot apply at all once each piece carries its
		 * own position — there is no shared spacing left to close up.
		 */
		public boolean connectedSlots = true;
		/** Keep a slot for a piece you are not wearing, so the block does not resize as you take hits. */
		public boolean showEmpty = false;

		public boolean showMainHand = false;
		public boolean showOffHand = false;
		public boolean showHelmet = true;
		public boolean showChest = true;
		public boolean showLegs = true;
		public boolean showBoots = true;

		/** Base durability color (used unless a per-segment / damage-tier color overrides). */
		public ColorSpec mainColor = new ColorSpec("#FFFFFF");

		public boolean itemName = false;
		public ColorSpec nameColor = new ColorSpec("#FFFFFF");

		/** Separate colors for the sub-parts of the durability text. */
		public boolean separateColors = false;
		public ColorSpec currentColor = new ColorSpec("#FFFFFF");
		public ColorSpec slashColor = new ColorSpec("#9A9AA5");
		public ColorSpec maxColor = new ColorSpec("#FFFFFF");
		public ColorSpec percentColor = new ColorSpec("#9A9AA5");

		/** Static color per damage tier for the numeric value. */
		public boolean staticDamageColors = false;
		public ColorSpec dmgDefault = new ColorSpec("#FFFFFF");
		public ColorSpec dmgLow = new ColorSpec("#8CFF8C");
		public ColorSpec dmgMedium = new ColorSpec("#FFE24A");
		public ColorSpec dmgHigh = new ColorSpec("#FFA24A");
		public ColorSpec dmgLowest = new ColorSpec("#FF5A5A");

		/** When on, each piece is positioned independently in the editor. */
		public boolean separatePositions = false;
		public PartPos headPos = new PartPos(Anchor.MIDDLE_LEFT, 5, -30);
		public PartPos chestPos = new PartPos(Anchor.MIDDLE_LEFT, 5, -10);
		public PartPos legsPos = new PartPos(Anchor.MIDDLE_LEFT, 5, 10);
		public PartPos feetPos = new PartPos(Anchor.MIDDLE_LEFT, 5, 30);
		public PartPos mainPos = new PartPos(Anchor.MIDDLE_LEFT, 30, -10);
		public PartPos offPos = new PartPos(Anchor.MIDDLE_LEFT, 30, 10);

		@Override
		public void applyPositionFrom(ModuleSettings o) {
			super.applyPositionFrom(o);
			if (o instanceof Settings d) {
				headPos.copyPositionFrom(d.headPos);
				chestPos.copyPositionFrom(d.chestPos);
				legsPos.copyPositionFrom(d.legsPos);
				feetPos.copyPositionFrom(d.feetPos);
				mainPos.copyPositionFrom(d.mainPos);
				offPos.copyPositionFrom(d.offPos);
			}
		}

		public Settings() {
			anchor = Anchor.MIDDLE_LEFT;
			offsetX = 5;
			offsetY = 0;
			background = false;
		}
	}

	/** Kept out of the row so the line breaks stay readable. */
	private static final String SLOT_FRAMES_TIP =
			"A vanilla slot behind every piece, like the hotbar draws.\n"
					+ "One per piece, rather than the single chip the module\n"
					+ "background gives you — and it follows your resource pack.";

	private static final String CONNECTED_TIP =
			"Runs the frames together the way the hotbar does, instead\n"
					+ "of spacing them out. Needs Slot Frames, and cannot apply\n"
					+ "while each piece carries its own position.";

	/** One piece: what is worn, where it goes, and the outline to draw when nothing is worn. */
	private record PieceRef(ItemStack stack, ModuleSettings.PartPos pos,
			net.minecraft.resources.Identifier empty) {
	}

	/** Which color section's dropdown is expanded in settings (not saved). */
	private transient String expandedSection;

	public ArmorHudModule() {
		super("armorhud", "Armor HUD");
	}

	@Override
	public String description() {
		return "Shows your worn armor and its durability.";
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
	public boolean hasColorSection() {
		return false; // armor uses its own Main Color + per-segment / damage colors
	}

	private void expand(String key) {
		expandedSection = key.equals(expandedSection) ? null : key;
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		rows.add(screen.cycleRow("Layout",
				() -> s.layout == Layout.VERTICAL ? "Vertical" : "Horizontal",
				() -> s.layout = cycle(s.layout, -1), () -> s.layout = cycle(s.layout, 1),
				() -> s.layout = Layout.VERTICAL));
		if (s.layout == Layout.VERTICAL) {
			rows.add(screen.cycleRow("Text Position",
					() -> switch (s.textPos) {
						case RIGHT -> "Right";
						case LEFT -> "Left";
						case ABOVE -> "Above";
						case UNDER -> "Under";
					},
					() -> s.textPos = cycle(s.textPos, -1), () -> s.textPos = cycle(s.textPos, 1),
					() -> s.textPos = TextPos.RIGHT));
		}
		rows.add(screen.cycleRow("Durability Text",
				() -> switch (s.durability) {
					case PERCENT -> "Percent";
					case REMAINING -> "Remaining";
					case FRACTION -> "Current / Max";
					case NONE -> "None";
				},
				() -> s.durability = cycle(s.durability, -1), () -> s.durability = cycle(s.durability, 1),
				() -> s.durability = DuraFormat.REMAINING));
		screen.addColorRows(rows, "Main Color", () -> s.mainColor, () -> s.mainColor.copyFrom(d.mainColor));
		rows.add(screen.withTooltip(
				screen.dualToggle("Durability Bar", () -> s.durabilityBar, v -> s.durabilityBar = v,
						"Show Empty", () -> s.showEmpty, v -> s.showEmpty = v),
				"Vanilla's little durability bar on the corner of each piece.",
				"Keeps a slot for a piece you are not wearing, so the block\n"
						+ "stays the same size instead of resizing as armour breaks."));
		rows.add(screen.withTooltip(
				screen.dualToggle("Slot Frames", () -> s.slotBackground, v -> s.slotBackground = v,
						"Connected", () -> s.connectedSlots, v -> s.connectedSlots = v),
				SLOT_FRAMES_TIP,
				CONNECTED_TIP));
		if (s.slotBackground && s.connectedSlots && s.separatePositions) {
			rows.add(screen.note(() -> "Connected has no effect while each piece has its own position."));
		} else if (textSuppressed(s) && (s.itemName || s.durability != DuraFormat.NONE)) {
			rows.add(screen.note(() -> s.layout == Layout.HORIZONTAL
					? "Text is hidden while connected in a row. Use Vertical layout to keep it."
					: "Text is hidden while connected. Put it Left or Right to keep it."));
		}
		rows.add(screen.dualToggle("Helmet", () -> s.showHelmet, v -> s.showHelmet = v,
				"Chestplate", () -> s.showChest, v -> s.showChest = v));
		rows.add(screen.dualToggle("Leggings", () -> s.showLegs, v -> s.showLegs = v,
				"Boots", () -> s.showBoots, v -> s.showBoots = v));
		rows.add(screen.dualToggle("Main Hand", () -> s.showMainHand, v -> s.showMainHand = v,
				"Off Hand", () -> s.showOffHand, v -> s.showOffHand = v));
		rows.add(screen.dualToggle("Move Separately", () -> s.separatePositions,
				v -> s.separatePositions = v,
				"Hide Unbreakable", () -> s.hideUnbreakable, v -> s.hideUnbreakable = v));

		rows.add(screen.toggleGear("Item Name", () -> s.itemName, v -> s.itemName = v,
				() -> expand("name"), () -> "name".equals(expandedSection), () -> s.itemName = false));
		if ("name".equals(expandedSection)) {
			screen.groupCard(rows, group ->
					screen.addColorRows(group, "Name Color", () -> s.nameColor, () -> s.nameColor.copyFrom(d.nameColor)));
		}

		rows.add(screen.toggleGear("Separate Value Colors", () -> s.separateColors, v -> s.separateColors = v,
				() -> expand("sep"), () -> "sep".equals(expandedSection), () -> s.separateColors = false));
		if ("sep".equals(expandedSection)) {
			screen.groupCard(rows, group -> {
				screen.addColorRows(group, "Current Value", () -> s.currentColor,
						() -> s.currentColor.copyFrom(d.currentColor));
				screen.addColorRows(group, "Slash \"/\"", () -> s.slashColor, () -> s.slashColor.copyFrom(d.slashColor));
				screen.addColorRows(group, "Max Value", () -> s.maxColor, () -> s.maxColor.copyFrom(d.maxColor));
				screen.addColorRows(group, "Percent \"%\"", () -> s.percentColor,
						() -> s.percentColor.copyFrom(d.percentColor));
			});
		}

		rows.add(screen.toggleGear("Static Damage Colors", () -> s.staticDamageColors, v -> s.staticDamageColors = v,
				() -> expand("dmg"), () -> "dmg".equals(expandedSection), () -> s.staticDamageColors = false));
		if ("dmg".equals(expandedSection)) {
			screen.groupCard(rows, group -> {
				screen.addColorRows(group, "Damage Default", () -> s.dmgDefault, () -> s.dmgDefault.copyFrom(d.dmgDefault));
				screen.addColorRows(group, "Low Damage", () -> s.dmgLow, () -> s.dmgLow.copyFrom(d.dmgLow));
				screen.addColorRows(group, "Medium Damage", () -> s.dmgMedium, () -> s.dmgMedium.copyFrom(d.dmgMedium));
				screen.addColorRows(group, "High Damage", () -> s.dmgHigh, () -> s.dmgHigh.copyFrom(d.dmgHigh));
				screen.addColorRows(group, "Lowest Damage", () -> s.dmgLowest, () -> s.dmgLowest.copyFrom(d.dmgLowest));
			});
		}
	}

	private record TextSeg(String text, ColorSpec spec) {
	}

	private List<PieceRef> pieces(Minecraft mc) {
		Settings s = (Settings) settings();
		// In separated mode the editor shows a placeholder for every empty slot, so each
		// piece stays draggable even when nothing is equipped.
		boolean editorSeparate = s.separatePositions && mc.gui.screen() instanceof HudEditorScreen;
		List<PieceRef> out = new ArrayList<>(6);
		ModuleSettings.PartPos[] armorPos = {s.headPos, s.chestPos, s.legsPos, s.feetPos};
		net.minecraft.world.item.Item[] armorPlaceholder =
				{Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
		boolean[] armorShown = {s.showHelmet, s.showChest, s.showLegs, s.showBoots};
		for (int i = 0; i < ARMOR.length; i++) {
			if (!armorShown[i]) {
				continue;
			}
			ItemStack stack = mc.player != null ? mc.player.getItemBySlot(ARMOR[i]) : ItemStack.EMPTY;
			if (!stack.isEmpty()) {
				out.add(new PieceRef(stack, armorPos[i], EMPTY_ARMOR[i]));
			} else if (editorSeparate) {
				out.add(new PieceRef(new ItemStack(armorPlaceholder[i]), armorPos[i], EMPTY_ARMOR[i]));
			} else if (s.showEmpty) {
				// A real empty stack, not a placeholder item: the slot holds its place and draws
				// its frame, and lines() gives an empty stack no name and no durability.
				out.add(new PieceRef(ItemStack.EMPTY, armorPos[i], EMPTY_ARMOR[i]));
			}
		}
		if (s.showMainHand) {
			ItemStack stack = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;
			if (!stack.isEmpty()) {
				out.add(new PieceRef(stack, s.mainPos, EMPTY_MAIN));
			} else if (editorSeparate) {
				out.add(new PieceRef(new ItemStack(Items.IRON_SWORD), s.mainPos, EMPTY_MAIN));
			} else if (s.showEmpty) {
				out.add(new PieceRef(ItemStack.EMPTY, s.mainPos, EMPTY_MAIN));
			}
		}
		if (s.showOffHand) {
			ItemStack stack = mc.player != null ? mc.player.getOffhandItem() : ItemStack.EMPTY;
			if (!stack.isEmpty()) {
				out.add(new PieceRef(stack, s.offPos, EMPTY_OFF));
			} else if (editorSeparate) {
				out.add(new PieceRef(new ItemStack(Items.SHIELD), s.offPos, EMPTY_OFF));
			} else if (s.showEmpty) {
				out.add(new PieceRef(ItemStack.EMPTY, s.offPos, EMPTY_OFF));
			}
		}
		if (out.isEmpty() && mc.gui.screen() instanceof HudEditorScreen) {
			out.add(new PieceRef(new ItemStack(Items.IRON_CHESTPLATE), s.chestPos, EMPTY_ARMOR[1])); // placeholder
		}
		return out;
	}

	private List<ItemStack> stacks(Minecraft mc) {
		List<ItemStack> out = new ArrayList<>(6);
		for (PieceRef p : pieces(mc)) {
			out.add(p.stack());
		}
		return out;
	}

	private ColorSpec damageColor(float frac, Settings s) {
		if (frac > 0.75f) {
			return s.dmgDefault;
		}
		if (frac > 0.5f) {
			return s.dmgLow;
		}
		if (frac > 0.25f) {
			return s.dmgMedium;
		}
		if (frac > 0.1f) {
			return s.dmgHigh;
		}
		return s.dmgLowest;
	}

	/** Durability text as colored segments; empty when no dura is shown. */
	private List<TextSeg> duraSegs(ItemStack stack, Settings s) {
		if (s.durability == DuraFormat.NONE) {
			return List.of();
		}
		boolean damageable = stack.isDamageableItem();
		boolean unbreakable = !damageable && stack.has(DataComponents.MAX_DAMAGE);
		if (!damageable && !(unbreakable && !s.hideUnbreakable)) {
			return List.of();
		}
		int max = stack.getMaxDamage();
		int left = damageable ? max - stack.getDamageValue() : max;
		float frac = max <= 0 ? 1f : left / (float) max;

		ColorSpec base = s.mainColor;
		ColorSpec numSpec = s.staticDamageColors ? damageColor(frac, s)
				: (s.separateColors ? s.currentColor : base);
		ColorSpec maxSpec = s.staticDamageColors ? damageColor(frac, s)
				: (s.separateColors ? s.maxColor : base);
		ColorSpec sepSlash = s.separateColors ? s.slashColor
				: (s.staticDamageColors ? damageColor(frac, s) : base);
		ColorSpec sepPct = s.separateColors ? s.percentColor
				: (s.staticDamageColors ? damageColor(frac, s) : base);

		List<TextSeg> segs = new ArrayList<>(3);
		switch (s.durability) {
			case REMAINING -> segs.add(new TextSeg(Integer.toString(left), numSpec));
			case PERCENT -> {
				segs.add(new TextSeg(Math.round(frac * 100f) + "", numSpec));
				segs.add(new TextSeg("%", sepPct));
			}
			case FRACTION -> {
				segs.add(new TextSeg(Integer.toString(left), numSpec));
				segs.add(new TextSeg("/", sepSlash));
				segs.add(new TextSeg(Integer.toString(max), maxSpec));
			}
			default -> {
			}
		}
		return segs;
	}

	/** All text lines of a piece (optional name + dura), in unscaled font units. */
	private List<List<TextSeg>> lines(ItemStack stack, Settings s) {
		List<List<TextSeg>> lines = new ArrayList<>(2);
		if (textSuppressed(s)) {
			return lines; // no room for it beside a connected run
		}
		if (stack.isEmpty()) {
			return lines; // no name, no durability — getHoverName would say "Air"
		}
		if (s.itemName) {
			lines.add(List.of(new TextSeg(stack.getHoverName().getString(), s.nameColor)));
		}
		List<TextSeg> dura = duraSegs(stack, s);
		if (!dura.isEmpty()) {
			lines.add(dura);
		}
		return lines;
	}

	private float segsWidth(Minecraft mc, Settings s, List<TextSeg> segs) {
		return segsWidth(mc, s, segs, s.scale);
	}

	/** Unscaled width measured AT {@code scale} — the atlas font is not perfectly linear. */
	private float segsWidth(Minecraft mc, Settings s, List<TextSeg> segs, float scale) {
		float w = 0;
		for (TextSeg seg : segs) {
			w += HudText.width(mc, s, seg.text(), scale) / scale;
		}
		return w;
	}

	private float linesWidth(Minecraft mc, Settings s, List<List<TextSeg>> lines) {
		return linesWidth(mc, s, lines, s.scale);
	}

	private float linesWidth(Minecraft mc, Settings s, List<List<TextSeg>> lines, float scale) {
		float w = 0;
		for (List<TextSeg> line : lines) {
			w = Math.max(w, segsWidth(mc, s, line, scale));
		}
		return w;
	}

	private float lineH(Minecraft mc, Settings s) {
		return lineH(mc, s, s.scale);
	}

	private float lineH(Minecraft mc, Settings s, float scale) {
		return HudText.lineHeight(mc, s, scale) / scale;
	}

	/** Max text-block width across pieces (for a consistent icon column). */
	private float maxTextW(Minecraft mc, Settings s, List<ItemStack> stacks) {
		float w = 0;
		for (ItemStack stack : stacks) {
			w = Math.max(w, linesWidth(mc, s, lines(stack, s)));
		}
		return w;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		Settings s = (Settings) settings();
		List<ItemStack> stacks = stacks(mc);
		if (stacks.isEmpty()) {
			return 0;
		}
		float tw = maxTextW(mc, s, stacks);
		if (s.layout == Layout.HORIZONTAL) {
			float cell = Math.max(cell(s), tw);
			int gap = rowPieceGap(s, tw);
			return stacks.size() * (cell + gap) - gap + s.extraW(PAD);
		}
		float rowW = switch (s.textPos) {
			case RIGHT, LEFT -> cell(s) + (tw > 0 ? GAP + tw : 0);
			case ABOVE, UNDER -> Math.max(cell(s), tw);
		};
		return rowW + s.extraW(PAD);
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		Settings s = (Settings) settings();
		List<ItemStack> stacks = stacks(mc);
		if (stacks.isEmpty()) {
			return 0;
		}
		float lh = lineH(mc, s);
		if (s.layout == Layout.HORIZONTAL) {
			int maxLines = 0;
			for (ItemStack stack : stacks) {
				maxLines = Math.max(maxLines, lines(stack, s).size());
			}
			return cell(s) + (maxLines > 0 ? 1 + maxLines * lh : 0) + s.extraH(PAD);
		}
		float total = 0;
		for (ItemStack stack : stacks) {
			total += rowHeight(mc, s, stack) + rowGap(s);
		}
		return total - rowGap(s) + s.extraH(PAD);
	}

	private float rowHeight(Minecraft mc, Settings s, ItemStack stack) {
		if (connected(s)) {
			// The strip advances one slot pitch per slot whatever the row holds, so a taller row would
			// walk the icons out of their frames. Pin it; beside-the-icon text centres inside.
			return cell(s);
		}
		float textH = lines(stack, s).size() * lineH(mc, s);
		return switch (s.textPos) {
			case RIGHT, LEFT -> Math.max(cell(s), textH);
			case ABOVE, UNDER -> cell(s) + (textH > 0 ? GAP + textH : 0);
		};
	}

	/**
	 * Whether the frames should run together. Frames only, and never while the pieces are placed
	 * separately: they have no shared spacing to close.
	 */
	private boolean connected(Settings s) {
		return s.slotBackground && s.connectedSlots && !s.separatePositions;
	}

	/**
	 * Whether a connected run leaves room for the durability text.
	 *
	 * <p>A run of slots only reads as a hotbar if the slots touch, and text can only sit between
	 * them by pushing them apart. Beside the icons — text right or left of a column — there is room
	 * and the text stays. Above or under them in a column, or under them in a row, there is not:
	 * the text is dropped rather than drawn across the frames, which is what it used to do.
	 *
	 * <p>Dropping the text rather than the run, because a connected strip is the thing that was
	 * asked for, and the durability bars on the items still show wear without it.
	 */
	private boolean textSuppressed(Settings s) {
		if (!connected(s)) {
			return false;
		}
		return s.layout == Layout.HORIZONTAL
				|| s.textPos == TextPos.ABOVE || s.textPos == TextPos.UNDER;
	}

	/** Space between one piece and the next across a row. */
	private int pieceGap(Settings s) {
		return connected(s) ? -(SLOT - PITCH) : GAP;
	}

	/**
	 * The same gap, which the durability text can force wider.
	 *
	 * <p>In a row the text sits under its icon and is usually wider than the icon, so neighbouring
	 * pieces end up spaced by their text rather than their icons and the runs touch:
	 * "263/363 328/528" reads as one number. Where the text is what sets the cell width, it gets
	 * the wider gap.
	 */
	private int rowPieceGap(Settings s, float textW) {
		return !connected(s) && textW > cell(s) ? GAP * 2 : pieceGap(s);
	}

	/** Space between one piece and the next down a column. */
	private int rowGap(Settings s) {
		return connected(s) ? -(SLOT - PITCH) : 2;
	}

	/**
	 * The box one piece takes: the item alone, or wide enough for a slot frame around it.
	 *
	 * <p>Asked for by the layout rather than hard-coded, so turning frames on grows the spacing with
	 * them instead of letting neighbouring pieces overlap. With frames off it returns ICON, which
	 * makes every one of those layout sites the same arithmetic it was before.
	 */
	private int cell(Settings s) {
		return s.slotBackground ? SLOT : ICON;
	}

	/**
	 * A run of {@code n} joined slots, drawn from the hotbar itself.
	 *
	 * <p>Two blits whatever the length: the sprite already holds nine slots with the dividers vanilla
	 * draws between them, so the body is the first {@code n} of those and the cap is the 1px right
	 * edge. Nothing is tiled, so nothing doubles up.
	 */
	private void drawStrip(GuiGraphicsExtractor g, float x, float y, int n, float scale) {
		int tw = Math.round(HOTBAR_W * scale);
		int th = Math.round(HOTBAR_H * scale);
		int body = Math.round((CAP + PITCH * Math.min(n, HOTBAR_W / PITCH)) * scale);
		int cap = Math.max(1, Math.round(CAP * scale));
		int px = Math.round(x);
		int py = Math.round(y);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, tw, th, 0, 0, px, py, body, th);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, tw, th, tw - cap, 0, px + body, py, cap, th);
	}

	/**
	 * The same run turned on its side, for the vertical layout.
	 *
	 * <p>Vanilla has no vertical bar to slice, so the horizontal one is rotated a quarter turn. The
	 * dividers and the caps are still vanilla's; only the bevel ends up lit from the side rather than
	 * from above, which is not something the eye picks up at this size.
	 */
	private void drawStripVertical(GuiGraphicsExtractor g, float x, float y, int n, float scale) {
		g.pose().pushMatrix();
		// Rotating about the origin sends local +X down and local +Y to -X, so the strip is shifted
		// right by its own thickness first to land back on the block.
		g.pose().translate(x + SLOT * scale, y);
		g.pose().rotateAbout((float) (Math.PI / 2.0), 0f, 0f);
		drawStrip(g, 0f, 0f, n, scale);
		g.pose().popMatrix();
	}

	private void drawIcon(GuiGraphicsExtractor g, ItemStack stack, float sx, float sy, float scale, boolean bar,
			Minecraft mc, Settings s, net.minecraft.resources.Identifier empty) {
		float inset = 0f;
		if (s.slotBackground) {
			// Connected draws one strip for the lot, before the pieces; only a lone slot draws here.
			if (!connected(s)) {
				drawStrip(g, sx, sy, 1, scale);
			}
			inset = (SLOT - ICON) / 2f * scale;
		}
		if (stack.isEmpty()) {
			// Nothing worn: vanilla's own ghost outline for that slot, sized and placed exactly where
			// the item would have gone, so a filling slot does not shift.
			if (empty != null) {
				int side = Math.round(ICON * scale);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, empty, Math.round(sx + inset),
						Math.round(sy + inset), side, side, -1);
			}
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(sx + inset, sy + inset);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		if (bar) {
			g.itemDecorations(mc.font, stack, 0, 0); // vanilla durability bar
		}
		g.pose().popMatrix();
	}

	/** Draws one line of colored segments at screen (x, y). */
	private void drawLine(GuiGraphicsExtractor g, Minecraft mc, Settings s, List<TextSeg> line, float x, float y,
			float scale) {
		float cx = x;
		for (TextSeg seg : line) {
			HudText.draw(g, mc, s, seg.text(), cx, y, seg.spec(), 0, seg.text().length(), scale);
			cx += HudText.width(mc, s, seg.text(), scale);
		}
	}

	/** Unscaled {w, h} of one piece's icon+text block for the current text position. */
	private float[] pieceSize(Minecraft mc, Settings s, ItemStack stack) {
		return pieceSize(mc, s, stack, s.scale);
	}

	private float[] pieceSize(Minecraft mc, Settings s, ItemStack stack, float scale) {
		List<List<TextSeg>> lines = lines(stack, s);
		float tw = linesWidth(mc, s, lines, scale);
		float th = lines.size() * lineH(mc, s, scale);
		return switch (s.textPos) {
			case RIGHT, LEFT -> new float[]{cell(s) + (tw > 0 ? GAP + tw : 0), Math.max(cell(s), th)};
			case ABOVE, UNDER -> new float[]{Math.max(cell(s), tw), cell(s) + (th > 0 ? GAP + th : 0)};
		};
	}

	/** Draws one piece's icon + text block at screen (ox, oy); text column width = alignTextW (unscaled). */
	private void drawPieceBlock(GuiGraphicsExtractor g, Minecraft mc, Settings s, ItemStack stack,
			float ox, float oy, float alignTextW, float scale,
			net.minecraft.resources.Identifier empty) {
		List<List<TextSeg>> lines = lines(stack, s);
		float lh = HudText.lineHeight(mc, s, scale);
		float textH = lines.size() * lh;
		float tw = alignTextW * scale;
		float iconS = cell(s) * scale;
		float blockH = switch (s.textPos) {
			case RIGHT, LEFT -> Math.max(iconS, textH);
			case ABOVE, UNDER -> iconS + (textH > 0 ? GAP * scale : 0) + textH;
		};
		float iconX;
		float iconY;
		float textLeft;
		float textTop;
		float colW;
		switch (s.textPos) {
			case RIGHT -> {
				iconX = ox;
				iconY = oy + (blockH - iconS) / 2f;
				textLeft = ox + iconS + GAP * scale;
				textTop = oy + (blockH - textH) / 2f;
				colW = tw;
			}
			case LEFT -> {
				iconX = ox + tw + GAP * scale;
				iconY = oy + (blockH - iconS) / 2f;
				textLeft = ox;
				textTop = oy + (blockH - textH) / 2f;
				colW = tw;
			}
			case ABOVE -> {
				colW = Math.max(iconS, tw);
				iconX = ox + (colW - iconS) / 2f;
				iconY = oy + textH + (textH > 0 ? GAP * scale : 0);
				textLeft = ox;
				textTop = oy;
			}
			default -> { // UNDER
				colW = Math.max(iconS, tw);
				iconX = ox + (colW - iconS) / 2f;
				iconY = oy;
				textLeft = ox;
				textTop = oy + iconS + (textH > 0 ? GAP * scale : 0);
			}
		}
		drawIcon(g, stack, iconX, iconY, scale, s.durabilityBar, mc, s, empty);
		float ty = textTop;
		for (List<TextSeg> line : lines) {
			float lw = segsWidth(mc, s, line, scale) * scale;
			float lx = switch (s.textPos) {
				case LEFT -> textLeft + (colW - lw);          // right-align against icon
				case ABOVE, UNDER -> textLeft + (colW - lw) / 2f; // center
				default -> textLeft;                          // RIGHT: left-align
			};
			drawLine(g, mc, s, line, lx, ty, scale);
			ty += lh;
		}
	}

	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		Settings s = (Settings) settings();
		if (!s.separatePositions) {
			return super.draggables(mc, screenW, screenH);
		}
		List<Draggable> out = new ArrayList<>();
		for (PieceRef pr : pieces(mc)) {
			ModuleSettings.PartPos pos = pr.pos();
			// Measure at the piece's effective scale (module scale × per-piece factor).
			float eff = s.scale * pos.scale;
			float[] sz = pieceSize(mc, s, pr.stack(), eff);
			float w = (sz[0] + s.extraW(PAD)) * eff;
			float h = (sz[1] + s.extraH(PAD)) * eff;
			out.add(new Draggable() {
				@Override
				public Rect bounds() {
					return partBounds(pos, w, h, screenW, screenH);
				}

				@Override
				public void scaleBy(float delta) {
					pos.scale = clampScale(pos.scale + delta);
				}

				@Override
				public void moveTo(float x, float y) {
					partMoveTo(pos, x, y, w, h, screenW, screenH);
				}
			});
		}
		return out;
	}

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		List<PieceRef> pieces = pieces(mc);
		if (pieces.isEmpty()) {
			return;
		}
		float scale = s.scale;

		if (s.separatePositions) {
			for (PieceRef pr : pieces) {
				// Render at the piece's effective scale (module scale × per-piece factor).
				float eff = s.scale * pr.pos().scale;
				float[] sz = pieceSize(mc, s, pr.stack(), eff);
				float w = (sz[0] + s.extraW(PAD)) * eff;
				float h = (sz[1] + s.extraH(PAD)) * eff;
				Rect pb = partBounds(pr.pos(), w, h, g.guiWidth(), g.guiHeight());
				int px = Math.round(pb.x());
				int py = Math.round(pb.y());
				drawChromeScreen(g, mc, px, py, Math.round(pb.w()), Math.round(pb.h()), eff);
				drawPieceBlock(g, mc, s, pr.stack(), px + s.insetX(PAD) * eff,
						py + s.insetY(PAD) * eff, linesWidth(mc, s, lines(pr.stack(), s), eff), eff,
						pr.empty());
			}
			return;
		}

		List<ItemStack> stacks = stacks(mc);
		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		int rx = Math.round(r.x());
		int ry = Math.round(r.y());
		drawChromeScreen(g, mc, rx, ry, Math.round(r.w()), Math.round(r.h()));

		float ox = rx + s.insetX(PAD) * scale;
		float oy = ry + s.insetY(PAD) * scale;
		float lh = HudText.lineHeight(mc, s);
		float tw = maxTextW(mc, s, stacks);

		if (s.layout == Layout.HORIZONTAL) {
			float cell = Math.max(cell(s), tw);
			if (connected(s)) {
				drawStrip(g, ox, oy, stacks.size(), scale);
			}
			float x = ox;
			for (PieceRef pr : pieces) {
				ItemStack stack = pr.stack();
				float iconX = x + (cell - cell(s)) * scale / 2f;
				drawIcon(g, stack, iconX, oy, scale, s.durabilityBar, mc, s, pr.empty());
				float ty = oy + (cell(s) + 1) * scale;
				for (List<TextSeg> line : lines(stack, s)) {
					float lw = segsWidth(mc, s, line, scale) * scale;
					drawLine(g, mc, s, line, x + (cell * scale - lw) / 2f, ty, scale);
					ty += lh;
				}
				x += (cell + rowPieceGap(s, tw)) * scale;
			}
			return;
		}

		if (connected(s)) {
			// Behind the icons, not at the block origin: with the text on the left the icon column
			// starts a text width in, and drawing at ox put the frames under the text instead.
			float stripX = s.textPos == TextPos.LEFT ? ox + (tw + GAP) * scale : ox;
			drawStripVertical(g, stripX, oy, stacks.size(), scale);
		}
		float y = oy;
		for (PieceRef pr : pieces) {
			drawPieceBlock(g, mc, s, pr.stack(), ox, y, tw, scale, pr.empty());
			y += rowHeight(mc, s, pr.stack()) * scale + rowGap(s) * scale;
		}
	}
}
