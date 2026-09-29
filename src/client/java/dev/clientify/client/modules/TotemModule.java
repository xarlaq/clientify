package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.HudEditorScreen;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.ChromeMask;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import dev.clientify.client.util.Draw;
import dev.clientify.client.util.TotemCounts;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Everything to do with totems: the animation one plays when it saves you, and a count of how many
 * everybody has got through.
 *
 * <p>The animation itself is never drawn from here — it is vanilla's, caught as it happens in
 * {@code ScreenEffectRendererMixin}, which asks this module where to put it and how big. What this
 * draws is the editor box that says where, and the counter's own list.
 *
 * <p>The counter shows in three places, and two of them are outside this module's HUD element: the
 * tab list and nametags are decorated where the game decides what those say, so a future
 * streamproof mode cannot redirect them along with everything else. See DECISIONS.md.
 */
public class TotemModule extends HudModule {
	/** What the counter's list is ordered by. */
	public enum Sort {
		COUNT("Count"), NAME("Name"), RECENT("Recent");

		private final String label;

		Sort(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** Where a nametag count sits relative to the name. */
	public enum NametagPos {
		BEFORE("Before"), AFTER("After"), ABOVE("Above"), BELOW("Below");

		private final String label;

		NametagPos(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		/** Above and below are their own line, so they are submitted rather than written in. */
		public boolean ownLine() {
			return this == ABOVE || this == BELOW;
		}
	}

	public static class Settings extends ModuleSettings {
		public boolean hideAnimation = false;
		public boolean lockPosition = true;
		public boolean lockRotation = false;
		/** Seconds the whole thing runs. Vanilla's is two. */
		public float duration = 2f;
		public boolean showInEditor = true;

		// ---- totem counter ----
		public boolean counter = false;
		public boolean counterHud = true;
		public boolean counterTab = true;
		public boolean counterTabFlip = false;
		public boolean counterColor = true;
		public boolean counterNametag = false;
		public boolean counterNametagPrefix = false;
		public NametagPos counterNametagPos = NametagPos.AFTER;
		public boolean counterNameVanilla = false;
		public ColorSpec counterCountColor = new ColorSpec("#FFFFFF");
		/** The counter list has its own place on screen; the module's own box places the animation. */
		public PartPos counterPos = new PartPos();

		@Override
		public void applyPositionFrom(ModuleSettings o) {
			super.applyPositionFrom(o);
			if (o instanceof Settings d) {
				counterPos.copyPositionFrom(d.counterPos);
			}
		}
		public float counterScale = 1f;
		public Sort counterSort = Sort.COUNT;
		public boolean counterIcon = true;
		public boolean counterPrefix = true;
		public boolean counterShadow = true;
		public boolean counterFlip = false;
		public boolean counterBackground = true;
		public ColorSpec counterBgColor = new ColorSpec("#8C101014");
		public ColorSpec counterTextColor = new ColorSpec("#FFFFFF");

		public Settings() {
			enabled = false;
			anchor = Anchor.CENTER;
		}
	}

	private static TotemModule instance;

	/** Scratch settings view so the counter's text draws with its own scale and shadow. */
	private final ModuleSettings counterView = new ModuleSettings();

	/** The counter's list, and the frame it was built for. */
	private int counterToken = -1;
	private List<Line> counterCache;

	/** Compared against, never written to — the placement check only reads three of its fields. */
	private static final Settings DEFAULTS = new Settings();

	/** Which nested card is open in the settings, if any. */
	private String expanded;

	private final net.minecraft.client.KeyMapping resetKey;

	/**
	 * Half the tangent of the field of view the animation is drawn through, which is not the field
	 * of view setting: the game asks for that one with the setting switched off, leaving it at a
	 * flat seventy in ordinary play. It is asked for rather than assumed so that the odd cases
	 * where it is not seventy — a panorama, dying, a mod that moves it — still land on the spot.
	 */
	private static float halfFovTan(Minecraft mc) {
		float fov = 70f;
		if (mc.gameRenderer instanceof dev.clientify.mixin.client.GameRendererFovAccessor access) {
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
			fov = access.clientify$getFov(mc.gameRenderer.getMainCamera(), partial, false);
		}
		return (float) Math.tan(Math.toRadians(fov) / 2d);
	}

	/** Kept just inside the edge so a box in the very corner still shows something. */
	private static float clamp(float across) {
		return Math.max(-0.95f, Math.min(0.95f, across));
	}

	public TotemModule() {
		super("totem", "Totem Tweaks");
		instance = this;
		resetKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyBinding(
				new net.minecraft.client.KeyMapping("key.clientify.totem_reset",
						org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN,
						dev.clientify.client.ClientifyClient.KEY_CATEGORY));
	}

	@Override
	public String description() {
		return "Changes the totem pop animation and counts pops.";
	}

	@Override
	public void tick(Minecraft mc) {
		TotemCounts.followConnection(mc);
		while (resetKey.consumeClick()) {
			TotemCounts.reset();
		}
	}

	@Override
	public String category() {
		return "MECHANIC";
	}

	@Override
	public boolean hasAppearance() {
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

	private static Settings active() {
		return instance != null && instance.isEnabled() && instance.settings() instanceof Settings s ? s : null;
	}

	// ---- what the mixin asks ----

	public static boolean hidden() {
		Settings s = active();
		return s != null && s.hideAnimation;
	}

	public static boolean positionLocked() {
		Settings s = active();
		return s != null && s.lockPosition;
	}

	public static boolean rotationLocked() {
		Settings s = active();
		return s != null && s.lockRotation;
	}

	/** The module's own scale doubles as the totem's size, so the editor can scroll it. */
	public static float scale() {
		Settings s = active();
		return s == null ? 1f : s.scale;
	}

	/** How much longer than vanilla's two seconds the animation runs. */
	public static float durationFactor() {
		Settings s = active();
		return s == null ? 1f : Math.max(0.1f, s.duration / 2f);
	}

	/**
	 * Where the totem should appear, in the animation's own units — the same ones vanilla throws it
	 * out in, which are nothing like pixels.
	 *
	 * <p>Null when the module is off or has not been moved, which leaves vanilla's placement alone.
	 *
	 * @param distance how far in front of the camera the totem is this frame. The animation flies
	 *     it in from ten units out to one, and this space is drawn in perspective, so the same
	 *     offset covers ten times as much screen at the near end as at the far one. Feeding the
	 *     distance in is what keeps the totem on the spot you picked for the whole flight instead
	 *     of drifting out from the middle.
	 */
	public static float[] offset(Minecraft mc, float distance) {
		Settings s = active();
		if (s == null) {
			return null;
		}
		if (s.anchor == DEFAULTS.anchor && s.offsetX == DEFAULTS.offsetX
				&& s.offsetY == DEFAULTS.offsetY) {
			// Never moved, so vanillas own placement stands. A module box starts at a small inset
			// rather than at nothing, and feeding that inset in as a displacement was enough to
			// carry the totem out of view the moment the module was switched on.
			return null;
		}
		float guiW = (float) (mc.getWindow().getWidth() / mc.getWindow().getGuiScale());
		float guiH = (float) (mc.getWindow().getHeight() / mc.getWindow().getGuiScale());
		Rect r = instance.bounds(mc, guiW, guiH);
		// Where the box sits, as a fraction of the way from the middle of the screen to its edge.
		float acrossX = clamp(((r.x() + r.w() / 2f) / guiW - 0.5f) * 2f);
		float acrossY = clamp(((r.y() + r.h() / 2f) / guiH - 0.5f) * 2f);
		// Half the screen, in this space, at that distance -- undoing the perspective the totem is
		// drawn through. Landing on a spot is then just that fraction of a half screen. Up is
		// positive here, the opposite of the editor's downward y.
		float halfHeight = distance * halfFovTan(mc);
		float halfWidth = halfHeight * mc.getWindow().getWidth() / mc.getWindow().getHeight();
		return new float[] {acrossX * halfWidth, -acrossY * halfHeight};
	}

	// ---- the totem counter ----
	//
	// Pops are counted from the event the server sends everyone, in ClientPacketListenerMixin. What
	// is here is the three places a count can be shown -- a list of its own, the tab list and
	// nametags -- and the shape of the number in all of them.

	/** True while pops should be recorded. Read from the packet handler, so it stays cheap. */
	public static boolean counting() {
		Settings s = active();
		return s != null && s.counter;
	}

	/** That player's count, or -1 when they have nothing to show. */
	private static int countFor(java.util.UUID id) {
		int n = TotemCounts.of(id);
		return n > 0 ? n : -1;
	}

	/**
	 * A minus rather than a times: these are totems gone, not totems held, and "-3" says that at a
	 * glance where "x3" reads like a stack in somebody's inventory.
	 */
	private static String countText(Settings s, int n) {
		return s.counterPrefix ? "-" + n : Integer.toString(n);
	}

	/**
	 * Green climbing to red as the count does. Someone on their sixth totem is the thing you want to
	 * spot without reading, which a colour does and a number does not.
	 */
	private static int countRgb(Settings s, int n) {
		if (!s.counterColor) {
			return s.counterCountColor.argbAt(0, 1) & 0xFFFFFF;
		}
		float t = Math.min(1f, Math.max(0f, (n - 1) / 7f));
		int r = Math.round(85 + t * 170);
		int g = Math.round(255 - t * 170);
		return (r << 16) | (g << 8) | 0x55;
	}

	/** The tab list's answer, or null to leave the row alone. */
	public static Component decorateTabName(PlayerInfo info, Component original) {
		Settings s = active();
		if (s == null || !s.counter || !s.counterTab || original == null || info == null) {
			return null;
		}
		int n = countFor(info.getProfile().id());
		if (n < 0) {
			return null;
		}
		Component count = Component.literal(countText(s, n)).withColor(countRgb(s, n));
		return s.counterTabFlip
				? Component.empty().append(count).append(" ").append(original)
				: Component.empty().append(original).append(" ").append(count);
	}

	/** A player's nametag, or null to leave it alone. */
	public static Component decorateNameTag(Player player, Component original) {
		Settings s = active();
		if (s == null || !s.counter || !s.counterNametag || original == null) {
			return null;
		}
		int n = countFor(player.getUUID());
		if (n < 0) {
			return null;
		}
		if (s.counterNametagPos.ownLine()) {
			// A nametag is one line of text however it is written, so above and below cannot be
			// written into it. The count rides along in the style instead -- an insertion is only
			// ever read when you shift-click chat, so nothing else looks at it -- and the tag
			// renderer submits it as a line of its own.
			return original.copy().withStyle(style -> style.withInsertion(MARK + n));
		}
		Component count = countComponent(s, n);
		return s.counterNametagPos == NametagPos.BEFORE
				? Component.empty().append(count).append(" ").append(original)
				: Component.empty().append(original).append(" ").append(count);
	}

	private static final String MARK = "clientify$totem:";

	private static Component countComponent(Settings s, int n) {
		String text = s.counterNametagPrefix ? "Totems: " + countText(s, n) : countText(s, n);
		return Component.literal(text).withColor(countRgb(s, n));
	}

	/** The count as a line of its own, for a tag that asked for one. Null when it did not. */
	public static Component nameTagLine(Component tag) {
		Settings s = active();
		if (s == null || !s.counter || !s.counterNametag || tag == null) {
			return null;
		}
		String insertion = tag.getStyle().getInsertion();
		if (insertion == null || !insertion.startsWith(MARK)) {
			return null;
		}
		try {
			return countComponent(s, Integer.parseInt(insertion.substring(MARK.length())));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** How far off the name that line sits, in the rows the tag renderer counts in. */
	public static int nameTagLineOffset() {
		Settings s = active();
		return s != null && s.counterNametagPos == NametagPos.ABOVE ? -10 : 10;
	}

	// ---- the counter's own list ----

	private static final int COUNTER_ICON = 10;
	private static final int COUNTER_GAP = 3;
	private static final int COUNTER_PAD = 4;

	/** One line of the list: who, and how many. */
	private record Line(String name, int ownColor, int count) {
	}

	private ModuleSettings counterView(Settings s) {
		counterView.scale = s.counterScale;
		counterView.textShadow = s.counterShadow;
		counterView.font = s.font;
		return counterView;
	}

	/**
	 * Built once a frame, not once a caller.
	 *
	 * <p>The list was being assembled twice over — the editor asks for the box, then the draw asks
	 * again — and each pass copied the whole count map and walked the tab list for every name. Worse
	 * than the cost: the box and the thing inside it were sized from two separate builds, so a pop
	 * landing between them would have left the outline round the wrong shape for a frame.
	 */
	private List<Line> counterLines(Minecraft mc, Settings s) {
		int token = dev.clientify.client.hud.HudFrame.token();
		if (token == counterToken && counterCache != null) {
			return counterCache;
		}
		List<Line> out = new java.util.ArrayList<>();
		if (mc.getConnection() != null) {
			for (java.util.Map.Entry<java.util.UUID, Integer> e : TotemCounts.entries()) {
				if (countFor(e.getKey()) < 0) {
					continue;
				}
				PlayerInfo info = mc.getConnection().getPlayerInfo(e.getKey());
				if (info != null) {
					Component display = info.getTabListDisplayName();
					int own = 0xFFFFFFFF;
					if (display != null && display.getStyle().getColor() != null) {
						own = 0xFF000000 | display.getStyle().getColor().getValue();
					}
					out.add(new Line(info.getProfile().name(), own, e.getValue()));
				}
			}
		}
		switch (s.counterSort) {
			case COUNT -> out.sort((a, b) -> Integer.compare(b.count(), a.count()));
			case NAME -> out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
			// Insertion order is oldest first, so the newest pop belongs at the top.
			case RECENT -> java.util.Collections.reverse(out);
			default -> {
			}
		}
		// Something to aim at while placing it: an empty list has no size and cannot be dragged.
		if (out.isEmpty() && mc.screen instanceof HudEditorScreen) {
			out.add(new Line("Player", 0xFFFFFFFF, 1));
		}
		counterToken = token;
		counterCache = out;
		return out;
	}

	private float lineWidth(Minecraft mc, Settings s, ModuleSettings v, Line line) {
		float w = s.counterIcon ? (COUNTER_ICON + COUNTER_GAP) * s.counterScale : 0;
		w += HudText.width(mc, v, line.name(), s.counterScale);
		w += HudText.width(mc, v, " ", s.counterScale);
		w += HudText.width(mc, v, countText(s, line.count()), s.counterScale);
		return w - HudText.trailing(v, s.counterScale);
	}

	private float[] counterSize(Minecraft mc, Settings s, List<Line> lines) {
		ModuleSettings v = counterView(s);
		float w = 0;
		for (Line line : lines) {
			w = Math.max(w, lineWidth(mc, s, v, line));
		}
		float lineH = HudText.lineHeight(mc, v, s.counterScale);
		float h = lines.isEmpty() ? 0 : lines.size() * lineH - HudText.trailing(v, s.counterScale);
		float pad = COUNTER_PAD * 2 * s.counterScale;
		return new float[] {w + pad, h + pad};
	}

	private boolean counterShowing(Minecraft mc, Settings s) {
		return s != null && s.counter && s.counterHud
				&& (mc.screen instanceof HudEditorScreen || !TotemCounts.isEmpty());
	}

	private void drawCounter(GuiGraphicsExtractor g, Minecraft mc, Settings s) {
		List<Line> lines = counterLines(mc, s);
		if (lines.isEmpty()) {
			return;
		}
		ModuleSettings v = counterView(s);
		float[] size = counterSize(mc, s, lines);
		Rect r = partBounds(s.counterPos, size[0], size[1], g.guiWidth(), g.guiHeight());
		if (s.counterBackground) {
			int argb = s.counterBgColor.argbAt(0, 1);
			int bx = Math.round(r.x());
			int by = Math.round(r.y());
			int bw = Math.round(r.w());
			int bh = Math.round(r.h());
			if (ChromeMask.enabled()) {
				ChromeMask.draw(g, r.x(), r.y(), r.w(), r.h(), 0,
						() -> Draw.smoothRounded(g, bx, by, bw, bh, 0, argb));
			} else {
				Draw.smoothRounded(g, bx, by, bw, bh, 0, argb);
			}
		}

		float scale = s.counterScale;
		float pad = COUNTER_PAD * scale;
		float lineH = HudText.lineHeight(mc, v, scale);
		float y = r.y() + pad;
		for (Line line : lines) {
			String count = countText(s, line.count());
			float countW = HudText.width(mc, v, count, scale);
			float nameW = HudText.width(mc, v, line.name(), scale);
			float x = r.x() + pad;
			if (s.counterIcon) {
				g.pose().pushMatrix();
				g.pose().translate(x, y);
				g.pose().scale(COUNTER_ICON / 16f * scale, COUNTER_ICON / 16f * scale);
				g.item(TOTEM, 0, 0);
				g.pose().popMatrix();
				x += (COUNTER_ICON + COUNTER_GAP) * scale;
			}
			// Flipped puts the number first, which reads better when the counts are what you are
			// scanning and the names are only there to say whose they are.
			float nameX = s.counterFlip ? x + countW + HudText.width(mc, v, " ", scale) : x;
			float countX = s.counterFlip ? x : x + nameW + HudText.width(mc, v, " ", scale);
			if (s.counterNameVanilla) {
				// Whatever the tab list draws them in -- a team colour, usually.
				HudText.draw(g, mc, v, line.name(), nameX, y, line.ownColor());
			} else {
				HudText.draw(g, mc, v, line.name(), nameX, y, s.counterTextColor, 0,
						line.name().length(), scale);
			}
			// The count's colour is worked out per line rather than picked, so it goes in flat
			// rather than through a spec.
			HudText.draw(g, mc, v, count, countX, y, 0xFF000000 | countRgb(s, line.count()));
			y += lineH;
		}
	}

	// ---- the editor box ----

	/** Below this the box is more of a dot than a handle, and there is no scrolling back up. */
	/** Shared: renderItem only reads it, and one per line per frame was one too many. */
	private static final ItemStack TOTEM = new ItemStack(Items.TOTEM_OF_UNDYING);

	private static final float SMALLEST_BOX = 24f;

	/**
	 * About what the totem covers at its usual size, but never so small it cannot be grabbed —
	 * {@code bounds} multiplies this by the scale, so the floor has to be divided back out. The box
	 * stops shrinking well before the totem does, which is the price of still being able to scroll
	 * it back up.
	 */
	private static float boxSide() {
		Settings s = active();
		float scale = s == null ? 1f : Math.max(0.01f, s.scale);
		return Math.max(48f, SMALLEST_BOX / scale);
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		return boxSide();
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return boxSide();
	}

	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		Settings s = active();
		if (s == null) {
			return List.of();
		}
		List<Draggable> out = new java.util.ArrayList<>(2);
		// Nothing to place while the animation is switched off. The box used to stay, so hiding the
		// animation and then moving the box looked for all the world like moving it had broken it.
		if (s.showInEditor && !s.hideAnimation) {
			out.addAll(super.draggables(mc, screenW, screenH));
		}
		// The counter's list is placed on its own: it is a corner of the screen you read, not a
		// thing thrown at your face, and the two have no business sharing a position.
		if (counterShowing(mc, s)) {
			float[] size = counterSize(mc, s, counterLines(mc, s));
			out.add(new Draggable() {
				@Override
				public Rect bounds() {
					return partBounds(s.counterPos, size[0], size[1], screenW, screenH);
				}

				@Override
				public void moveTo(float x, float y) {
					partMoveTo(s.counterPos, x, y, size[0], size[1], screenW, screenH);
				}

				@Override
				public void scaleBy(float delta) {
					s.counterScale = clampScale(s.counterScale + delta);
				}
			});
		}
		return out;
	}

	// A longer reach at both ends than a text box gets. The totem is drawn out in front of the
	// camera rather than on the HUD: three times over is not much of a totem, and half is not much
	// of a reduction on something that flies at your face.

	@Override
	public float minScale() {
		return 0.1f;
	}

	@Override
	public float maxScale() {
		return 6f;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = active();
		if (s == null) {
			return;
		}
		if (counterShowing(mc, s)) {
			drawCounter(graphics, mc, s);
		}
		// The placement box is only ever visible while you are placing it: the real animation is
		// vanilla's, and drawing a second totem over the game the rest of the time would leave a
		// permanent totem on screen.
		if (!s.showInEditor || s.hideAnimation || !(mc.screen instanceof HudEditorScreen)) {
			return;
		}
		Rect r = bounds(mc, graphics.guiWidth(), graphics.guiHeight());
		graphics.pose().pushMatrix();
		graphics.pose().translate(r.x() + r.w() / 2f, r.y() + r.h() / 2f);
		graphics.pose().scale(r.w() / 16f, r.h() / 16f);
		graphics.item(TOTEM, -8, -8);
		graphics.pose().popMatrix();
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();

		// On its own row, and first, because it switches the rest of them off. Sharing a row with an
		// ordinary toggle made it easy to catch by accident, and then nothing here appeared to work.
		rows.add(screen.withTooltip(
				screen.toggle("Hide Animation", () -> s.hideAnimation, v -> s.hideAnimation = v,
						() -> s.hideAnimation = d.hideAnimation),
				"Skips the totem animation entirely. Nothing else\n"
						+ "about the animation does anything while it is on;\n"
						+ "the counter below carries on regardless."));
		rows.add(screen.withTooltip(
				screen.dualToggle("Lock Position", () -> s.lockPosition, v -> s.lockPosition = v,
						"Lock Rotation", () -> s.lockRotation, v -> s.lockRotation = v),
				"Drops vanilla's throw, so the totem stays where you\n"
						+ "put it instead of swinging out to a random side.",
				"Drops the spin and the two shivers riding on it, so\n"
						+ "the totem faces you the whole way in."));
		rows.add(screen.withTooltip(
				screen.sliderRow("Duration", 0.25f, 5f, 0.05f, () -> s.duration,
						v -> s.duration = v, "%.2fs", () -> s.duration = d.duration),
				"How long the whole animation runs. Vanilla is two\n"
						+ "seconds; shorter gets you back to the fight."));
		rows.add(screen.withTooltip(
				screen.toggle("Show In Editor", () -> s.showInEditor, v -> s.showInEditor = v,
						() -> s.showInEditor = d.showInEditor),
				"Puts a totem in the HUD editor to drag and scroll.\n"
						+ "The animation lands where the box is; it flies in\n"
						+ "toward you, so its size still changes as it plays."));

		appendCounterRows(screen, rows, s, d);
	}

	/** The Totem Counter half of the page: who has popped how many, and where that is shown. */
	private void appendCounterRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Settings s, Settings d) {
		rows.add(screen.section("Totem Counter"));
		rows.add(screen.withTooltip(
				screen.toggle("Totem Counter", () -> s.counter, v -> s.counter = v,
						() -> s.counter = d.counter),
				"Keeps track of how many totems each player has\n"
						+ "popped. Nothing is counted while this is off, so\n"
						+ "turning it on mid-fight starts from the wrong number."));
		if (!s.counter) {
			return;
		}
		rows.add(screen.withTooltip(
				screen.dualToggleGear(
						"HUD", () -> s.counterHud, v -> s.counterHud = v,
						() -> expand("counterHud"), () -> "counterHud".equals(expanded),
						"Show In Tab List", () -> s.counterTab, v -> s.counterTab = v, null, () -> false),
				"A list of players and their counts, placed in the HUD\n"
						+ "editor like any other module.",
				"Writes the same counts beside the names in the tab\n"
						+ "list, where you are already looking for people."));
		if ("counterHud".equals(expanded)) {
			screen.groupCard(rows, group -> {
				group.add(screen.sliderRow("Scale", 0.3f, 3f, 0.05f, () -> s.counterScale,
						v -> s.counterScale = v, "%.2f", () -> s.counterScale = d.counterScale));
				group.add(screen.section("General"));
				group.add(screen.withTooltip(
						screen.cycleRow("Type", () -> s.counterSort.label(),
								() -> s.counterSort = cycle(s.counterSort, -1),
								() -> s.counterSort = cycle(s.counterSort, 1),
								() -> s.counterSort = d.counterSort),
						"What the list is ordered by: the biggest count\n"
								+ "first, by name, or whoever popped most recently."));
				group.add(screen.withTooltip(
						screen.dualToggle("Icon Mode", () -> s.counterIcon, v -> s.counterIcon = v,
								"Show Prefix", () -> s.counterPrefix, v -> s.counterPrefix = v),
						"Puts a totem at the start of each line, so the list\n"
								+ "says what it is without a heading.",
						"Writes the count as -3 rather than 3: these are\n"
								+ "totems gone, not totems held."));
				group.add(screen.dualToggle("Text Shadow", () -> s.counterShadow, v -> s.counterShadow = v,
						"Flip", () -> s.counterFlip, v -> s.counterFlip = v));
				group.add(screen.toggle("Show Background", () -> s.counterBackground,
						v -> s.counterBackground = v, () -> s.counterBackground = d.counterBackground));
				if (s.counterBackground) {
					screen.addColorRows(group, "Background Color", () -> s.counterBgColor,
							() -> s.counterBgColor.copyFrom(d.counterBgColor));
				}
			});
		}
		rows.add(screen.withTooltip(
				screen.dualToggle("Flip Tab List", () -> s.counterTabFlip, v -> s.counterTabFlip = v,
						"Their Name Color", () -> s.counterNameVanilla, v -> s.counterNameVanilla = v),
				"Puts the number before the name in the tab list\n"
						+ "instead of after it.",
				"Draws names in the list in whatever colour they\n"
						+ "already have, which on a server is their team's."));
		if (!s.counterNameVanilla) {
			screen.addColorRows(rows, "Name Color", () -> s.counterTextColor,
					() -> s.counterTextColor.copyFrom(d.counterTextColor));
		}
		rows.add(screen.withTooltip(
				screen.dualToggleGear(
						"Dynamic Count Color", () -> s.counterColor, v -> s.counterColor = v,
						() -> expand("counterCount"), () -> "counterCount".equals(expanded),
						"Show Nametag", () -> s.counterNametag, v -> s.counterNametag = v,
						() -> expand("counterNametag"), () -> "counterNametag".equals(expanded)),
				"Runs the number green to red as it climbs, so a player\n"
						+ "on their sixth reads without being read. Switch it\n"
						+ "off to pick the colour yourself, on the gear.",
				"Puts the count over their head, on the gear's terms:\n"
						+ "before or after the name, or on its own line."));
		if ("counterCount".equals(expanded)) {
			screen.groupCard(rows, group -> screen.addColorRows(group, "Count Color",
					() -> s.counterCountColor, () -> s.counterCountColor.copyFrom(d.counterCountColor)));
		}
		if ("counterNametag".equals(expanded)) {
			screen.groupCard(rows, group -> {
				group.add(screen.withTooltip(
						screen.cycleRow("Position", () -> s.counterNametagPos.label(),
								() -> s.counterNametagPos = cycle(s.counterNametagPos, -1),
								() -> s.counterNametagPos = cycle(s.counterNametagPos, 1),
								() -> s.counterNametagPos = d.counterNametagPos),
						"Where the count sits: written into the name before\n"
								+ "or after it, or on a line of its own above or below."));
				group.add(screen.withTooltip(
						screen.toggle("Show Prefix", () -> s.counterNametagPrefix,
								v -> s.counterNametagPrefix = v,
								() -> s.counterNametagPrefix = d.counterNametagPrefix),
						"Writes the word out, as Totems: -3."));
			});
		}
		rows.add(screen.keybindRow("Reset Counts Keybind", resetKey, () -> {
			resetKey.setKey(resetKey.getDefaultKey());
			net.minecraft.client.KeyMapping.resetMapping();
		}));
		rows.add(screen.button("Reset Counts", TotemCounts::reset));
	}

	private void expand(String key) {
		expanded = key.equals(expanded) ? null : key;
	}

}
