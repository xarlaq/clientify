package dev.clientify.client.gui;

import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ClientifyConfig;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.widget.GlassEditBox;
import dev.clientify.client.hud.HudModule.Rect;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.util.Colors;
import dev.clientify.client.util.Draw;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The shared Lunar-style panel chrome: translucent panel over the raw world (no blur, no
 * dim), header with brand + MODS/SETTINGS tabs + close button, and the profile sidebar
 * (switch/rename/delete/create + EDIT HUD LAYOUT). Subclasses fill the main area.
 */
public abstract class PanelScreen extends Screen {
	protected static final int HEADER_H = 30;
	protected static final int SIDEBAR_W = 96;

	/** The drag editor this menu stack was opened from (EDIT HUD LAYOUT target). */
	protected final Screen editorScreen;

	protected int px, py, pw, ph, mainX, mainW;
	protected GlassEditBox renameBox;
	private String renamingProfile;
	/** Profile armed by right-click; its pencil becomes reset + bin icons. */
	private String deleteArmed;
	private double profileScroll;
	private String tooltip;
	private int tooltipX, tooltipY;

	protected PanelScreen(Component title, Screen editorScreen) {
		super(title);
		this.editorScreen = editorScreen;
	}

	@Override
	protected final void init() {
		pw = Math.min(500, width - 30);
		ph = Math.min(300, height - 30);
		px = (width - pw) / 2;
		py = (height - ph) / 2;
		mainX = px + SIDEBAR_W + 9;
		mainW = px + pw - 8 - mainX;

		renamingProfile = null;
		deleteArmed = null;
		profileScroll = 0;
		initMain();
		renameBox = new GlassEditBox(font, px + 6, py + HEADER_H + 5, SIDEBAR_W - 11, 15,
				Component.literal("Profile name"), false);
		renameBox.visible = false;
		addRenderableWidget(renameBox);
	}

	/** Subclass widgets + geometry (runs before the rename box is added). */
	protected abstract void initMain();

	/** Main-area background content; runs at the end of {@link #renderBackground}. */
	protected abstract void renderMain(GuiGraphics g, int mouseX, int mouseY, float partialTick);

	/** Clicks the common chrome didn't consume. */
	protected boolean mainClicked(MouseButtonEvent e) {
		return false;
	}

	/** Where Esc / onClose goes. */
	protected abstract Screen backTarget();

	/** Clicking the MODS tab from a sub-view returns to the list (no-op on the list). */
	protected void onModsTab() {
	}

	/** The list screen of this menu stack (for tab navigation). */
	protected abstract ModListScreen listScreen();

	/** Clicking the SETTINGS tab opens the menu-theme settings. */
	protected void onSettingsTab() {
		ModuleManager.save();
		minecraft.setScreen(new GlobalSettingsScreen(listScreen()));
	}

	protected boolean anyTextFieldFocused() {
		return renameBox != null && renameBox.isFocused();
	}

	/** Hover tooltip, drawn topmost at the end of the frame. */
	protected void setTooltip(String text, int mx, int my) {
		tooltip = text;
		tooltipX = mx;
		tooltipY = my;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		if (tooltip != null) {
			// Split on newlines: a tooltip that has to explain something rarely fits on one line,
			// and one long line would run off the edge of the screen instead of wrapping.
			String[] lines = tooltip.split("\n");
			int tw = 0;
			for (String line : lines) {
				tw = Math.max(tw, Ui.sw(line));
			}
			tw += 10;
			int th = 4 + lines.length * 10;
			int tx = Math.max(4, Math.min(tooltipX + 8, width - tw - 4));
			int ty = Math.max(4, tooltipY - 2 - th);
			Draw.smoothRoundedBordered(g, tx, ty, tw, th, 3, 0xF0101014, Ui.HAIRLINE, 1);
			for (int i = 0; i < lines.length; i++) {
				Ui.str(g, lines[i], tx + 5, ty + 3 + i * 10, Ui.TEXT);
			}
			tooltip = null;
		}
	}

	// ---- geometry ----

	private Rect closeRect() {
		return new Rect(px + pw - 24, py + 7, 16, 16);
	}

	private Rect editLayoutRect() {
		return new Rect(px + 6, py + ph - 24, SIDEBAR_W - 11, 16);
	}

	private Rect newProfileRect() {
		return new Rect(px + 6, py + ph - 42, SIDEBAR_W - 11, 14);
	}

	private Rect profileRowRect(int index) {
		return new Rect(px + 2, py + HEADER_H + 5 + index * 17 - (int) profileScroll, SIDEBAR_W - 3, 16);
	}

	/** The scrollable profile-rows region (above + NEW PROFILE). */
	private int profilesBottom() {
		return py + ph - 46;
	}

	private Rect modsTabRect() {
		int tabX = px + 32 + MenuFont.width("CLIENTIFY", MenuFont.Size.TITLE, 1.5f) + 18;
		return new Rect(tabX, py + 7, Ui.capsW("Mods", 1f) + 14, 16);
	}

	private Rect settingsTabRect() {
		Rect mt = modsTabRect();
		return new Rect(mt.x() + mt.w() + 5, py + 7, Ui.capsW("Settings", 1f) + 14, 16);
	}

	// ---- rendering ----

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// Outside a world there is no world to show through the panel, so vanilla's title panorama
		// stands in behind it. It also has to be drawn before the blur capture, since that samples
		// whatever is already on the screen.
		if (minecraft.level == null) {
			renderPanorama(g, partialTick);
			// The HUD is what normally marks a new frame for the backdrop, and it is not running.
			dev.clientify.client.hud.BlurBackdrop.newFrame();
		}
		// Menu blur is frosted-card style: only the panel region samples the blurred world,
		// the rest of the screen stays crisp (BlurBackdrop + region blit, not a fullscreen blur).
		if (ClientifyConfig.global().menuBlur
				&& dev.clientify.client.hud.BlurBackdrop.prepare(minecraft)) {
			Draw.backdropRounded(g, dev.clientify.client.hud.BlurBackdrop.TEXTURE_ID,
					px, py, pw, ph, Draw.R_SHEET, 0, 0, 1f, width, height,
					dev.clientify.client.hud.BlurBackdrop.vFlip());
		}

		// Panel: gradient modes and chroma WAVE get a real vertical gradient (the flat fill
		// can't show a wave — one sample point); everything else is a flat fill.
		ModuleSettings.ColorSpec p = ClientifyConfig.global().panelColor;
		boolean chromaWave = p.mode == ModuleSettings.ColorSpec.Mode.CHROMA
				&& p.chromaType == ModuleSettings.ColorSpec.ChromaType.WAVE;
		if (chromaWave || p.mode == ModuleSettings.ColorSpec.Mode.GRADIENT
				|| p.mode == ModuleSettings.ColorSpec.Mode.GRADIENT_WAVE) {
			int ca = Colors.parse(p.a, 0x57000000);
			int cb = Colors.parse(p.b, 0x57101018);
			int alpha = ca & 0xFF000000;
			int top;
			int bottom;
			if (chromaWave) {
				top = alpha | (Colors.chroma(p.speed, 0f) & 0xFFFFFF);
				bottom = alpha | (Colors.chroma(p.speed, 0.3f * p.spread) & 0xFFFFFF);
			} else if (p.mode == ModuleSettings.ColorSpec.Mode.GRADIENT) {
				top = ca;
				bottom = alpha | (cb & 0xFFFFFF);
			} else {
				top = alpha | (Colors.lerp(ca, cb, Colors.waveT(p.speed, 0f)) & 0xFFFFFF);
				bottom = alpha | (Colors.lerp(ca, cb, Colors.waveT(p.speed, 0.5f * p.spread)) & 0xFFFFFF);
			}
			Draw.smoothRounded(g, px, py, pw, ph, Draw.R_SHEET, Ui.HAIRLINE);
			Draw.smoothRoundedGradient(g, px + 1, py + 1, pw - 2, ph - 2, Draw.R_SHEET - 1, top, bottom);
		} else {
			Draw.smoothRoundedBordered(g, px, py, pw, ph, Draw.R_SHEET, Ui.panel(), Ui.HAIRLINE, 1);
		}

		// Sidebar tint + dividers
		g.fill(px + 1, py + HEADER_H + 1, px + SIDEBAR_W, py + ph - Draw.R_SHEET, 0x26000000);
		g.fill(px + SIDEBAR_W, py + HEADER_H, px + SIDEBAR_W + 1, py + ph - 1, Ui.HAIRLINE);
		g.fill(px + 1, py + HEADER_H, px + pw - 1, py + HEADER_H + 1, Ui.HAIRLINE);

		// Brand: the mark itself (no plate — the header is already a surface) + spaced-caps wordmark
		Logo.draw(g, px + 8, py + 5, 20);
		MenuFont.draw(g, "CLIENTIFY", px + 32, py + 9, Ui.TEXT, MenuFont.Size.TITLE, false, 1.5f);

		// Tabs — the active one gets the lifted chip
		boolean onSettings = this instanceof GlobalSettingsScreen;
		Rect mt = modsTabRect();
		Rect st = settingsTabRect();
		if (onSettings) {
			Draw.smoothRounded(g, (int) st.x(), (int) st.y(), (int) st.w(), (int) st.h(), 4, 0x1EFFFFFF);
		} else {
			Draw.smoothRounded(g, (int) mt.x(), (int) mt.y(), (int) mt.w(), (int) mt.h(), 4, 0x1EFFFFFF);
		}
		Ui.caps(g, "Mods", (int) mt.x() + 7, (int) mt.y() + 3, onSettings ? 0x99EDEDF2 : Ui.TEXT, 1f);
		Ui.caps(g, "Settings", (int) st.x() + 7, (int) st.y() + 3, onSettings ? Ui.TEXT : 0x99EDEDF2, 1f);

		// Close button
		Rect xr = closeRect();
		if (xr.contains(mouseX, mouseY)) {
			Draw.smoothRounded(g, (int) xr.x(), (int) xr.y(), 16, 16, 4, 0x59C43D3D);
		}
		Draw.smoothBorder(g, (int) xr.x(), (int) xr.y(), 16, 16, 4, 0x2AFFFFFF);
		if (Textures.ensure()) {
			int n = Textures.SIZE;
			g.blit(RenderPipelines.GUI_TEXTURED, Textures.CLOSE, (int) xr.x() + 4, (int) xr.y() + 4,
					0f, 0f, 8, 8, n, n, n, n, Ui.TEXT);
		}

		// A screen can take the sidebar over for its own list (waypoints), in which case the
		// profile rows and + NEW PROFILE are replaced entirely.
		if (customSidebar()) {
			g.enableScissor(px + 1, py + HEADER_H + 1, px + SIDEBAR_W, profilesBottom());
			renderCustomSidebar(g, mouseX, mouseY, px + 2, py + HEADER_H + 5, SIDEBAR_W - 3,
					profilesBottom());
			g.disableScissor();
			renderCustomSidebarFooter(g, mouseX, mouseY, newProfileRect());
			Rect erc = editLayoutRect();
			boolean eHoverC = erc.contains(mouseX, mouseY);
			Draw.smoothRounded(g, (int) erc.x(), (int) erc.y(), (int) erc.w(), (int) erc.h(), 4,
					eHoverC ? Ui.accent() : Ui.accentDim());
			Ui.caps(g, "Edit HUD Layout", (int) (erc.x() + (erc.w() - Ui.capsW("Edit HUD Layout", 0.2f)) / 2),
					(int) erc.y() + 4, 0xFFFFFFFF, 0.2f);
			renderMain(g, mouseX, mouseY, partialTick);
			minecraft.gui.renderDeferredSubtitles();
			return;
		}

		// Sidebar: profile rows (click = switch, pencil = rename, right-click arms reset + bin)
		List<String> profiles = ModuleManager.profiles();
		String activeProfile = ModuleManager.activeProfile();
		g.enableScissor(px + 1, py + HEADER_H + 1, px + SIDEBAR_W, profilesBottom());
		for (int i = 0; i < profiles.size(); i++) {
			String name = profiles.get(i);
			Rect rr = profileRowRect(i);
			int ry = (int) rr.y();
			if (ry + 17 < py + HEADER_H || ry > profilesBottom()) {
				continue;
			}
			boolean isActive = name.equals(activeProfile);
			boolean armed = name.equals(deleteArmed);
			if (isActive) {
				g.fill(px + 2, ry + 2, px + 4, ry + 14, Ui.accent());
			}
			if (!name.equals(renamingProfile)) {
				boolean inRow = rr.contains(mouseX, mouseY) && mouseY < profilesBottom();
				MenuFont.draw(g, trimTracked(name, armed ? SIDEBAR_W - 40 : SIDEBAR_W - 28), px + 9, ry + 4,
						isActive ? Ui.TEXT : Ui.TEXT_DIM, MenuFont.Size.BODY, false, 0.6f);
				if (Textures.ensure()) {
					int n = Textures.SIZE;
					if (armed) {
						boolean overReset = inRow && mouseX >= px + SIDEBAR_W - 29 && mouseX < px + SIDEBAR_W - 17;
						boolean overBin = inRow && mouseX >= px + SIDEBAR_W - 17;
						g.blit(RenderPipelines.GUI_TEXTURED, Textures.RESET, px + SIDEBAR_W - 26, ry + 4,
								0f, 0f, 8, 8, n, n, n, n, overReset ? Ui.TEXT : Ui.TEXT_DIM);
						g.blit(RenderPipelines.GUI_TEXTURED, Textures.TRASH, px + SIDEBAR_W - 14, ry + 4,
								0f, 0f, 8, 8, n, n, n, n, overBin ? 0xFFFF7A7A : 0xFFD86A6A);
						if (overReset) {
							setTooltip("Reset profile to defaults", mouseX, mouseY);
						} else if (overBin) {
							setTooltip("Delete profile", mouseX, mouseY);
						}
					} else {
						boolean overPencil = inRow && mouseX >= px + SIDEBAR_W - 17;
						g.blit(RenderPipelines.GUI_TEXTURED, Textures.PENCIL, px + SIDEBAR_W - 14, ry + 4,
								0f, 0f, 8, 8, n, n, n, n, overPencil ? Ui.TEXT : Ui.TEXT_DIM);
					}
				}
			}
			g.fill(px + 6, ry + 16, px + SIDEBAR_W - 5, ry + 17, 0x0FFFFFFF);
		}
		g.disableScissor();

		int profContent = profiles.size() * 17;
		int profView = profilesBottom() - (py + HEADER_H + 5);
		if (profContent > profView) {
			int barH = Math.max(10, profView * profView / profContent);
			int barY = py + HEADER_H + 5
					+ (int) ((profView - barH) * (profileScroll / (profContent - profView)));
			Draw.smoothRounded(g, px + SIDEBAR_W - 4, barY, 2, barH, 1, 0x50FFFFFF);
		}

		// + NEW PROFILE (copies the current settings, Lunar's save-as-new semantics)
		Rect np = newProfileRect();
		boolean npHover = np.contains(mouseX, mouseY);
		Draw.smoothBorder(g, (int) np.x(), (int) np.y(), (int) np.w(), (int) np.h(), 3, 0x2AFFFFFF);
		Ui.caps(g, "+ New Profile", (int) (np.x() + (np.w() - Ui.capsW("+ New Profile", 0.2f)) / 2),
				(int) np.y() + 3, npHover ? Ui.TEXT : Ui.TEXT_DIM, 0.2f);

		Rect er = editLayoutRect();
		boolean eHover = er.contains(mouseX, mouseY);
		Draw.smoothRounded(g, (int) er.x(), (int) er.y(), (int) er.w(), (int) er.h(), 4,
				eHover ? Ui.accent() : Ui.accentDim());
		Ui.caps(g, "Edit HUD Layout", (int) (er.x() + (er.w() - Ui.capsW("Edit HUD Layout", 0.2f)) / 2),
				(int) er.y() + 4, 0xFFFFFFFF, 0.2f);

		renderMain(g, mouseX, mouseY, partialTick);
		minecraft.gui.renderDeferredSubtitles(); // vanilla ends renderBackground with this
	}

	// ---- sidebar takeover (a screen swaps the profile list for its own) ----

	/** True to replace the profile sidebar with this screen's own list. */
	protected boolean customSidebar() {
		return false;
	}

	/** Draws the custom sidebar rows inside (x, y, w) clipped to {@code bottom}. */
	protected void renderCustomSidebar(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int bottom) {
	}

	/** Draws the custom sidebar's footer button in the + NEW PROFILE slot. */
	protected void renderCustomSidebarFooter(GuiGraphics g, int mouseX, int mouseY, Rect slot) {
	}

	/** Handles a click in the custom sidebar (including its footer slot). */
	protected boolean clickCustomSidebar(MouseButtonEvent e) {
		return false;
	}

	/** Scroll wheel over the custom sidebar. */
	protected void scrollCustomSidebar(double delta) {
	}

	/** Geometry helpers so a takeover can lay out rows exactly like the profile list. */
	protected Rect sidebarFooterRect() {
		return newProfileRect();
	}

	protected int sidebarBottom() {
		return profilesBottom();
	}

	protected int sidebarTop() {
		return py + HEADER_H + 5;
	}

	private String trimTracked(String s, int maxW) {
		if (MenuFont.width(s, MenuFont.Size.BODY, 0.6f) <= maxW) {
			return s;
		}
		while (s.length() > 1 && MenuFont.width(s + "…", MenuFont.Size.BODY, 0.6f) > maxW) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	// ---- profile rename lifecycle ----

	private void startRename(int index, String name) {
		renamingProfile = name;
		Rect rr = profileRowRect(index);
		renameBox.moveTo(px + 6, (int) rr.y());
		renameBox.setValue(name);
		renameBox.visible = true;
		setFocused(renameBox);
	}

	private void commitRename() {
		if (renamingProfile != null) {
			ModuleManager.renameProfile(renamingProfile, renameBox.getValue());
		}
		endRename();
	}

	private void endRename() {
		renamingProfile = null;
		renameBox.visible = false;
		setFocused(null);
	}

	// ---- input ----

	/** Set while a press is being handled, so one click is one sound however deep it landed. */
	private static boolean clickSounded;

	/** The vanilla menu click, so our menus sound like the rest of the game. */
	public static void clickSound() {
		if (clickSounded) {
			return;
		}
		clickSounded = true;
		net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
				net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		// Everything the panels do goes through here -- the module list, the tabs, the rows --
		// so the sound is decided in one place rather than remembered at every control.
		clickSounded = false;
		boolean handled = clientify$handleClick(e, doubleClick);
		if (handled) {
			clickSound();
		}
		return handled;
	}

	/**
	 * A chance to take the click as an answer rather than as a click.
	 *
	 * <p>A row listening for a key has to be offered mouse buttons too — they are keys you can bind
	 * — and a mouse button never arrives through {@code keyPressed}. Overridden where such a row
	 * can exist; false everywhere else, so nothing changes for screens without one.
	 */
	protected boolean captureRawMouse(MouseButtonEvent e) {
		return false;
	}

	private boolean clientify$handleClick(MouseButtonEvent e, boolean doubleClick) {
		if (captureRawMouse(e)) {
			return true;
		}
		// Clicking away from a field drops its caret, rather than leaving it armed for typing.
		if (getFocused() instanceof net.minecraft.client.gui.components.EditBox box
				&& !box.isMouseOver(e.x(), e.y())) {
			box.setFocused(false);
			setFocused(null);
		}
		if (super.mouseClicked(e, doubleClick)) {
			return true;
		}
		// A click anywhere outside the rename field commits the rename.
		if (renamingProfile != null) {
			commitRename();
		}
		if (customSidebar()) {
			if (e.x() >= px && e.x() < px + SIDEBAR_W && clickCustomSidebar(e)) {
				return true;
			}
			if (closeRect().contains(e.x(), e.y())) {
				ModuleManager.save();
				Menus.exit(minecraft);
				return true;
			}
			if (editLayoutRect().contains(e.x(), e.y())) {
				ModuleManager.save();
				minecraft.setScreen(editorScreen);
				return true;
			}
			// The MODS/SETTINGS tabs work here too — the takeover only replaces the sidebar.
			if (modsTabRect().contains(e.x(), e.y())) {
				onModsTab();
				return true;
			}
			if (settingsTabRect().contains(e.x(), e.y())) {
				onSettingsTab();
				return true;
			}
			return mainClicked(e);
		}
		List<String> profiles = ModuleManager.profiles();
		boolean rowHit = false;
		for (int i = 0; i < profiles.size() && !rowHit; i++) {
			Rect rr = profileRowRect(i);
			if (e.y() >= profilesBottom() || !rr.contains(e.x(), e.y())) {
				continue;
			}
			rowHit = true;
			String name = profiles.get(i);
			if (e.button() == 1) {
				// Right-click arms the row: reset + bin icons appear; click one to confirm.
				deleteArmed = name.equals(deleteArmed) ? null : name;
			} else if (name.equals(deleteArmed) && e.x() >= px + SIDEBAR_W - 17) {
				ModuleManager.deleteProfile(name); // no-op on the last remaining profile
				deleteArmed = null;
			} else if (name.equals(deleteArmed) && e.x() >= px + SIDEBAR_W - 29) {
				ModuleManager.resetProfile(name); // whole profile back to default settings
				deleteArmed = null;
			} else if (e.x() >= px + SIDEBAR_W - 17) {
				deleteArmed = null;
				startRename(i, name);
			} else {
				deleteArmed = null;
				ModuleManager.switchProfile(name);
			}
			return true;
		}
		deleteArmed = null; // any click outside the rows disarms
		if (newProfileRect().contains(e.x(), e.y())) {
			ModuleManager.createProfile();
			return true;
		}
		if (closeRect().contains(e.x(), e.y())) {
			ModuleManager.save();
			minecraft.setScreen(null);
			return true;
		}
		if (editLayoutRect().contains(e.x(), e.y())) {
			ModuleManager.save();
			minecraft.setScreen(editorScreen);
			return true;
		}
		if (modsTabRect().contains(e.x(), e.y())) {
			onModsTab();
			return true;
		}
		if (settingsTabRect().contains(e.x(), e.y())) {
			onSettingsTab();
			return true;
		}
		return mainClicked(e);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (renamingProfile != null) {
			if (e.isEscape()) {
				endRename();
				return true;
			}
			if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) {
				commitRename();
				return true;
			}
		}
		// RShift closes the whole menu back to the game — unless the user is typing
		// (right shift is a normal typing key in text fields).
		if (!anyTextFieldFocused() && ClientifyClient.menuKey.matches(e)) {
			ModuleManager.save();
			Menus.exit(minecraft);
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hDelta, double vDelta) {
		if (super.mouseScrolled(mx, my, hDelta, vDelta)) {
			return true;
		}
		if (customSidebar()) {
			if (mx >= px && mx < px + SIDEBAR_W && my >= py + HEADER_H && my < profilesBottom()) {
				scrollCustomSidebar(vDelta);
				return true;
			}
			return false;
		}
		if (mx >= px && mx < px + SIDEBAR_W && my >= py + HEADER_H && my < profilesBottom()) {
			int content = ModuleManager.profiles().size() * 17;
			int view = profilesBottom() - (py + HEADER_H + 5);
			double max = Math.max(0, content - view);
			profileScroll = Math.max(0, Math.min(max, profileScroll - vDelta * 17));
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		ModuleManager.save();
		minecraft.setScreen(backTarget());
	}
}
