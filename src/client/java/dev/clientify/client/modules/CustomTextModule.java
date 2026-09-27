package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.config.ModuleSettings.FontMode;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.BlurBackdrop;
import dev.clientify.client.hud.ChromeMask;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import dev.clientify.client.util.Draw;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Free-form text on the HUD: any number of separately draggable text boxes, each with its
 * OWN formatting (color, font, background, shadow, scale — the gear on its text row). Type
 * {@code \n} inside a box for a line break; the bin icon removes a box.
 */
public class CustomTextModule extends HudModule {
	private static final int PAD_X = 5;
	private static final int PAD_Y = 3;
	private static final int LINE_GAP = 2;

	/** Vertical step between entries when their positions are reset, so they do not stack. */
	private static final float RESET_STRIDE = 12f;

	public static class Box {
		public String text = "Sample Text";
		public ModuleSettings.PartPos pos = new ModuleSettings.PartPos();
		public float scale = 1f;
		public FontMode font = FontMode.MINECRAFT;
		public boolean shadow = true;
		public ColorSpec color = new ColorSpec("#FFFFFF");
		public boolean background = true;
		public ColorSpec bgColor = new ColorSpec("#8C101014");
		/** Extra background beyond the text, split evenly on both sides (as on other modules). */
		public int bgWidth;
		public int bgHeight;
		public boolean bgRounded;
		public int bgRadius = 6;
		public boolean bgBlur;
		/** A line round the box, drawn outward the way every other module draws one. */
		public boolean border;
		public float borderThickness = 1f;
		public ColorSpec borderColor = new ColorSpec("#F35D12");

		public Box() {
		}

		public Box(float ox, float oy) {
			pos.ox = ox;
			pos.oy = oy;
		}
	}

	public static class Settings extends ModuleSettings {
		public List<Box> boxes = new ArrayList<>(List.of(new Box()));

		/**
		 * Every box takes the module defaults, since a box is where this module's appearance lives.
		 *
		 * <p>Without this, Apply To All Modules wrote into the module-wide fields that this module
		 * does not read, and the button appeared to do nothing here at all.
		 *
		 * <p>Text colour is left alone: a box carries one colour rather than the label-and-value
		 * pair the defaults hold, and there is no honest way to choose between them.
		 */
		@Override
		public void applyAppearanceFrom(ModuleSettings o) {
			super.applyAppearanceFrom(o);
			for (Box box : boxes) {
				box.font = o.font;
				box.shadow = o.textShadow;
				box.background = o.background;
				box.bgColor.copyFrom(o.bgColor);
				box.bgWidth = o.bgWidth;
				box.bgHeight = o.bgHeight;
				box.bgRounded = o.bgRounded;
				box.bgRadius = o.bgRadius;
				box.bgBlur = o.bgBlur;
				box.border = o.border;
				box.borderThickness = o.borderThickness;
				box.borderColor.copyFrom(o.borderColor);
			}
		}

		/**
		 * Each box carries its own position and the module offset is unused, so without
		 * this the button would do nothing here. They are stacked down from the default spot rather
		 * than all dropped on it, which would read as the reset having merged them.
		 */
		@Override
		public void applyPositionFrom(ModuleSettings o) {
			super.applyPositionFrom(o);
			ModuleSettings.PartPos start = new Box().pos;
			for (int i = 0; i < boxes.size(); i++) {
				ModuleSettings.PartPos p = boxes.get(i).pos;
				p.copyPositionFrom(start);
				p.oy += i * RESET_STRIDE;
			}
		}
	}

	/** Scratch settings view so HudText renders with a box's own font/scale/shadow. */
	private final ModuleSettings scratch = new ModuleSettings();
	private transient String expandedSection;

	public CustomTextModule() {
		super("customtext", "Custom Text");
	}

	@Override
	public String description() {
		return "Puts your own text anywhere on the HUD.";
	}

	@Override
	public boolean hasAppearance() {
		return false;
	}

	/**
	 * Blur is per box here, and the module-wide background fields are not even shown.
	 *
	 * <p>This has to agree with what render actually blurs. wantsBlur does not gate the capture --
	 * it decides whether the blur RADIUS is overridden for the frame -- so a module that draws a
	 * blurred chip while reporting false gets the captured world unblurred, a sharp copy of the
	 * scene. That only looked right when some other module happened to want blur too.
	 */
	@Override
	public boolean wantsBlur() {
		for (Box box : ((Settings) settings()).boxes) {
			if (box.background && box.bgBlur) {
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
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	/**
	 * Split on a typed backslash-n, compiled once.
	 *
	 * <p>{@code String.split} only skips the regex engine for a single plain character or an escape
	 * whose second character is not a letter — this pattern is neither, so it was compiling a fresh
	 * one on every call, and both callers are per-frame paths.
	 */
	private static final java.util.regex.Pattern TYPED_NEWLINE = java.util.regex.Pattern.compile("\\\\n");

	private static String[] lines(Box box) {
		return TYPED_NEWLINE.split(box.text, -1);
	}

	private ModuleSettings view(Box box) {
		scratch.font = box.font;
		scratch.scale = box.scale;
		scratch.textShadow = box.shadow;
		return scratch;
	}

	/** A box with nothing drawn behind it measures its text exactly, so it can sit in a corner. */
	private static boolean padded(Box box) {
		return box.background || box.border;
	}

	private void expand(String key) {
		expandedSection = key.equals(expandedSection) ? null : key;
	}

	@Override
	public List<TextField> textFields() {
		Settings s = (Settings) settings();
		List<TextField> fields = new ArrayList<>(s.boxes.size());
		for (int i = 0; i < s.boxes.size(); i++) {
			Box box = s.boxes.get(i);
			String key = "box" + i;
			fields.add(new TextField("Text " + (i + 1), () -> box.text, v -> box.text = v, "Sample Text",
					s.boxes.size() > 1 ? () -> {
						s.boxes.remove(box);
						expandedSection = null;
					} : null,
					() -> expand(key), () -> key.equals(expandedSection)));
		}
		return fields;
	}

	@Override
	public void appendTextFieldGear(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows, int index) {
		Settings s = (Settings) settings();
		if (index >= s.boxes.size()) {
			return;
		}
		Box box = s.boxes.get(index);
		Box d = new Box();
		rows.add(screen.sliderRow("Scale", 0.5f, 3f, 0.05f, () -> box.scale, v -> box.scale = v,
				"%.2f", () -> box.scale = d.scale));
		rows.add(screen.cycleRow("Font", () -> box.font.label(),
				() -> box.font = cycle(box.font, -1), () -> box.font = cycle(box.font, 1),
				() -> box.font = d.font));
		rows.add(screen.toggle("Text Shadow", () -> box.shadow, v -> box.shadow = v,
				() -> box.shadow = d.shadow));
		screen.addColorRows(rows, "Text Color", () -> box.color, () -> box.color.copyFrom(d.color));
		rows.add(screen.toggle("Background", () -> box.background, v -> box.background = v,
				() -> box.background = d.background));
		if (box.background) {
			screen.addColorRows(rows, "Background Color", () -> box.bgColor, () -> box.bgColor.copyFrom(d.bgColor));
			rows.add(screen.sliderRow("Extra Width", 0f, 40f, 1f, () -> (float) box.bgWidth,
					v -> box.bgWidth = Math.round(v), "%.0f", () -> box.bgWidth = d.bgWidth));
			rows.add(screen.sliderRow("Extra Height", 0f, 20f, 1f, () -> (float) box.bgHeight,
					v -> box.bgHeight = Math.round(v), "%.0f", () -> box.bgHeight = d.bgHeight));
			rows.add(screen.blurToggle("Background Blur", () -> box.bgBlur, v -> box.bgBlur = v,
					() -> box.bgBlur = d.bgBlur));
			rows.add(screen.roundedRow(() -> box.bgRounded, v -> box.bgRounded = v,
					() -> box.bgRadius, v -> box.bgRadius = v, () -> {
						box.bgRounded = d.bgRounded;
						box.bgRadius = d.bgRadius;
					}));
		}
		// Outside the background block on purpose: a line with nothing behind it is a look,
		// and every other module lets you have it.
		rows.add(screen.sliderRow("Border Thickness", 0f, 5f, 1f, () -> box.border
				? box.borderThickness : 0f, v -> {
					box.border = v >= 1f;
					if (box.border) {
						box.borderThickness = v;
					}
				}, "%.0f", () -> {
					box.border = d.border;
					box.borderThickness = d.borderThickness;
				}));
		if (box.border) {
			screen.addColorRows(rows, "Border Color", () -> box.borderColor,
					() -> box.borderColor.copyFrom(d.borderColor));
		}
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		rows.add(screen.button("New Text Box", () -> {
			s.boxes.add(new Box(5 + s.boxes.size() * 8, 5 + s.boxes.size() * 16));
			screen.refreshTextFields();
		}));
	}

	/** A box's backdrop and fill, in screen space (boxes lay out in screen coords, not a pose). */
	private void paintBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int rad, int argb, boolean blur) {
		if (blur) {
			Draw.backdropRounded(g, BlurBackdrop.TEXTURE_ID, x, y, w, h, rad,
					0, 0, 1f, g.guiWidth(), g.guiHeight(), BlurBackdrop.vFlip());
		}
		Draw.smoothRounded(g, x, y, w, h, rad, argb);
	}

	/** Screen-space size of one box (content + padding, at the box's own scale). */
	private float[] boxSize(Minecraft mc, Box box) {
		ModuleSettings v = view(box);
		float maxW = 0;
		String[] lines = lines(box);
		for (String line : lines) {
			maxW = Math.max(maxW, HudText.width(mc, v, line));
		}
		float lineH = HudText.lineHeight(mc, v);
		// The vanilla font's advance runs a pixel past its ink both ways; without dropping that
		// the text sits up and to the left of centre inside the padding.
		float trail = HudText.trailing(v, box.scale);
		float w = maxW - trail + ModuleSettings.extra(padded(box), PAD_X, box.bgWidth) * box.scale;
		float h = lines.length * lineH - trail + (lines.length - 1) * LINE_GAP * box.scale
				+ ModuleSettings.extra(padded(box), PAD_Y, box.bgHeight) * box.scale;
		return new float[] {w, h};
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
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		Settings s = (Settings) settings();
		List<Draggable> out = new ArrayList<>(s.boxes.size());
		for (Box box : s.boxes) {
			float[] size = boxSize(mc, box);
			out.add(new Draggable() {
				@Override
				public Rect bounds() {
					return partBounds(box.pos, size[0], size[1], screenW, screenH);
				}

				@Override
				public void moveTo(float x, float y) {
					partMoveTo(box.pos, x, y, size[0], size[1], screenW, screenH);
				}

				@Override
				public void scaleBy(float delta) {
					box.scale = Math.max(0.5f, Math.min(3f, box.scale + delta));
				}
			});
		}
		return out;
	}

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = (Settings) settings();
		for (Box box : s.boxes) {
			float[] size = boxSize(mc, box);
			Rect r = partBounds(box.pos, size[0], size[1], g.guiWidth(), g.guiHeight());
			if (box.background || box.border) {
				int bx = Math.round(r.x());
				int by = Math.round(r.y());
				int bw = Math.round(r.w());
				int bh = Math.round(r.h());
				int rad = box.bgRounded ? Math.round(Math.max(0, box.bgRadius) * box.scale) : 0;
				int argb = box.bgColor.argbAt(0, 1);
				int bc = box.borderColor.argbAt(0, 1);
				int t = box.border ? Math.max(1, Math.round(box.borderThickness * box.scale)) : 0;
				boolean blur = box.background && box.bgBlur && BlurBackdrop.prepare(mc);
				boolean fill = box.background;
				Runnable paint = () -> {
					if (fill) {
						paintBox(g, bx, by, bw, bh, rad, argb, blur);
					}
					if (t > 0) {
						// Outward, so the line sits around the box rather than eating into it.
						Draw.thickBorder(g, bx - t, by - t, bw + t * 2, bh + t * 2,
								rad == 0 ? 0 : rad + t, t, bc);
					}
				};
				// Straight to the fill unless overlap clipping is actually on (see ChromeMask).
				if (ChromeMask.enabled()) {
					ChromeMask.draw(g, r.x() - t, r.y() - t, r.w() + t * 2, r.h() + t * 2,
							rad == 0 ? 0 : rad + t, paint);
				} else {
					paint.run();
				}
			}

			ModuleSettings v = view(box);
			String[] lines = lines(box);
			int total = 0;
			for (String line : lines) {
				total += line.length();
			}
			float lineH = HudText.lineHeight(mc, v);
			float tx = r.x() + ModuleSettings.inset(padded(box), PAD_X, box.bgWidth) * box.scale;
			float ty = r.y() + ModuleSettings.inset(padded(box), PAD_Y, box.bgHeight) * box.scale;
			int charBase = 0;
			for (String line : lines) {
				HudText.draw(g, mc, v, line, tx, ty, box.color, charBase, total);
				charBase += line.length();
				ty += lineH + LINE_GAP * box.scale;
			}
		}
	}
}
