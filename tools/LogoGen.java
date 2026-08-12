import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Bakes the mod's icon: an orange C inside a wireframe cube, on the same dark rounded plate the
 * menus use. Also bakes the plate-less mark the menus themselves draw.
 *
 * <pre>
 * java tools/LogoGen.java icon src/main/resources/assets/clientify/icon.png 256
 * java tools/LogoGen.java mark src/client/resources/assets/clientify/textures/ui/logo.png 128
 * </pre>
 *
 * <p>The mark's 128 is not arbitrary. The editor draws it in a 32px box, and a GUI scale of 4 turns
 * that into 128 physical pixels — so the cell maps 1:1 there, which is the scale it was reported
 * looking soft at. It was baked at 48 before, which the GPU had to magnify 2.67x. Going further
 * than 128 would only trade that for minification: the panel draws the same mark at 20px, already
 * 3.2x down from this sheet.
 *
 * <p>The mark is two cells side by side — cube, then letter — both in white so the drawing code
 * can tint them separately (the letter follows the user's accent colour). Drawing them as one
 * flat image instead would freeze the accent into the texture.
 *
 * <p>Everything is laid out on a 100x100 grid and scaled to whatever size is asked for, so the
 * proportions hold at 16px in a mod list and at 512px on a project page.
 *
 * <p>Two things about it are less arbitrary than they look:
 *
 * <ul>
 *   <li>The cube is a <b>regular hexagon</b> with three spokes to alternate corners — the isometric
 *       projection of a cube seen corner-on. Drawing it as a hand-placed box made the three inner
 *       edges slightly unequal, and unequal stubs pointing at a letter are the sort of thing you
 *       cannot un-see once noticed.
 *   <li>The C's opening spans exactly 60 degrees, which puts its terminals on +30 and -30 — the
 *       angle of the upper right spoke. Every spoke therefore ends the same distance from ink. With
 *       a wider opening the upper right one aims into the gap, where the nearest ink is the far
 *       terminal, and that spoke reads as further away than the other two however much the letter
 *       is thinned.
 * </ul>
 *
 * <p>The spokes start at the letter's disc rather than at the hexagon's centre, so no line crosses
 * the C or sits inside its counter.
 */
public final class LogoGen {
	private static final Color ORANGE = new Color(0xF3, 0x5D, 0x12);
	private static final Color INK = new Color(0x14, 0x14, 0x18);
	private static final Color INK_SOFT = new Color(0x22, 0x22, 0x28);
	private static final Color WHITE = new Color(0xF6, 0xF6, 0xF8);

	private static final double CX = 50;
	private static final double CY = 50;
	/** Hexagon radius: centre to corner. */
	private static final double RADIUS = 30;
	private static final double LETTER_R = 13;
	private static final float CUBE_WEIGHT = 4.5f;
	private static final float C_WEIGHT = 6f;
	/** Clearance between the letter's stroke and the spoke tips. */
	private static final double GAP = 5.2;
	/** Degrees of arc; the remaining 60 is the opening, centred on the right. */
	private static final double EXTENT = 300;

	private LogoGen() {
	}

	public static void main(String[] args) throws Exception {
		String mode = args[0];
		File out = new File(args[1]);
		int size = args.length > 2 ? Integer.parseInt(args[2]) : 256;
		BufferedImage img;
		if (mode.equals("mark")) {
			img = new BufferedImage(size * 2, size, BufferedImage.TYPE_INT_ARGB);
			paint(img, 0, size, g -> {
				zoomToInk(g);
				cube(g, WHITE);
			});
			paint(img, size, size, g -> {
				zoomToInk(g);
				letter(g, WHITE);
			});
		} else {
			img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
			paint(img, 0, size, LogoGen::draw);
		}
		ImageIO.write(img, "PNG", out);
		System.out.println("wrote " + out + " (" + mode + ") at " + size + "px");
	}

	private interface Part {
		void draw(Graphics2D g);
	}

	/** Renders {@code part} into the {@code size}-wide cell starting at {@code cellX}. */
	private static void paint(BufferedImage img, int cellX, int size, Part part) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.translate(cellX, 0);
		g.scale(size / 100.0, size / 100.0);
		part.draw(g);
		g.dispose();
	}

	/**
	 * Blows the mark up so its ink fills the cell. The icon's layout leaves room for the plate, and
	 * without the plate that margin is just a smaller logo — a 20px draw would come out 13px of ink.
	 */
	private static void zoomToInk(Graphics2D g) {
		double k = 100.0 / (2 * (RADIUS + CUBE_WEIGHT / 2));
		g.translate(CX, CY);
		g.scale(k, k);
		g.translate(-CX, -CY);
	}

	private static void draw(Graphics2D g) {
		RoundRectangle2D plate = new RoundRectangle2D.Double(4, 4, 92, 92, 30, 30);
		g.setPaint(new GradientPaint(0, 4, INK_SOFT, 0, 96, INK));
		g.fill(plate);
		g.setColor(new Color(0xFF, 0xFF, 0xFF, 26));
		g.setStroke(new BasicStroke(1.6f));
		g.draw(plate);
		cube(g, WHITE);
		letter(g, ORANGE);
	}

	/** The hexagon and its three spokes. */
	private static void cube(Graphics2D g, Color color) {
		g.setColor(color);
		g.setStroke(new BasicStroke(CUBE_WEIGHT, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		Path2D.Double hex = new Path2D.Double();
		for (int i = 0; i < 6; i++) {
			double a = Math.toRadians(30 + i * 60);
			double px = CX + RADIUS * Math.cos(a);
			double py = CY - RADIUS * Math.sin(a);
			if (i == 0) {
				hex.moveTo(px, py);
			} else {
				hex.lineTo(px, py);
			}
		}
		hex.closePath();
		g.draw(hex);

		// The three inner edges, as stubs from the letter's disc out to the corners they belong to.
		double disc = LETTER_R + C_WEIGHT / 2 + GAP;
		for (double deg : new double[] {30, 150, 270}) {
			double a = Math.toRadians(deg);
			double ux = Math.cos(a);
			double uy = -Math.sin(a);
			g.draw(new Line2D.Double(CX + ux * disc, CY + uy * disc,
					CX + ux * RADIUS, CY + uy * RADIUS));
		}
	}

	/** The C itself. */
	private static void letter(Graphics2D g, Color color) {
		g.setColor(color);
		g.setStroke(new BasicStroke(C_WEIGHT, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		double start = (360 - EXTENT) / 2;
		g.draw(new Arc2D.Double(CX - LETTER_R, CY - LETTER_R, LETTER_R * 2, LETTER_R * 2, start,
				EXTENT, Arc2D.OPEN));
	}
}
