package dev.clientify.client.gui;

import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.util.Draw;
import dev.clientify.client.modules.WaypointsModule;
import dev.clientify.client.modules.WaypointsModule.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Asks before a shared waypoint joins your list.
 *
 * <p>Shows the position rather than just the name, because "do you want this waypoint?" is not a
 * question anyone can answer from a name alone — and these arrive from other people, so the answer
 * may well be no.
 */
public class WaypointImportScreen extends Screen {
	private static final int W = 220;
	private static final int H = 130;

	private final Waypoint waypoint;
	private final Screen parent;
	private int px;
	private int py;
	/** Resolved once on open: the same spot may not be added to this world twice. */
	private boolean duplicate;

	public WaypointImportScreen(Waypoint waypoint, Screen parent) {
		super(Component.literal("Add waypoint"));
		this.waypoint = waypoint;
		this.parent = parent;
	}

	@Override
	protected void init() {
		px = (width - W) / 2;
		py = (height - H) / 2;
		duplicate = WaypointsModule.alreadyHas(
				WaypointsModule.worldKey(Minecraft.getInstance()), waypoint);
	}

	private boolean overAdd(double mx, double my) {
		return mx >= px + W - 106 && mx < px + W - 56 && my >= py + H - 28 && my < py + H - 10;
	}

	private boolean overCancel(double mx, double my) {
		return mx >= px + W - 52 && mx < px + W - 12 && my >= py + H - 28 && my < py + H - 10;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// A plain dim, NOT vanilla's renderBackground: that one blurs the whole screen, and the mod
		// has already spent this frame's single allowed blur on its own frosted panels. Calling it
		// here crashed the client with "Can only blur once per frame" the moment the dialog opened.
		g.fill(0, 0, width, height, 0xB0000000);
		Draw.smoothRoundedBordered(g, px, py, W, H, Draw.R_SHEET, Ui.panel(), Ui.HAIRLINE, 1);

		Logo.draw(g, px + 10, py + 9, 14);
		MenuFont.draw(g, "ADD WAYPOINT", px + 30, py + 12, Ui.TEXT, MenuFont.Size.BODY, false, 1.2f);
		g.fill(px + 10, py + 30, px + W - 10, py + 31, Ui.HAIRLINE);

		var a = waypoint.anchor();
		g.fill(px + 12, py + 42, px + 20, py + 50, waypoint.color.chrome());
		MenuFont.draw(g, waypoint.name, px + 26, py + 41, Ui.TEXT, MenuFont.Size.BODY, false, 0.6f);
		Ui.str(g, a.x + ", " + a.y + ", " + a.z, px + 26, py + 54, Ui.TEXT_DIM);
		Ui.str(g, WaypointsModule.dimensionLabel(waypoint.dimension), px + 26, py + 66, Ui.TEXT_DIM);
		if (duplicate) {
			Ui.str(g, "You already have a waypoint here.", px + 12, py + 82, 0xFFFFB37A);
		}

		boolean onAdd = !duplicate && overAdd(mouseX, mouseY);
		boolean onCancel = overCancel(mouseX, mouseY);
		Draw.smoothRounded(g, px + W - 106, py + H - 28, 50, 18, 4,
				duplicate ? 0x14FFFFFF : (onAdd ? Ui.accent() : Ui.accentDim()));
		Ui.caps(g, "Add", px + W - 106 + (50 - Ui.capsW("Add", 0.2f)) / 2, py + H - 23,
				duplicate ? Ui.TEXT_DIM : 0xFFFFFFFF, 0.2f);
		Draw.smoothRounded(g, px + W - 52, py + H - 28, 40, 18, 4, onCancel ? 0x24FFFFFF : 0x14FFFFFF);
		Ui.caps(g, "Cancel", px + W - 52 + (40 - Ui.capsW("Cancel", 0.2f)) / 2, py + H - 23,
				Ui.TEXT_DIM, 0.2f);

		super.extractRenderState(g, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (!duplicate && overAdd(e.x(), e.y())) {
			Minecraft mc = Minecraft.getInstance();
			WaypointsModule.waypointsIn(WaypointsModule.worldKey(mc)).add(waypoint);
			ModuleManager.save();
			mc.gui.setScreen(parent);
			return true;
		}
		if (overCancel(e.x(), e.y())) {
			Minecraft.getInstance().gui.setScreen(parent);
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
