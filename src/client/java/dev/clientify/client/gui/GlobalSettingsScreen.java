package dev.clientify.client.gui;

import dev.clientify.client.config.ClientifyConfig;
import dev.clientify.client.config.GlobalSettings;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ServerProfiles;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudModule.Rect;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/**
 * The SETTINGS tab: theme the menu itself — accent/panel/card colors (full ColorSpec
 * effects) and the menu blur toggle. Per-row resets plus a reset-everything button.
 */
public class GlobalSettingsScreen extends SettingsRowsScreen {
	private final ModListScreen list;
	private final ModuleSettings defDefaults = new ModuleSettings();
	private boolean defaultsBgExpanded;

	public GlobalSettingsScreen(ModListScreen list) {
		super(Component.literal("Clientify Settings"), list.editorScreen);
		this.list = list;
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
	protected void onSettingsTab() {
		// Already here.
	}

	@Override
	protected List<Row> buildRows() {
		GlobalSettings gs = ClientifyConfig.global();
		GlobalSettings def = new GlobalSettings();
		List<Row> rows = new ArrayList<>();

		rows.add(section("Menu"));
		// Bound to the real KeyMapping, so it stays in sync with Minecraft's Controls screen.
		rows.add(keybindRow("Open Menu Key", dev.clientify.client.ClientifyClient.menuKey, () -> {
			dev.clientify.client.ClientifyClient.menuKey.setKey(
					dev.clientify.client.ClientifyClient.menuKey.getDefaultKey());
			net.minecraft.client.KeyMapping.resetMapping();
		}));
		rows.add(blurToggle("Menu Blur", () -> gs.menuBlur, v -> gs.menuBlur = v,
				() -> gs.menuBlur = def.menuBlur));
		if (gs.menuBlur) {
			rows.add(sliderRow("Menu Blur Strength", 1f, 10f, 1f, () -> (float) gs.menuBlurStrength,
					v -> gs.menuBlurStrength = Math.round(v), "%.0f",
					() -> gs.menuBlurStrength = def.menuBlurStrength));
		}
		rows.add(sliderRow("Module Blur Strength", 1f, 10f, 1f, () -> (float) gs.moduleBlurStrength,
				v -> gs.moduleBlurStrength = Math.round(v), "%.0f",
				() -> gs.moduleBlurStrength = def.moduleBlurStrength));

		rows.add(section("Profiles"));
		rows.add(toggle("Profile Per Server", () -> gs.profilePerServer, v -> gs.profilePerServer = v,
				() -> gs.profilePerServer = def.profilePerServer));
		if (gs.profilePerServer) {
			rows.add(serverBindingRow());
		}

		rows.add(section("HUD"));
		rows.add(toggle("Snap To Screen Centre", () -> gs.editorSnap, v -> gs.editorSnap = v,
				() -> gs.editorSnap = def.editorSnap));
		rows.add(toggle("No Overlap Blending", () -> gs.flatBackgrounds, v -> gs.flatBackgrounds = v,
				() -> gs.flatBackgrounds = def.flatBackgrounds));

		rows.add(section("Colors"));
		addColorRows(rows, "Accent Color", () -> gs.accentColor,
				() -> gs.accentColor.copyFrom(def.accentColor));
		addColorRows(rows, "Panel Color", () -> gs.panelColor,
				() -> gs.panelColor.copyFrom(def.panelColor));

		// A full module-appearance template; APPLY TO ALL stamps it onto every module.
		rows.add(section("Module Defaults"));
		addGeneralAppearanceRows(rows, gs.moduleDefaults, defDefaults,
				() -> defaultsBgExpanded, () -> defaultsBgExpanded = !defaultsBgExpanded);
		addColorAppearanceRows(rows, gs.moduleDefaults, defDefaults, false);
		rows.add(applyToAllRow(gs));

		rows.add(new Row(26, (g, y, mx, my) -> {
			int w = 110;
			int x = mainX + (mainW - w) / 2;
			boolean hover = mx >= x && mx < x + w && my >= y + 4 && my < y + 22
					&& my >= rowY0 && my <= rowY1;
			Draw.smoothBorder(g, x, y + 4, w, 18, 4, hover ? 0x66C43D3D : 0x2AFFFFFF);
			Ui.caps(g, "Reset All", x + (w - Ui.capsW("Reset All", 0.6f)) / 2, y + 9,
					hover ? 0xFFD86A6A : Ui.TEXT_DIM, 0.6f);
		}, (e, y) -> {
			int w = 110;
			int x = mainX + (mainW - w) / 2;
			if (e.x() >= x && e.x() < x + w && e.y() >= y + 4 && e.y() < y + 22) {
				ClientifyConfig.resetGlobal();
				collapsePicker();
				ModuleManager.save();
				return true;
			}
			return false;
		}));
		return rows;
	}

	/**
	 * Which profile this server gets. Cycles through the profiles plus None, and names the place
	 * you are in so it is clear what is being bound — the binding follows the address, not the
	 * world you happen to be standing in.
	 */
	private Row serverBindingRow() {
		String key = ServerProfiles.currentKey(minecraft);
		String where = ServerProfiles.currentLabel(minecraft);
		if (key == null) {
			return new Row(18, (g, y, mx, my) -> {
				Ui.str(g, "This Server", mainX + 2, y + 5, Ui.TEXT_DIM);
				String note = "Join a world to bind one";
				Ui.str(g, note, controlRight() - Ui.sw(note), y + 5, Ui.TEXT_DIM);
			}, null);
		}
		List<String> choices = new ArrayList<>();
		choices.add("None");
		choices.addAll(ModuleManager.profiles());
		return cycleRow(where.length() > 22 ? where.substring(0, 21) + "…" : where,
				() -> {
					String bound = ServerProfiles.boundProfile(key);
					return bound == null ? "None" : bound;
				},
				() -> bindStep(key, choices, -1), () -> bindStep(key, choices, 1),
				() -> {
					ServerProfiles.bind(key, null);
					ModuleManager.save();
				});
	}

	private void bindStep(String key, List<String> choices, int dir) {
		String bound = ServerProfiles.boundProfile(key);
		int at = bound == null ? 0 : Math.max(0, choices.indexOf(bound));
		int next = (at + dir + choices.size()) % choices.size();
		ServerProfiles.bind(key, next == 0 ? null : choices.get(next));
		ModuleManager.save();
	}

	private Row applyToAllRow(GlobalSettings gs) {
		return new Row(26, (g, y, mx, my) -> {
			int w = 150;
			int x = mainX + (mainW - w) / 2;
			boolean hover = mx >= x && mx < x + w && my >= y + 4 && my < y + 22
					&& my >= rowY0 && my <= rowY1;
			Draw.smoothRounded(g, x, y + 4, w, 18, 4, hover ? Ui.accent() : Ui.accentDim());
			Ui.caps(g, "Apply To All Modules", x + (w - Ui.capsW("Apply To All Modules", 0.4f)) / 2,
					y + 9, 0xFFFFFFFF, 0.4f);
			if (hover) {
				setTooltip("Copy these defaults onto every module", mx, my);
			}
		}, (e, y) -> {
			int w = 150;
			int x = mainX + (mainW - w) / 2;
			if (e.x() >= x && e.x() < x + w && e.y() >= y + 4 && e.y() < y + 22) {
				for (HudModule m : ModuleManager.all()) {
					m.settings().applyAppearanceFrom(gs.moduleDefaults);
				}
				ModuleManager.save();
				return true;
			}
			return false;
		});
	}

	private Rect backRect() {
		return new Rect(mainX, py + HEADER_H + 6, 16, 16);
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
		MenuFont.draw(g, "SETTINGS", mainX + 24, py + HEADER_H + 8, Ui.TEXT, MenuFont.Size.TITLE, false, 1.2f);
		g.fill(mainX, py + HEADER_H + 26, mainX + mainW, py + HEADER_H + 27, Ui.HAIRLINE);
		Ui.str(g, "Customize the Clientify menu.", mainX + 2, py + HEADER_H + 31, Ui.TEXT_DIM);
	}

	@Override
	protected boolean rowsHeaderClicked(MouseButtonEvent e) {
		if (backRect().contains(e.x(), e.y())) {
			ModuleManager.save();
			minecraft.gui.setScreen(list);
			return true;
		}
		return false;
	}
}
