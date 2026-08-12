package dev.clientify.client.gui;

import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ClientifyConfig;
import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.widget.GlassButton;
import dev.clientify.client.hud.ChromeMask;
import dev.clientify.client.hud.HudFrame;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudModule.Draggable;
import dev.clientify.client.hud.HudModule.Rect;
import dev.clientify.client.hud.ModuleManager;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The RShift screen: every enabled module (or its separated sub-parts) shown at its live
 * position, draggable (with screen-center snapping) and mouse-wheel resizable, plus the
 * central "Mods" button.
 */
public class HudEditorScreen extends Screen {
	private static final float SNAP = 5f;
	private static final int MODS_H = 24;
	private static final int LOGO = 32;

	private Draggable dragging;
	/** What the arrow keys move, held by module and place rather than by object. */
	private HudModule selectedModule;
	private int selectedIndex = -1;
	private HudModule dragModule;
	private float dragOffX;
	private float dragOffY;
	private float snapGuideX = -1;
	private float snapGuideY = -1;
	private GlassButton snapButton;
	private int logoX;
	private int logoY;

	public HudEditorScreen() {
		super(Component.literal("Clientify"));
	}

	private record Target(HudModule module, Draggable drag, int index) {
	}

	@Override
	protected void init() {
		// The snap toggle has to read as secondary to Mods, so Mods is sized FROM it rather than
		// both being fixed: whatever the font does to the label, the ordering holds and the text
		// never outgrows its button.
		int snapW = Math.max(Ui.sw(snapText(true)), Ui.sw(snapText(false))) + 14;
		int modsW = Math.max(80, snapW + 14);
		int modsY = height / 2 - MODS_H / 2;
		addRenderableWidget(new GlassButton(width / 2 - modsW / 2, modsY, modsW, MODS_H,
				Component.literal("Mods"), () -> minecraft.setScreen(new ModListScreen(this)),
				GlassButton.Style.PANEL));
		snapButton = addRenderableWidget(new GlassButton(width / 2 - snapW / 2, modsY + MODS_H + 6,
				snapW, 16, snapLabel(), this::toggleSnap, GlassButton.Style.PANEL));
		logoX = width / 2 - LOGO / 2;
		logoY = modsY - 6 - LOGO;
	}

	private static boolean snapEnabled() {
		return ClientifyConfig.global().editorSnap;
	}

	private static String snapText(boolean on) {
		return on ? "Snap: On" : "Snap: Off";
	}

	private static Component snapLabel() {
		return Component.literal(snapText(snapEnabled()));
	}

	/** Centre snapping is a habit thing — some layouts want a module a few px off centre. */
	private void toggleSnap() {
		ClientifyConfig.global().editorSnap = !snapEnabled();
		snapButton.setMessage(snapLabel());
		snapGuideX = snapGuideY = -1;
	}

	private List<Target> targets() {
		List<Target> out = new ArrayList<>();
		for (HudModule m : ModuleManager.all()) {
			if (m.isEnabled() && m.isHudElement()) {
				List<Draggable> list = m.draggables(minecraft, width, height);
				for (int i = 0; i < list.size(); i++) {
					out.add(new Target(m, list.get(i), i));
				}
			}
		}
		return out;
	}

	/** The selected target as it stands this frame, or null if it has gone. */
	private Target selected() {
		if (selectedModule == null) {
			return null;
		}
		for (Target t : targets()) {
			if (t.module() == selectedModule && t.index() == selectedIndex) {
				return t;
			}
		}
		return null;
	}

	private boolean isSelected(Target t) {
		return t.module() == selectedModule && t.index() == selectedIndex;
	}

	private Target targetAt(double mx, double my) {
		Target found = null;
		for (Target t : targets()) {
			if (t.drag().bounds().contains(mx, my)) {
				found = t; // last hit == rendered topmost
			}
		}
		return found;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// No blur and no dim in the editor — the raw world stays visible while editing. With no
		// world to show, vanilla's title panorama stands in (nothing else would clear the screen).
		if (minecraft.level == null) {
			renderPanorama(g, partialTick);
		}
		DeltaTracker dt = minecraft.getDeltaTracker();
		// A pass of its own: the HUD pass behind this screen already claimed its rects and cached
		// its content, and the modules it drew are about to be drawn again here as previews.
		HudFrame.begin();
		// Outside a world nothing else marks the frame for the blur backdrop (the HUD normally
		// does), so a blurred chip would keep re-using one stale capture.
		if (minecraft.level == null) {
			dev.clientify.client.hud.BlurBackdrop.newFrame();
		}
		ChromeMask.beginFrame(ClientifyConfig.global().flatBackgrounds);
		for (HudModule m : ModuleManager.all()) {
			if (m.isEnabled() && m.isHudElement()) {
				m.render(g, dt);
			}
		}
		for (Target t : targets()) {
			Rect r = t.drag().bounds();
			boolean hover = r.contains(mouseX, mouseY);
			int color = isSelected(t) ? Ui.accent() : (hover ? Ui.accentSoft() : 0x40FFFFFF);
			// Match the module's own Math.round rounding, and wrap the border when one is on so
			// the outline sits on the chip's outer edge rather than cutting through it.
			ModuleSettings ms = t.module().settings();
			int expand = ms != null && ms.background && ms.border
					? Math.max(1, Math.round(ms.borderThickness * ms.scale))
					: 0;
			int x = Math.round(r.x()) - expand;
			int y = Math.round(r.y()) - expand;
			int w = Math.round(r.w()) + expand * 2;
			int h = Math.round(r.h()) + expand * 2;
			int radius = t.module().outlineRadius();
			Draw.smoothBorder(g, x, y, w, h, radius == 0 ? 0 : radius + expand, color);
		}

		if (dragging != null) {
			int guide = 0x8C000000 | (Ui.accent() & 0xFFFFFF);
			if (snapGuideX >= 0) {
				g.fill((int) snapGuideX, 0, (int) snapGuideX + 1, height, guide);
			}
			if (snapGuideY >= 0) {
				g.fill(0, (int) snapGuideY, width, (int) snapGuideY + 1, guide);
			}
		}
		minecraft.gui.renderDeferredSubtitles(); // vanilla ends renderBackground with this
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		// Above the buttons and over the top of everything, like the buttons themselves.
		Logo.draw(g, logoX, logoY, LOGO);
		String hint = "Drag to move  ·  Scroll to resize  ·  Arrows to nudge";
		Ui.str(g, hint, (width - Ui.sw(hint)) / 2, height - 16, Ui.TEXT_DIM);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) {
			return true;
		}
		Target t = targetAt(e.x(), e.y());
		if (t != null && e.button() == 1) {
			// Right-click a module → its settings (backing out lands in the mod list).
			minecraft.setScreen(t.module().settingsScreen(new ModListScreen(this)));
			return true;
		}
		if (t != null && e.button() == 0) {
			dragging = t.drag();
			dragModule = t.module();
			selectedModule = t.module();
			selectedIndex = t.index();
			Rect r = t.drag().bounds();
			dragOffX = (float) (e.x() - r.x());
			dragOffY = (float) (e.y() - r.y());
			return true;
		}
		selectedModule = null;
		selectedIndex = -1;
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(e, dx, dy);
		}
		Rect r = dragging.bounds();
		float nx = (float) (e.x() - dragOffX);
		float ny = (float) (e.y() - dragOffY);
		float w = r.w();
		float h = r.h();

		snapGuideX = snapGuideY = -1;
		if (snapEnabled()) {
			List<Float> xLines = new ArrayList<>();
			List<Float> yLines = new ArrayList<>();
			xLines.add(width / 2f);
			yLines.add(height / 2f);
			for (Target t : targets()) {
				Rect o = t.drag().bounds();
				if (o.equals(r)) {
					continue; // this is the module being dragged — it reads the same live position
				}
				// Its edges and its centre, so a chip can line up with or sit flush against it.
				xLines.add(o.x());
				xLines.add(o.x() + o.w() / 2f);
				xLines.add(o.x() + o.w());
				yLines.add(o.y());
				yLines.add(o.y() + o.h() / 2f);
				yLines.add(o.y() + o.h());
			}
			nx = snap(nx, w, xLines, true);
			ny = snap(ny, h, yLines, false);
		}

		nx = Math.max(0, Math.min(width - w, nx));
		ny = Math.max(0, Math.min(height - h, ny));
		dragging.moveTo(nx, ny);
		return true;
	}

	/**
	 * Latches the near edge {@code pos} of a {@code size}-long module onto the closest of
	 * {@code lines}, testing its own near edge, centre and far edge against each — so aligning
	 * two chips, centring one on another and butting them together all come out of one pass.
	 * Records the line that won as the guide to draw.
	 */
	private float snap(float pos, float size, List<Float> lines, boolean vertical) {
		float bestDelta = SNAP;
		float best = pos;
		float guide = -1;
		for (float line : lines) {
			for (float edge : new float[] {0f, size / 2f, size}) {
				float delta = Math.abs(pos + edge - line);
				if (delta < bestDelta) {
					bestDelta = delta;
					best = line - edge;
					guide = line;
				}
			}
		}
		if (vertical) {
			snapGuideX = guide;
		} else {
			snapGuideY = guide;
		}
		return best;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null) {
			dragging = null;
			dragModule = null;
			snapGuideX = snapGuideY = -1;
			return true;
		}
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double hDelta, double vDelta) {
		Target t = targetAt(mx, my);
		if (t != null && vDelta != 0) {
			// The target scales itself: whole module or the hovered part/box.
			t.drag().scaleBy((float) vDelta * 0.05f);
			return true;
		}
		return super.mouseScrolled(mx, my, hDelta, vDelta);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		// RShift toggles the editor closed again (Lunar behavior).
		if (ClientifyClient.menuKey.matches(e)) {
			onClose();
			return true;
		}
		if (nudge(e)) {
			return true;
		}
		return super.keyPressed(e);
	}

	/**
	 * Arrow keys move whatever is selected.
	 *
	 * <p>Dragging is the only way to place anything otherwise, and a mouse cannot reliably
	 * give you one pixel. Shift takes ten at a time for crossing the screen.
	 */
	private boolean nudge(KeyEvent e) {
		int dx = e.isRight() ? 1 : (e.isLeft() ? -1 : 0);
		int dy = e.isDown() ? 1 : (e.isUp() ? -1 : 0);
		if (dx == 0 && dy == 0) {
			return false;
		}
		Target t = selected();
		if (t == null) {
			return false;
		}
		int step = e.hasShiftDown() ? 10 : 1;
		Rect r = t.drag().bounds();
		float nx = Math.max(0, Math.min(width - r.w(), r.x() + dx * step));
		float ny = Math.max(0, Math.min(height - r.h(), r.y() + dy * step));
		t.drag().moveTo(nx, ny);
		return true;
	}

	@Override
	public void onClose() {
		ModuleManager.save();
		Menus.exit(minecraft);
	}
}
