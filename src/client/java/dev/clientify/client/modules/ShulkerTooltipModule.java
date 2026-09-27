package dev.clientify.client.modules;

import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.util.Colors;
import dev.clientify.client.util.Draw;
import dev.clientify.client.util.HoldableKey;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * ShulkerBoxTooltip-style grid preview of shulker contents in the item tooltip.
 * ItemStackMixin supplies the TooltipComponent (with the box's dye tint); the callback
 * registered here maps it to the client renderer.
 */
public class ShulkerTooltipModule extends HudModule {
	public static class Settings extends ModuleSettings {
		public boolean slotBackgrounds = true;
		public boolean showWholeContainer = false;
		public boolean hideVanillaText = false;
		public boolean vanillaContainer = false;
		public boolean lockTooltip = true;

		public Settings() {
			enabled = true;
		}
	}

	private static ShulkerTooltipModule instance;
	private final HoldableKey lockKey;

	public ShulkerTooltipModule() {
		super("shulkertooltip", "Shulker Tooltip");
		instance = this;
		lockKey = new HoldableKey("key.clientify.lock_tooltip", InputConstants.KEY_LCONTROL,
				ClientifyClient.KEY_CATEGORY);
		KeyMappingHelper.registerKeyMapping(lockKey); // returns the base type, so keep our own reference
		TooltipComponentCallback.EVENT.register(data ->
				data instanceof ShulkerGridTooltip grid ? new ClientShulkerGridTooltip(grid) : null);
	}

	// ---- tooltip lock ----
	//
	// Hold the key over a shulker and its tooltip stays put, so the pointer is free to leave the
	// item and wander over the preview grid; whatever it lands on gets its own tooltip. Without
	// the lock the tooltip belongs to whatever the mouse is on, so it vanishes the moment you try
	// to look inside it.

	private static ItemStack lockedStack = ItemStack.EMPTY;
	private static int lockedX;
	private static int lockedY;
	// Where the grid last drew itself, so the pointer can be mapped back to a slot in it.
	private static int gridX;
	private static int gridY;
	private static int gridCols;
	private static int gridRows;
	private static List<ItemStack> gridItems = List.of();
	private static boolean gridDrawn;

	private static boolean hasPreview(ItemStack stack) {
		return !stack.isEmpty() && stack.getTooltipImage()
				.filter(image -> image instanceof ShulkerGridTooltip).isPresent();
	}

	/**
	 * Draws the held-open tooltip in place of the hovered one. Returns true when it took over, in
	 * which case the caller must not draw the normal tooltip.
	 *
	 * @param hovered the stack under the pointer, empty if none — only used to start a lock
	 */
	public static boolean renderLocked(GuiGraphicsExtractor g, Font font, ItemStack hovered, int mouseX, int mouseY) {
		if (instance == null || !instance.isEnabled() || !(instance.settings() instanceof Settings s)
				|| !s.lockTooltip || !instance.lockKey.isHeld()) {
			lockedStack = ItemStack.EMPTY;
			return false;
		}
		if (lockedStack.isEmpty()) {
			if (!hasPreview(hovered)) {
				return false; // nothing worth locking under the pointer yet
			}
			lockedStack = hovered;
			lockedX = mouseX;
			lockedY = mouseY;
		}
		Minecraft mc = Minecraft.getInstance();
		gridDrawn = false;
		// Drawn now rather than deferred, so the item the pointer finds inside it can claim the
		// deferred slot and land on top.
		g.renderTooltip(font, tooltipOf(mc, lockedStack), lockedX, lockedY,
				DefaultTooltipPositioner.INSTANCE,
				lockedStack.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE));
		ItemStack inside = itemAt(mouseX, mouseY);
		if (!inside.isEmpty()) {
			g.setTooltipForNextFrame(font, inside, mouseX, mouseY);
		}
		return true;
	}

	/** The tooltip lines plus the preview image, ordered as vanilla orders them. */
	private static List<ClientTooltipComponent> tooltipOf(Minecraft mc, ItemStack stack) {
		List<ClientTooltipComponent> out = new ArrayList<>();
		for (net.minecraft.network.chat.Component line : net.minecraft.client.gui.screens.Screen
				.getTooltipFromItem(mc, stack)) {
			out.add(ClientTooltipComponent.create(line.getVisualOrderText()));
		}
		stack.getTooltipImage().ifPresent(image ->
				out.add(out.isEmpty() ? 0 : 1, ClientTooltipComponent.create(image)));
		return out;
	}

	/** The item under (mx,my) in the grid as it was last drawn, or empty. */
	private static ItemStack itemAt(int mx, int my) {
		if (!gridDrawn) {
			return ItemStack.EMPTY;
		}
		int col = (mx - gridX) / ClientShulkerGridTooltip.CELL;
		int row = (my - gridY) / ClientShulkerGridTooltip.CELL;
		if (mx < gridX || my < gridY || col < 0 || row < 0 || col >= gridCols || row >= gridRows) {
			return ItemStack.EMPTY;
		}
		int index = row * 9 + col;
		return index >= 0 && index < gridItems.size() ? gridItems.get(index) : ItemStack.EMPTY;
	}

	public static boolean active() {
		return instance != null && instance.isEnabled();
	}

	/** Read by ItemContainerContentsMixin to suppress the vanilla contents text lines. */
	public static boolean hideVanillaText() {
		return instance != null && instance.isEnabled() && instance.settings() instanceof Settings s && s.hideVanillaText;
	}

	private static Settings activeSettings() {
		return instance != null && instance.settings() instanceof Settings s ? s : new Settings();
	}

	@Override
	public String description() {
		return "Previews shulker box contents in the tooltip.";
	}

	@Override
	public String category() {
		return "MECHANIC";
	}

	@Override
	public boolean isHudElement() {
		return false;
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
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		rows.add(screen.toggle("Whole Container", () -> s.showWholeContainer, v -> s.showWholeContainer = v,
				() -> s.showWholeContainer = false));
		rows.add(screen.toggle("Colored Container", () -> s.vanillaContainer, v -> s.vanillaContainer = v,
				() -> s.vanillaContainer = false));
		rows.add(screen.toggle("Slot Backgrounds", () -> s.slotBackgrounds, v -> s.slotBackgrounds = v,
				() -> s.slotBackgrounds = true));
		rows.add(screen.toggle("Hide Vanilla Text", () -> s.hideVanillaText, v -> s.hideVanillaText = v,
				() -> s.hideVanillaText = false));
		rows.add(screen.toggle("Lock Tooltip", () -> s.lockTooltip, v -> s.lockTooltip = v,
				() -> s.lockTooltip = true));
		if (s.lockTooltip) {
			rows.add(screen.keybindRow("Lock Key", lockKey, () -> {
				lockKey.setKey(lockKey.getDefaultKey());
				net.minecraft.client.KeyMapping.resetMapping();
			}));
		}
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		return 0;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return 0;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
	}

	/** Marker component produced by ItemStackMixin ({@code tint} = the box's dye ARGB, 0 = default). */
	public record ShulkerGridTooltip(ItemContainerContents contents, int tint) implements TooltipComponent {
	}

	static class ClientShulkerGridTooltip implements ClientTooltipComponent {
		static final int CELL = 18;
		private static final int PAD = 3;
		private static final int SLOTS = 27;

		private final List<ItemStack> items = new ArrayList<>();
		private final int tint;

		ClientShulkerGridTooltip(ShulkerGridTooltip grid) {
			for (ItemStack stack : grid.contents().nonEmptyItemsCopy()) {
				items.add(stack);
			}
			this.tint = grid.tint() == 0 ? 0xFF9A72C6 : grid.tint(); // default: shulker purple
		}

		private int slots() {
			return activeSettings().showWholeContainer ? SLOTS : Math.max(1, Math.min(SLOTS, items.size()));
		}

		private int cols() {
			return Math.min(9, Math.max(1, slots()));
		}

		private int rows() {
			return (slots() + 8) / 9;
		}

		@Override
		public int getWidth(Font font) {
			return cols() * CELL + PAD * 2;
		}

		@Override
		public int getHeight(Font font) {
			return rows() * CELL + PAD * 2 + 2;
		}

		@Override
		public void renderImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
			Settings s = activeSettings();
			int w = cols() * CELL + PAD * 2;
			int h = rows() * CELL + PAD * 2;

			// Remember where the cells landed; the lock hit-tests the pointer against this.
			gridX = x + PAD;
			gridY = y + PAD;
			gridCols = cols();
			gridRows = rows();
			gridItems = items;
			gridDrawn = true;

			if (s.vanillaContainer) {
				// Colored container: dye-tinted panel + darker slot recesses.
				int panel = (0xF0 << 24) | (Colors.lerp(tint, 0xFF000000, 0.35f) & 0xFFFFFF);
				Draw.roundedFill(g, x, y, w, h, 3, panel);
				Draw.roundedFill(g, x + 1, y + 1, w - 2, h - 2, 3, 0x30FFFFFF); // top sheen
				Draw.roundedFill(g, x + 1, y + 3, w - 2, h - 4, 3, (0x60 << 24) | (Colors.lerp(tint, 0xFF000000, 0.35f) & 0xFFFFFF));
			} else {
				Draw.roundedFill(g, x, y, w, h, 3, 0x8C101014);
			}

			int total = slots();
			for (int i = 0; i < total; i++) {
				int cx = x + PAD + (i % 9) * CELL;
				int cy = y + PAD + (i / 9) * CELL;
				if (s.vanillaContainer) {
					Draw.roundedFill(g, cx, cy, CELL - 1, CELL - 1, 2, 0x80000000); // slot recess
					Draw.roundedFill(g, cx, cy, CELL - 1, 1, 0, 0x40000000);
				} else if (s.slotBackgrounds) {
					Draw.roundedFill(g, cx, cy, CELL - 1, CELL - 1, 2, 0x14FFFFFF);
				}
				if (i < items.size()) {
					ItemStack stack = items.get(i);
					g.item(stack, cx + 1, cy + 1);
					g.itemDecorations(font, stack, cx + 1, cy + 1);
				}
			}
		}
	}
}
