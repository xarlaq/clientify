import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Bakes the module icons into the atlas the mod ships.
 *
 * <p>The icons are Tabler (MIT, https://tabler.io/icons). Only these few are rendered out, rather
 * than bundling the whole 2.7 MB webfont for twenty-odd glyphs — the atlas is a few tens of KB and
 * needs no font parsing at runtime.
 *
 * <p>Run against the webfont, which is NOT kept in the repo:
 *
 * <pre>
 * curl -o tabler-icons.ttf https://cdn.jsdelivr.net/npm/@tabler/icons-webfont@3.45.0/dist/fonts/tabler-icons.ttf
 * java tools/IconAtlas.java tabler-icons.ttf src/client/resources/assets/clientify/textures/ui/module_icons.png
 * </pre>
 *
 * <p>Codepoints come from the package's own CSS (.ti-&lt;name&gt;:before). Adding an icon means
 * appending to {@link #ICONS} and re-running — order is the cell index the mod indexes by, so add
 * to the END and never reorder.
 */
public final class IconAtlas {
	/**
	 * Cells are 48px for icons drawn around 14px on screen. At the GUI scales people actually use
	 * that lands just under 1:1, so the glyph is always being shrunk slightly rather than blown up
	 * — line art survives minification and falls apart when magnified. A 64px cell was too far
	 * from the drawn size and the 2px strokes turned to grey mush.
	 */
	private static final int CELL = 48;
	private static final int COLS = 8;
	/** Rendered a little under the cell so no glyph touches its neighbour's edge when sampled. */
	private static final float GLYPH_SIZE = 40f;

	/** Drawn on Tabler's own 24x24 grid; the stroke and colour are already set up. */
	private interface Art {
		void draw(Graphics2D g);
	}

	/** Either a glyph lifted from the font, or one drawn here in the same geometry. */
	private record Icon(String name, int codepoint, Art art, boolean flipY) {
		static Icon glyph(String name, String hex) {
			return new Icon(name, Integer.parseInt(hex, 16), null, false);
		}

		/** Same glyph, mirrored top to bottom — for when the stock one points the wrong way. */
		static Icon flipped(String name, String hex) {
			return new Icon(name, Integer.parseInt(hex, 16), null, true);
		}

		static Icon drawn(String name, Art art) {
			return new Icon(name, 0, art, false);
		}
	}

	/**
	 * Cell order is the index the mod uses — replacing the icon in a cell is fine, moving one is
	 * not. Stock choices follow the conventions players already know from Lunar (signal bars for
	 * ping, a pin for waypoints, a drumstick for saturation). Where the concept has no honest
	 * stock glyph — an XYZ axis, a shulker, a boss bar — the icon is drawn below rather than
	 * borrowing something that only nearly fits.
	 */
	private static final Icon[] ICON_LIST = {
			Icon.glyph("gauge", "EAB1"),           // 0  fps
			Icon.glyph("antenna-bars-5", "ECCB"),  // 1  ping
			Icon.drawn("reach", IconAtlas::reach), // 2  reach
			Icon.drawn("axes", IconAtlas::axes),   // 3  coords
			Icon.glyph("run", "EC82"),             // 4  sprint
			Icon.glyph("flask", "EBD2"),           // 5  effects
			Icon.glyph("shield", "EB24"),          // 6  armor (id is "armorhud") — see chestplate()
			Icon.glyph("bulb", "EA51"),            // 7  fullbright
			Icon.drawn("shulker", IconAtlas::shulker),   // 8  shulker tooltip
			Icon.drawn("hitbox", IconAtlas::hitbox),     // 9  hitbox
			Icon.glyph("brush", "EBB8"),           // 10 hit colour
			Icon.flipped("meat", "EF12"),          // 11 saturation — bone to the bottom right
			Icon.glyph("typography", "EBC5"),      // 12 custom text
			Icon.glyph("list-numbers", "EF11"),    // 13 item counter
			Icon.glyph("cloud-rain", "EA72"),      // 14 weather
			Icon.drawn("daylight", IconAtlas::daylight), // 15 time
			Icon.drawn("crosshair", IconAtlas::crosshair), // 16 crosshair
			Icon.glyph("zoom-in", "EB56"),         // 17 zoom
			Icon.glyph("tag", "10096"),            // 18 nametags
			Icon.glyph("letter-case", "EEA5"),     // 19 title
			Icon.glyph("message", "EAEF"),         // 20 action bar
			Icon.drawn("bossbar", IconAtlas::bossbar),   // 21 boss bar
			Icon.glyph("list-details", "EF40"),    // 22 scoreboard
			Icon.glyph("layout-list", "EC14"),     // 23 tab list
			Icon.glyph("map-pin", "EAE8"),         // 24 waypoints
			Icon.drawn("guiscale", IconAtlas::guiScale), // 25 gui scale
			Icon.drawn("attackindicator", IconAtlas::attackIndicator), // 26 attack indicator
			Icon.drawn("overlay", IconAtlas::overlay),   // 27 overlay
			Icon.drawn("oreoutlines", IconAtlas::oreOutlines), // 28 ore outlines
			Icon.drawn("keystrokes", IconAtlas::keystrokes), // 29 keystrokes
	};

	// NOTE: cell 28 in the SHIPPED atlas is the hand-painted totem, not the ore outlines this list
	// still names — ore outlines were removed from the mod and the totem was traced by hand rather
	// than generated. Re-running this tool would paint over it. Fix 28 before regenerating.

	// ---- drawn icons: 24x24, 2px round-capped strokes, elements kept a few px off the edge ----

	/** Distance between two ends: a measured span with stops at both ends. */
	private static void reach(Graphics2D g) {
		g.draw(new Line2D.Double(4, 6, 4, 18));
		g.draw(new Line2D.Double(20, 6, 20, 18));
		g.draw(new Line2D.Double(4, 12, 20, 12));
		g.draw(new Line2D.Double(8, 8.5, 4.5, 12));
		g.draw(new Line2D.Double(8, 15.5, 4.5, 12));
		g.draw(new Line2D.Double(16, 8.5, 19.5, 12));
		g.draw(new Line2D.Double(16, 15.5, 19.5, 12));
	}

	/** Three axes from a corner — what coordinates actually are, rather than a map pin. */
	private static void axes(Graphics2D g) {
		g.draw(new Line2D.Double(7, 17, 7, 4));
		g.draw(new Line2D.Double(5, 6.5, 7, 4));
		g.draw(new Line2D.Double(9, 6.5, 7, 4));
		g.draw(new Line2D.Double(7, 17, 20, 17));
		g.draw(new Line2D.Double(17.5, 15, 20, 17));
		g.draw(new Line2D.Double(17.5, 19, 20, 17));
		g.draw(new Line2D.Double(7, 17, 3, 21));
	}

	/**
	 * A shulker box with its lid tipped open, hinged at the left.
	 *
	 * <p>Sized around the one rule that governs this whole set: a 2 unit stroke eats 2 units off
	 * every side, so any interior under about 5 units fills in solid at the size these are drawn.
	 * The first version gave the base 7 units and the lid 4 — both closed up into blobs. The base
	 * is 10 units deep now, and the lid is a single open flap rather than a closed rectangle.
	 */
	/**
	 * A shulker box head-on: the box, with the lid stepping up out of the middle. Drawn as an
	 * open polyline across the box rather than a closed shape, so there is no enclosed sliver to
	 * fill in — the earlier lids all failed on exactly that.
	 */
	private static void shulker(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(4, 4, 16, 16, 2, 2));
		java.awt.geom.Path2D.Double lid = new java.awt.geom.Path2D.Double();
		lid.moveTo(4, 13);
		lid.lineTo(9, 13);
		lid.lineTo(9, 8.5);
		lid.lineTo(15, 8.5);
		lid.lineTo(15, 13);
		lid.lineTo(20, 13);
		g.draw(lid);
	}

	/** A tall wireframe box — an entity's hitbox is taller than it is wide, so the icon is too. */
	private static void hitbox(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(4.5, 7.5, 9, 13, 1, 1)); // front face
		g.draw(new Line2D.Double(4.5, 7.5, 8.5, 3.5));              // rising edges
		g.draw(new Line2D.Double(13.5, 7.5, 17.5, 3.5));
		g.draw(new Line2D.Double(8.5, 3.5, 17.5, 3.5));             // back top
		g.draw(new Line2D.Double(17.5, 3.5, 17.5, 16.5));           // back right
		g.draw(new Line2D.Double(13.5, 20.5, 17.5, 16.5));          // bottom right
	}

	/**
	 * NOT USED — cell 6 is the shield glyph, on the user's call after three attempts at armour.
	 * Kept as the record: a torso with pointed shoulders and a sharp V reads as a crown, and the
	 * square-cut version below is legible but still says "vest" more than "armour" at 14px. A
	 * shield says it immediately, which is worth more than being literal about the module name.
	 */
	private static void chestplate(Graphics2D g) {
		java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();
		p.moveTo(4.5, 19.5);
		p.lineTo(4.5, 8);
		p.lineTo(9, 6);        // flat left shoulder — points here made it a crown
		p.lineTo(9, 11);       // neck cut straight down
		p.lineTo(15, 11);      // and across: 6 units wide, 5 deep, so it survives at 14px
		p.lineTo(15, 6);
		p.lineTo(19.5, 8);
		p.lineTo(19.5, 19.5);
		p.closePath();
		g.draw(p);
	}

	/** Time of day: a sun on the horizon, which is what the module sets. */
	private static void daylight(Graphics2D g) {
		g.draw(new Line2D.Double(3, 18, 21, 18));
		g.draw(new Arc2D.Double(7, 13, 10, 10, 0, 180, Arc2D.OPEN));
		g.draw(new Line2D.Double(12, 8, 12, 5.5));
		g.draw(new Line2D.Double(6.5, 10.5, 4.8, 8.8));
		g.draw(new Line2D.Double(17.5, 10.5, 19.2, 8.8));
	}

	/** A crosshair as a game draws one: ring, four ticks, centre dot. */
	private static void crosshair(Graphics2D g) {
		g.draw(new Ellipse2D.Double(5.5, 5.5, 13, 13));
		g.draw(new Line2D.Double(12, 2, 12, 5));
		g.draw(new Line2D.Double(12, 19, 12, 22));
		g.draw(new Line2D.Double(2, 12, 5, 12));
		g.draw(new Line2D.Double(19, 12, 22, 12));
		g.fill(new Ellipse2D.Double(11, 11, 2, 2));
	}

	/** A bar part way along, which is all a boss bar is. */
	private static void bossbar(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(3, 9, 18, 6, 3, 3));
		g.fill(new RoundRectangle2D.Double(5.5, 11.5, 8, 1, 1, 1));
	}

	/**
	 * NOT USED — kept as the record of a failed attempt. A drumstick is the right idea for
	 * saturation, but a ring with a stick coming off it reads as the magnifier two cards away,
	 * and it did at 14px. Tabler's own meat glyph is finer than ideal but unambiguous, so cell 11
	 * stays with the font. Anything replacing it has to not be round-with-a-handle.
	 */
	private static void drumstick(Graphics2D g) {
		g.draw(new Ellipse2D.Double(4, 3.5, 11, 11));
		g.draw(new Line2D.Double(13, 13, 17.5, 17.5));
		g.draw(new Line2D.Double(17.5, 17.5, 16.5, 21));
		g.draw(new Line2D.Double(17.5, 17.5, 21, 20));
	}

	/** A panel being resized. */
	private static void guiScale(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(3, 5, 18, 14, 3, 3));
		g.draw(new Line2D.Double(9, 15, 15, 9));
		g.draw(new Line2D.Double(9, 12, 9, 15));
		g.draw(new Line2D.Double(9, 15, 12, 15));
		g.draw(new Line2D.Double(15, 12, 15, 9));
		g.draw(new Line2D.Double(12, 9, 15, 9));
	}

	/**
	 * The attack cooldown as the game draws it: the little bar under the crosshair, part filled.
	 * A sword was the obvious pick and the wrong one — at 14px a blade with a crossguard is a grey
	 * smudge, and the module is about the BAR, not the weapon.
	 */
	private static void attackIndicator(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(2, 9, 20, 6, 3, 3));
		g.fill(new RoundRectangle2D.Double(4, 11, 9, 2, 1, 1));
	}

	/** Two panes over one another — the same shorthand every menu uses for layers. */
	private static void overlay(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(3, 3, 13, 13, 3, 3));
		// Open on the two hidden sides, so the front pane reads as being on top rather than as a
		// grid: a closed second square here turns into a solid block at icon size.
		g.draw(new Line2D.Double(19, 8, 21, 8));
		g.draw(new Line2D.Double(21, 8, 21, 21));
		g.draw(new Line2D.Double(21, 21, 8, 21));
		g.draw(new Line2D.Double(8, 21, 8, 19));
	}

	/**
	 * A gem inside a block: the outline the module draws, around the thing it draws it on. An ore
	 * texture at icon size is just speckles, so the box is what carries the meaning.
	 */
	private static void oreOutlines(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(2, 2, 20, 20, 3, 3));
		java.awt.geom.Path2D.Double gem = new java.awt.geom.Path2D.Double();
		gem.moveTo(12, 7);
		gem.lineTo(17, 12);
		gem.lineTo(12, 17);
		gem.lineTo(7, 12);
		gem.closePath();
		g.fill(gem);
	}

	/**
	 * A keyboard: the body, three keys and a space bar under them.
	 *
	 * <p>Only four marks inside, and well apart. This is drawn at about fourteen pixels in the mod
	 * list, and a full set of keys turns to grey mush at that size.
	 */
	private static void keystrokes(Graphics2D g) {
		g.draw(new RoundRectangle2D.Double(3, 6.5, 18, 11, 2.5, 2.5));
		for (double x : new double[] {7.5, 12, 16.5}) {
			g.fill(new java.awt.geom.Ellipse2D.Double(x - 1, 9.5, 2, 2));
		}
		g.draw(new Line2D.Double(7.5, 14.5, 16.5, 14.5));
	}

	/** Kept only as a record of what each cell used to be; nothing reads it. */
	private static final String[][] PREVIOUS_CHOICES = {
			{"gauge", "EAB1"},           // 0  fps
			{"antenna-bars-5", "ECCB"},  // 1  ping — signal bars, not a wifi fan
			{"ruler-measure", "F291"},   // 2  reach
			{"topology-star-3", "F5E1"}, // 3  coords — joined points, like an axis marker
			{"run", "EC82"},             // 4  sprint
			{"flask", "EBD2"},           // 5  effects
			{"shield", "EB24"},          // 6  armor
			{"bulb", "EA51"},            // 7  fullbright
			{"package", "EAFF"},         // 8  shulker tooltip — a lidded box, not a bare cube
			{"cube", "FA97"},            // 9  hitbox — the wireframe box around an entity
			{"brush", "EBB8"},           // 10 hit colour
			{"meat", "EF12"},            // 11 saturation
			{"typography", "EBC5"},      // 12 custom text
			{"list-numbers", "EF11"},    // 13 item counter
			{"cloud-rain", "EA72"},      // 14 weather
			{"sun-moon", "F4A3"},        // 15 time — it changes time of day, it is not a clock
			{"crosshair", "EC3E"},       // 16 crosshair
			{"zoom-in", "EB56"},         // 17 zoom
			{"tag", "10096"},            // 18 nametags — above the BMP, hence int codepoints
			{"letter-case", "EEA5"},     // 19 title
			{"message", "EAEF"},         // 20 action bar
			{"skull", "F292"},           // 21 boss bar
			{"list-details", "EF40"},    // 22 scoreboard
			{"layout-list", "EC14"},     // 23 tab list — the list itself reads better than people
			{"map-pin", "EAE8"},         // 24 waypoints
			{"resize", "EECF"},          // 25 gui scale
	};

	private IconAtlas() {
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: java IconAtlas.java <tabler-icons.ttf> <out.png>");
			System.exit(2);
		}
		Font font = Font.createFont(Font.TRUETYPE_FONT, new File(args[0])).deriveFont(GLYPH_SIZE);
		int rows = (ICON_LIST.length + COLS - 1) / COLS;
		BufferedImage atlas = new BufferedImage(COLS * CELL, rows * CELL, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = atlas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setFont(font);
		g.setColor(Color.WHITE); // white mask; the mod tints it when drawing
		FontMetrics fm = g.getFontMetrics();

		int drawn = 0;
		for (int i = 0; i < ICON_LIST.length; i++) {
			Icon icon = ICON_LIST[i];
			int cellX = (i % COLS) * CELL;
			int cellY = (i / COLS) * CELL;
			if (icon.art() != null) {
				Graphics2D cg = (Graphics2D) g.create(cellX, cellY, CELL, CELL);
				// Tabler's own canvas: 24 units square, 2 unit strokes, round caps and joins.
				cg.scale(CELL / 24.0, CELL / 24.0);
				cg.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				icon.art().draw(cg);
				cg.dispose();
				drawn++;
				continue;
			}
			String glyph = new String(Character.toChars(icon.codepoint()));
			int x = cellX + (CELL - fm.stringWidth(glyph)) / 2;
			int y = cellY + (CELL - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();
			if (icon.flipY()) {
				Graphics2D fg = (Graphics2D) g.create();
				fg.translate(0, 2.0 * cellY + CELL);
				fg.scale(1, -1);
				fg.drawString(glyph, x, y);
				fg.dispose();
				continue;
			}
			g.drawString(glyph, x, y);
			if (fm.stringWidth(glyph) <= 1) {
				System.err.println("WARNING: " + icon.name() + " rendered empty — wrong codepoint?");
			}
		}
		g.dispose();
		File out = new File(args[1]);
		ImageIO.write(atlas, "PNG", out);
		System.out.printf("%d icons (%d drawn here) -> %s (%dx%d, %d KB)%n",
				ICON_LIST.length, drawn, out, atlas.getWidth(), atlas.getHeight(), out.length() / 1024);
	}
}
