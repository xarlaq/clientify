package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Coordinates. Vertical: X/Y/Z/Biome stacked, Direction pinned to the right of the Y line
 * (unlabeled). Horizontal: everything on one line. Each part (X/Y/Z/Direction/Biome) carries
 * its own value color. Rendered in screen space so the module font and wave/gradient work.
 */
public class CoordsModule extends HudModule {
	public enum ListMode {
		VERTICAL, HORIZONTAL
	}

	private static final int PAD = 3;
	private static final int GAP = 6;
	/** Extra vertical spacing between coordinate lines (unscaled px). */
	private static final int LINE_SPACING = 2;

	public static class Settings extends ModuleSettings {
		public ListMode listMode = ListMode.VERTICAL;
		public boolean showX = true;
		public boolean showY = true;
		public boolean showZ = true;
		public boolean showDirection = true;
		public boolean showBiome = false;
		public boolean showLabels = true;
		public boolean decimals = false;
		public boolean fullDirectionNames = false;

		// Per-part value colors + label colors (the "X: " label vs the "100" value).
		public ColorSpec xColor = new ColorSpec("#F35D12");
		public ColorSpec yColor = new ColorSpec("#F35D12");
		public ColorSpec zColor = new ColorSpec("#F35D12");
		public ColorSpec directionColor = new ColorSpec("#FFFFFF");
		public ColorSpec biomeColor = new ColorSpec("#FFFFFF");
		public ColorSpec xLabelColor = new ColorSpec("#FFFFFF");
		public ColorSpec yLabelColor = new ColorSpec("#FFFFFF");
		public ColorSpec zLabelColor = new ColorSpec("#FFFFFF");
		public ColorSpec biomeLabelColor = new ColorSpec("#FFFFFF");

		/** When on, each part is positioned independently in the editor. */
		public boolean separatePositions = false;
		public PartPos xPos = new PartPos(Anchor.TOP_LEFT, 5, 39);
		public PartPos yPos = new PartPos(Anchor.TOP_LEFT, 5, 49);
		public PartPos zPos = new PartPos(Anchor.TOP_LEFT, 5, 59);
		public PartPos directionPos = new PartPos(Anchor.TOP_LEFT, 55, 49);
		public PartPos biomePos = new PartPos(Anchor.TOP_LEFT, 5, 69);

		/**
		 * This module keeps a colour per cell rather than the one label/value pair the template
		 * holds, so the inherited copy wrote into fields nothing here draws and APPLY TO ALL looked
		 * like it skipped the coordinates entirely.
		 *
		 * <p>The template's label colour goes to every label and its value colour to every reading.
		 * Direction has no label of its own, so it takes the value colour like the numbers do.
		 */
		@Override
		public void applyAppearanceFrom(ModuleSettings o) {
			super.applyAppearanceFrom(o);
			xLabelColor.copyFrom(o.labelColor);
			yLabelColor.copyFrom(o.labelColor);
			zLabelColor.copyFrom(o.labelColor);
			biomeLabelColor.copyFrom(o.labelColor);
			xColor.copyFrom(o.valueColor);
			yColor.copyFrom(o.valueColor);
			zColor.copyFrom(o.valueColor);
			directionColor.copyFrom(o.valueColor);
			biomeColor.copyFrom(o.valueColor);
		}

		@Override
		public void applyPositionFrom(ModuleSettings o) {
			super.applyPositionFrom(o);
			if (o instanceof Settings d) {
				xPos.copyPositionFrom(d.xPos);
				yPos.copyPositionFrom(d.yPos);
				zPos.copyPositionFrom(d.zPos);
				directionPos.copyPositionFrom(d.directionPos);
				biomePos.copyPositionFrom(d.biomePos);
			}
		}

		public Settings() {
			offsetY = 39;
		}
	}

	private record Cell(String label, String value, ColorSpec labelSpec, ColorSpec valueSpec) {
	}

	private record PartRef(Cell cell, ModuleSettings.PartPos pos) {
	}

	/** Which part's color options are expanded in the settings screen (not saved). */
	private transient String expandedPart;

	public CoordsModule() {
		super("coords", "Coordinates");
	}

	@Override
	public String description() {
		return "Displays your coordinates on the HUD.";
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
		return false; // coords colors are per-part (in each part's gear dropdown)
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();

		rows.add(screen.dualToggleGear(
				"X Coordinate", () -> s.showX, v -> s.showX = v, () -> toggleExpand("x"), () -> "x".equals(expandedPart),
				"Y Coordinate", () -> s.showY, v -> s.showY = v, () -> toggleExpand("y"), () -> "y".equals(expandedPart)));
		if ("x".equals(expandedPart)) {
			partColorRows(screen, rows, s, d, s.xLabelColor, s.xColor, d.xLabelColor, d.xColor, true);
		}
		if ("y".equals(expandedPart)) {
			partColorRows(screen, rows, s, d, s.yLabelColor, s.yColor, d.yLabelColor, d.yColor, true);
		}
		rows.add(screen.dualToggleGear(
				"Z Coordinate", () -> s.showZ, v -> s.showZ = v, () -> toggleExpand("z"), () -> "z".equals(expandedPart),
				"Direction", () -> s.showDirection, v -> s.showDirection = v, () -> toggleExpand("dir"),
				() -> "dir".equals(expandedPart)));
		if ("z".equals(expandedPart)) {
			partColorRows(screen, rows, s, d, s.zLabelColor, s.zColor, d.zLabelColor, d.zColor, true);
		}
		if ("dir".equals(expandedPart)) {
			partColorRows(screen, rows, s, d, null, s.directionColor, null, d.directionColor, false);
		}
		rows.add(screen.dualToggleGear(
				"Biome", () -> s.showBiome, v -> s.showBiome = v, () -> toggleExpand("biome"),
				() -> "biome".equals(expandedPart),
				null, () -> false, v -> {
				}, () -> {
				}, () -> false));
		if ("biome".equals(expandedPart)) {
			partColorRows(screen, rows, s, d, s.biomeLabelColor, s.biomeColor, d.biomeLabelColor, d.biomeColor, true);
		}

		rows.add(screen.cycleRow("List Mode",
				() -> s.listMode == ListMode.VERTICAL ? "Vertical" : "Horizontal",
				() -> s.listMode = cycle(s.listMode, -1), () -> s.listMode = cycle(s.listMode, 1),
				() -> s.listMode = ListMode.VERTICAL));
		rows.add(screen.toggle("Show Labels", () -> s.showLabels, v -> s.showLabels = v, () -> s.showLabels = true));
		rows.add(screen.toggle("Decimal Coordinates", () -> s.decimals, v -> s.decimals = v,
				() -> s.decimals = false));
		rows.add(screen.toggle("Full Direction Names", () -> s.fullDirectionNames,
				v -> s.fullDirectionNames = v, () -> s.fullDirectionNames = false));
		rows.add(screen.toggle("Move Each Separately", () -> s.separatePositions,
				v -> s.separatePositions = v, () -> s.separatePositions = false));
	}

	private void toggleExpand(String key) {
		expandedPart = key.equals(expandedPart) ? null : key;
	}

	/** Label + Value color rows for one part, plus an Apply To All Coordinates button. */
	private void partColorRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows, Settings s, Settings d,
			ColorSpec labelSpec, ColorSpec valueSpec, ColorSpec labelDef, ColorSpec valueDef, boolean hasLabel) {
		screen.groupCard(rows, group -> {
			if (hasLabel) {
				screen.addColorRows(group, "Label Color", () -> labelSpec, () -> labelSpec.copyFrom(labelDef));
			}
			screen.addColorRows(group, "Value Color", () -> valueSpec, () -> valueSpec.copyFrom(valueDef));
			group.add(screen.button("Apply To All Coordinates", () -> applyColorsToAll(s, labelSpec, valueSpec)));
		});
	}

	/** Copies a part's label + value colors onto every coordinate part. */
	private void applyColorsToAll(Settings s, ColorSpec labelSpec, ColorSpec valueSpec) {
		for (ColorSpec v : new ColorSpec[]{s.xColor, s.yColor, s.zColor, s.directionColor, s.biomeColor}) {
			v.copyFrom(valueSpec);
		}
		if (labelSpec != null) {
			for (ColorSpec l : new ColorSpec[]{s.xLabelColor, s.yLabelColor, s.zLabelColor, s.biomeLabelColor}) {
				l.copyFrom(labelSpec);
			}
		}
	}

	// ---- values ----

	private String coord(double v, Settings s) {
		return s.decimals ? String.format(Locale.ROOT, "%.1f", v) : Long.toString((long) Math.floor(v));
	}

	// The biome only changes when you move, but sampling it is the priciest call this module
	// makes (a 3D noise lookup) and the layout asks for the row several times a frame. Keyed on
	// the block position, so standing still costs one comparison.
	private long biomePos = Long.MIN_VALUE;
	private String biomeCache = "?";

	private String biomeName(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return "?";
		}
		net.minecraft.core.BlockPos pos = mc.player.blockPosition();
		long key = pos.asLong();
		if (key != biomePos) {
			biomeCache = mc.level.getBiome(pos).unwrapKey()
					.map(k -> prettify(k.identifier().getPath()))
					.orElse("?");
			biomePos = key;
		}
		return biomeCache;
	}

	private static String prettify(String path) {
		String[] words = path.split("_");
		StringBuilder out = new StringBuilder(path.length());
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			if (out.length() > 0) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	private Cell x(Minecraft mc, Settings s) {
		return new Cell(s.showLabels ? "X: " : "", coord(mc.player.getX(), s), s.xLabelColor, s.xColor);
	}

	private Cell y(Minecraft mc, Settings s) {
		return new Cell(s.showLabels ? "Y: " : "", coord(mc.player.getY(), s), s.yLabelColor, s.yColor);
	}

	private Cell z(Minecraft mc, Settings s) {
		return new Cell(s.showLabels ? "Z: " : "", coord(mc.player.getZ(), s), s.zLabelColor, s.zColor);
	}

	private Cell dir(Minecraft mc, Settings s) {
		return new Cell("", dev.clientify.client.util.Directions.of(mc.player.getYRot(), s.fullDirectionNames),
				s.directionColor, s.directionColor);
	}

	private Cell biome(Minecraft mc, Settings s) {
		return new Cell(s.showLabels ? "Biome: " : "", biomeName(mc), s.biomeLabelColor, s.biomeColor);
	}

	// ---- measuring ----

	private float uw(Minecraft mc, Settings s, String text) {
		return uw(mc, s, text, s.scale);
	}

	/** Unscaled width measured AT {@code scale} — the atlas font is not perfectly linear. */
	private float uw(Minecraft mc, Settings s, String text, float scale) {
		return HudText.width(mc, s, text, scale) / scale;
	}

	private float cellW(Minecraft mc, Settings s, Cell c) {
		return cellW(mc, s, c, s.scale);
	}

	private float cellW(Minecraft mc, Settings s, Cell c, float scale) {
		return uw(mc, s, c.label(), scale) + uw(mc, s, c.value(), scale);
	}

	private float lineH(Minecraft mc, Settings s) {
		return lineH(mc, s, s.scale);
	}

	private float lineH(Minecraft mc, Settings s, float scale) {
		return HudText.lineHeight(mc, s, scale) / scale;
	}

	private boolean placeholder(Minecraft mc) {
		return mc.player == null && mc.screen instanceof dev.clientify.client.gui.HudEditorScreen;
	}

	// The cell lists are rebuilt for every measurement and again to draw; one build per pass is
	// enough, and it is the build that formats each coordinate into a string.
	private int vToken = -1;
	private int hToken = -1;
	private List<Cell> vCache;
	private List<Cell> hCache;

	/** Vertical stacked lines (X, Y, Z, Biome — Direction is overlaid on the host line). */
	private List<Cell> vLines(Minecraft mc, Settings s) {
		int token = dev.clientify.client.hud.HudFrame.token();
		if (token != vToken || vCache == null) {
			vCache = buildVLines(mc, s);
			vToken = token;
		}
		return vCache;
	}

	private List<Cell> buildVLines(Minecraft mc, Settings s) {
		List<Cell> out = new ArrayList<>(4);
		if (s.showX) {
			out.add(x(mc, s));
		}
		if (s.showY) {
			out.add(y(mc, s));
		}
		if (s.showZ) {
			out.add(z(mc, s));
		}
		if (s.showBiome) {
			out.add(biome(mc, s));
		}
		return out;
	}

	/** Index of the vertical line that hosts the right-aligned Direction (Y if shown, else first). */
	private int hostIndex(Settings s, int lineCount) {
		if (lineCount == 0) {
			return 0;
		}
		int idx = 0;
		if (s.showX) {
			idx = s.showY ? 1 : 0; // Y is the 2nd line when X shown, else the 1st
		}
		return Math.min(idx, lineCount - 1);
	}

	/** Horizontal inline cells (X, Y, Z, Direction, Biome). */
	private List<Cell> hCells(Minecraft mc, Settings s) {
		int token = dev.clientify.client.hud.HudFrame.token();
		if (token != hToken || hCache == null) {
			hCache = buildHCells(mc, s);
			hToken = token;
		}
		return hCache;
	}

	private List<Cell> buildHCells(Minecraft mc, Settings s) {
		List<Cell> out = new ArrayList<>(5);
		if (s.showX) {
			out.add(x(mc, s));
		}
		if (s.showY) {
			out.add(y(mc, s));
		}
		if (s.showZ) {
			out.add(z(mc, s));
		}
		if (s.showDirection) {
			out.add(dir(mc, s));
		}
		if (s.showBiome) {
			out.add(biome(mc, s));
		}
		return out;
	}

	/** Gap between the Y value and the right-aligned Direction (≈ a few digits wide). */
	private float dirGap(Minecraft mc, Settings s) {
		return uw(mc, s, "0000");
	}

	private float verticalContentW(Minecraft mc, Settings s) {
		List<Cell> lines = vLines(mc, s);
		float dirW = s.showDirection ? uw(mc, s, dir(mc, s).value()) : 0;
		int host = hostIndex(s, lines.size());
		float w = 0;
		for (int i = 0; i < lines.size(); i++) {
			float lw = cellW(mc, s, lines.get(i));
			if (i == host && s.showDirection) {
				lw += dirGap(mc, s) + dirW;
			}
			w = Math.max(w, lw);
		}
		if (lines.isEmpty() && s.showDirection) {
			w = dirW;
		}
		return w;
	}

	private int verticalLineCount(Minecraft mc, Settings s) {
		int n = vLines(mc, s).size();
		if (n == 0 && s.showDirection) {
			n = 1;
		}
		return n;
	}

	// One layout per render pass. Rebuilding these means a String.format per coordinate and a
	// text measurement per cell, and the pass asks for the size three times before drawing.
	private int sizeToken = -1;
	private float sizeW;
	private float sizeH;

	private void ensureSized(Minecraft mc) {
		int token = dev.clientify.client.hud.HudFrame.token();
		if (token == sizeToken) {
			return;
		}
		sizeW = measureWidth(mc);
		sizeH = measureHeight(mc);
		sizeToken = token;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		ensureSized(mc);
		return sizeW;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		ensureSized(mc);
		return sizeH;
	}

	private float measureWidth(Minecraft mc) {
		Settings s = (Settings) settings();
		if (placeholder(mc)) {
			return 70;
		}
		if (mc.player == null) {
			return 0;
		}
		float content;
		if (s.listMode == ListMode.HORIZONTAL) {
			List<Cell> cells = hCells(mc, s);
			if (cells.isEmpty()) {
				return 0;
			}
			content = 0;
			for (int i = 0; i < cells.size(); i++) {
				content += cellW(mc, s, cells.get(i)) + (i > 0 ? GAP : 0);
			}
		} else {
			content = verticalContentW(mc, s);
			if (content <= 0) {
				return 0;
			}
		}
		return content + s.extraW(PAD);
	}

	private float measureHeight(Minecraft mc) {
		Settings s = (Settings) settings();
		if (placeholder(mc)) {
			return 34;
		}
		if (mc.player == null) {
			return 0;
		}
		int lines = s.listMode == ListMode.HORIZONTAL ? (hCells(mc, s).isEmpty() ? 0 : 1)
				: verticalLineCount(mc, s);
		if (lines == 0) {
			return 0;
		}
		return lines * lineH(mc, s) + (lines - 1) * LINE_SPACING + s.extraH(PAD);
	}

	private void drawCell(GuiGraphicsExtractor g, Minecraft mc, Settings s, Cell c, float x, float y) {
		drawCell(g, mc, s, c, x, y, s.scale);
	}

	private void drawCell(GuiGraphicsExtractor g, Minecraft mc, Settings s, Cell c, float x, float y, float scale) {
		if (!c.label().isEmpty()) {
			HudText.draw(g, mc, s, c.label(), x, y, c.labelSpec(), 0, c.label().length(), scale);
			x += HudText.width(mc, s, c.label(), scale);
		}
		HudText.draw(g, mc, s, c.value(), x, y, c.valueSpec(), 0, c.value().length(), scale);
	}

	// ---- separated parts ----

	private List<PartRef> visibleParts(Minecraft mc, Settings s) {
		List<PartRef> out = new ArrayList<>(5);
		if (mc.player == null) {
			return out;
		}
		if (s.showX) {
			out.add(new PartRef(x(mc, s), s.xPos));
		}
		if (s.showY) {
			out.add(new PartRef(y(mc, s), s.yPos));
		}
		if (s.showZ) {
			out.add(new PartRef(z(mc, s), s.zPos));
		}
		if (s.showDirection) {
			out.add(new PartRef(dir(mc, s), s.directionPos));
		}
		if (s.showBiome) {
			out.add(new PartRef(biome(mc, s), s.biomePos));
		}
		return out;
	}

	private float partW(Minecraft mc, Settings s, Cell c, float scale) {
		return (cellW(mc, s, c, scale) + s.extraW(PAD)) * scale;
	}

	private float partH(Minecraft mc, Settings s, float scale) {
		return (lineH(mc, s, scale) + s.extraH(PAD)) * scale;
	}

	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		Settings s = (Settings) settings();
		if (!s.separatePositions) {
			return super.draggables(mc, screenW, screenH);
		}
		List<Draggable> out = new ArrayList<>();
		for (PartRef pr : visibleParts(mc, s)) {
			ModuleSettings.PartPos pos = pr.pos();
			// Measure at the part's effective scale (module scale × per-part factor).
			float eff = s.scale * pos.scale;
			float w = partW(mc, s, pr.cell(), eff);
			float h = partH(mc, s, eff);
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
		float scale = s.scale;

		if (s.separatePositions && mc.player != null) {
			for (PartRef pr : visibleParts(mc, s)) {
				// Render at the part's effective scale (module scale × per-part factor).
				float eff = s.scale * pr.pos().scale;
				float w = partW(mc, s, pr.cell(), eff);
				float h = partH(mc, s, eff);
				Rect pb = partBounds(pr.pos(), w, h, g.guiWidth(), g.guiHeight());
				int px = Math.round(pb.x());
				int py = Math.round(pb.y());
				drawChromeScreen(g, mc, px, py, Math.round(pb.w()), Math.round(pb.h()), eff);
				drawCell(g, mc, s, pr.cell(), px + s.insetX(PAD) * eff,
						py + s.insetY(PAD) * eff, eff);
			}
			return;
		}

		Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
		int rx = Math.round(r.x());
		int ry = Math.round(r.y());
		drawChromeScreen(g, mc, rx, ry, Math.round(r.w()), Math.round(r.h()));

		float ox = rx + s.insetX(PAD) * scale;
		float oy = ry + s.insetY(PAD) * scale;

		if (placeholder(mc)) {
			HudText.draw(g, mc, s, "Coordinates", ox, oy, 0xFF9A9AA5);
			return;
		}
		if (mc.player == null) {
			return;
		}

		float lh = HudText.lineHeight(mc, s);
		if (s.listMode == ListMode.HORIZONTAL) {
			float x = ox;
			boolean first = true;
			for (Cell c : hCells(mc, s)) {
				if (!first) {
					x += GAP * scale;
				}
				drawCell(g, mc, s, c, x, oy);
				x += cellW(mc, s, c) * scale;
				first = false;
			}
			return;
		}

		// Vertical: stacked lines, Direction right-aligned on the host line.
		List<Cell> lines = vLines(mc, s);
		int host = hostIndex(s, lines.size());
		float contentW = verticalContentW(mc, s) * scale;
		float step = lh + LINE_SPACING * scale;
		for (int i = 0; i < lines.size(); i++) {
			drawCell(g, mc, s, lines.get(i), ox, oy + i * step);
		}
		if (s.showDirection) {
			Cell dc = dir(mc, s);
			float dw = HudText.width(mc, s, dc.value());
			float dy = oy + (lines.isEmpty() ? 0 : host) * step;
			drawCell(g, mc, s, dc, ox + contentW - dw, dy);
		}
	}
}
