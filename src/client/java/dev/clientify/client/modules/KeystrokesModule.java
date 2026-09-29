package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.hud.HudText;
import dev.clientify.client.util.Draw;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The keys you are pressing, drawn as a block of chips.
 *
 * <p>Every label is the key actually bound rather than a hard-coded letter, so a player on AZERTY
 * sees ZQSD and a player who has moved sneak sees where they moved it to. Vanilla already knows
 * this — {@code KeyMapping.getTranslatedKeyMessage} is what the controls screen prints — so there
 * is no reason to guess at it.
 *
 * <h2>Grouped and individual</h2>
 *
 * <p>Grouped lays the keys out as one block, drawn from one set of settings. Individual gives every
 * key its own place, its own size and its own colours, the way the coordinates and armour modules
 * separate their parts — seeded from where the block had put it, so switching moves nothing until
 * you do. "Copy Options" pushes the grouped settings back onto every key, which is the way back out
 * of a set of individual keys you no longer like.
 *
 * <p>Both modes share one drawing path: grouped fills a scratch {@link Look} from its own settings
 * and hands it over, so a key drawn on its own and a key drawn in the block cannot drift apart.
 *
 * <h2>Counting clicks</h2>
 *
 * <p>Clicks per second are taken from the press edge of the attack and use bindings, sampled once a
 * frame rather than once a tick. A tick is fifty milliseconds, so a tick-sampled counter cannot see
 * a press and its release inside one — it would cap out somewhere near ten and read low for anyone
 * who clicks fast, which is precisely the person watching the number.
 *
 * <p>Sampling rather than consuming, because {@code consumeClick} is how the game asks "has this
 * been pressed since I last looked" and taking that answer here would take it from whatever asks
 * next — the click would count and then not swing.
 *
 * <p>All of it is drawn inside the module's own HUD element, so it stays behind the single choke
 * point a streamproof mode would redirect.
 */
public class KeystrokesModule extends HudModule {
	/** How long a click stays counted, in milliseconds — one second, because it is per second. */
	private static final long WINDOW = 1000L;

	/** How long a press takes to finish animating at speed 1, in seconds. */
	private static final float ANIM_TIME = 0.12f;

	/** How long one ring takes to cross the key and fade out, in seconds, at speed 1. */
	private static final float RIPPLE_TIME = 0.45f;

	/** The small print under a mouse button, as a fraction of the label above it. */
	private static final float CPS_SCALE = 0.62f;

	/** The key size the text was drawn for, so text grows with a key rather than sitting in it. */
	private static final float BASE_SIZE = 22f;

	public enum Mode {
		GROUPED("Grouped"), INDIVIDUAL("Individual");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public enum Anim {
		NONE("None"), RIPPLE("Ripple"), SINK("Sink"), RIPPLE_SINK("Ripple + Sink");

		private final String label;

		Anim(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}

		public boolean ripples() {
			return this == RIPPLE || this == RIPPLE_SINK;
		}

		public boolean sinks() {
			return this == SINK || this == RIPPLE_SINK;
		}
	}

	/** Everything about how one key is drawn — the whole of what "Copy Options" copies. */
	public static class Look {
		public float size = 22f;
		public int radius = 0;
		public boolean outline = false;
		public float outlineThickness = 1f;
		public Anim anim = Anim.NONE;
		public float animSpeed = 1f;
		public boolean blur = false;
		public ModuleSettings.ColorSpec idleColor = new ModuleSettings.ColorSpec("#8C101014");
		public ModuleSettings.ColorSpec pressedColor = new ModuleSettings.ColorSpec("#80F35D12");
		public ModuleSettings.ColorSpec textColor = new ModuleSettings.ColorSpec("#FFFFFFFF");
		public ModuleSettings.ColorSpec pressedTextColor = new ModuleSettings.ColorSpec("#FFFFFFFF");
		public ModuleSettings.ColorSpec outlineColor = new ModuleSettings.ColorSpec("#59FFFFFF");

		public void copyFrom(Look o) {
			size = o.size;
			radius = o.radius;
			outline = o.outline;
			outlineThickness = o.outlineThickness;
			anim = o.anim;
			animSpeed = o.animSpeed;
			blur = o.blur;
			idleColor.copyFrom(o.idleColor);
			pressedColor.copyFrom(o.pressedColor);
			textColor.copyFrom(o.textColor);
			pressedTextColor.copyFrom(o.pressedTextColor);
			outlineColor.copyFrom(o.outlineColor);
		}
	}

	/** How a key is shaped — square, half a row wide, or a bar across the bottom. */
	public enum Form {
		SQUARE, HALF, BAR
	}

	/** One key on the board: which binding, whether it shows, where it sits, how it looks. */
	public static class Key {
		public String id = "";
		/** The binding's name, as {@code KeyMapping.getName()} gives it — "key.forward" and such. */
		public String binding = "";
		public boolean shown = true;
		public boolean builtin = true;
		/** Added keys watch a key directly, by the name vanilla saves binds under. */
		public String rawKey = "";
		/** Written on the key instead of whatever it would otherwise say. Empty leaves it. */
		public String customText = "";
		/** Added keys carry their own box. Nought means "however tall the key size says". */
		public float width = 0f;
		public float height = 0f;
		/** Whether this key has been given a place yet. Seeding is once, not every frame. */
		public boolean placed = false;
		public Form form = Form.SQUARE;
		public ModuleSettings.PartPos pos = new ModuleSettings.PartPos();
		public Look look = new Look();

		public Key() {
		}

		public Key(String id, String binding, Form form, boolean builtin) {
			this.id = id;
			this.binding = binding;
			this.form = form;
			this.builtin = builtin;
		}
	}

	public static class Settings extends ModuleSettings {
		public Mode mode = Mode.GROUPED;
		public boolean showMouse = true;
		public boolean showSpace = true;
		public boolean showSneak = false;
		public boolean showCps = false;
		public boolean arrows = false;
		/** One key's side, before the module scale. */
		public float keySize = 22f;
		public float gap = 2f;
		public int keyRadius = 0;
		public boolean outline = false;
		public float outlineThickness = 1f;
		/** How thick the space bar's line is drawn. */
		public float spaceThickness = 1f;
		/** How much of the bar the line spans, as a share of its width. */
		public float spaceLength = 0.7f;
		public Anim anim = Anim.NONE;
		public float animSpeed = 1f;
		public boolean blur = false;
		public ColorSpec idleColor = new ColorSpec("#8C101014");
		public ColorSpec pressedColor = new ColorSpec("#80F35D12");
		public ColorSpec idleTextColor = new ColorSpec("#FFFFFFFF");
		public ColorSpec pressedTextColor = new ColorSpec("#FFFFFFFF");
		public ColorSpec outlineColor = new ColorSpec("#59FFFFFF");
		/** Individual mode: every key, its place and its own look. Seeded on first use. */
		public List<Key> keys = new ArrayList<>();

		public Settings() {
			enabled = false;
			background = false;
			anchor = Anchor.MIDDLE_LEFT;
			offsetX = 8;
		}

		/**
		 * Module defaults arrive as a background and a border. This module has neither: a key
		 * colour is its background and an outline is its border, so they land there instead of
		 * in fields nothing draws. Every key takes them too, or the copy would only reach one
		 * of the two modes.
		 *
		 * <p>Corners are the exception. A background radius and a key radius are not the same
		 * measurement -- one rounds a panel, the other rounds a two-centimetre square -- so
		 * rounding is left alone.
		 *
		 * <p>The label takes the template's text colour, but only the resting one. A pressed key is
		 * a state the player set a colour for on purpose, and a palette applied across the HUD has
		 * no business deciding what "pressed" looks like.
		 */
		@Override
		public void applyAppearanceFrom(ModuleSettings o) {
			textShadow = o.textShadow;
			font = o.font;
			idleColor.copyFrom(o.bgColor);
			blur = o.bgBlur;
			outline = o.border;
			outlineThickness = o.borderThickness;
			outlineColor.copyFrom(o.borderColor);
			idleTextColor.copyFrom(o.labelColor);
			for (Key key : keys) {
				key.look.idleColor.copyFrom(o.bgColor);
				key.look.blur = o.bgBlur;
				key.look.outline = o.border;
				key.look.outlineThickness = o.borderThickness;
				key.look.outlineColor.copyFrom(o.borderColor);
				key.look.textColor.copyFrom(o.labelColor);
			}
		}

		/**
		 * Individually-placed keys go back to being unplaced, which hands them to seedPositions to
		 * be laid out afresh from wherever the grouped block now sits.
		 */
		@Override
		public void applyPositionFrom(ModuleSettings o) {
			super.applyPositionFrom(o);
			for (Key key : keys) {
				key.placed = false;
			}
		}
	}

	/** One chip to draw: what it says, where, how big, and whether it is held. */
	private record Chip(String id, float x, float y, float w, float h, String label, String sub,
			boolean down, boolean line, Look look) {
	}

	/**
	 * How far through its press a key is, and every ring still travelling out of it.
	 *
	 * <p>A ring is just the moment it started. They are kept in a queue because each press sends
	 * its own and none of them interrupt the others — hammering a key should send a train of rings
	 * out of it, not restart one ring over and over.
	 */
	private static final class Press {
		float t;
		boolean wasDown;
		final ArrayDeque<Long> rings = new ArrayDeque<>();
	}

	private final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
	private final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
	private boolean leftWasDown;
	private boolean rightWasDown;
	private final Map<String, Press> presses = new HashMap<>();
	private long lastFrame;

	/** Grouped's settings, worn as a Look so both modes draw through the same path. */
	private final Look grouped = new Look();

	/** Key lookup, and what it was built for — see {@link #keys}. */
	private final Map<String, Key> byId = new HashMap<>();
	private Settings keyedFor;
	private int keyCount = -1;

	/** Which key's card is open in the settings, if any. */
	private String expandedKey;

	public KeystrokesModule() {
		super("keystrokes", "Keystrokes");
	}

	@Override
	public String description() {
		return "Shows the movement keys and mouse buttons as you press them.";
	}

	@Override
	public String category() {
		return "HUD";
	}

	@Override
	public Class<? extends ModuleSettings> settingsClass() {
		return Settings.class;
	}

	@Override
	public ModuleSettings createDefaultSettings() {
		return new Settings();
	}

	/** The key colours below are the ones that matter; the general label/value pair is not used. */
	@Override
	public boolean hasColorSection() {
		return false;
	}

	/**
	 * A Custom Text box for every key, in key order.
	 *
	 * <p>The settings screen owns the boxes, so a module hands it the list and asks for one
	 * back by index where it wants it drawn — here, inside that key card rather than in a
	 * section of its own.
	 */
	@Override
	public List<TextField> textFields() {
		Minecraft mc = Minecraft.getInstance();
		if (!(settings() instanceof Settings s)) {
			return List.of();
		}
		List<TextField> out = new ArrayList<>();
		for (Key key : keys(mc, s)) {
			if (!key.builtin) {
				out.add(TextField.inGear("Custom Text", () -> key.customText,
						v -> key.customText = v, ""));
			}
		}
		return out;
	}

	/**
	 * Blur lives on the keys here, not on a module-wide background, so the default check would
	 * never have seen it.
	 */
	@Override
	public boolean wantsBlur() {
		if (!(settings() instanceof Settings s)) {
			return false;
		}
		if (s.mode == Mode.GROUPED) {
			return s.blur;
		}
		for (Key key : s.keys) {
			if (key.shown && key.look.blur) {
				return true;
			}
		}
		return false;
	}

	/** Each key carries its own colour and outline; a background behind the lot does nothing. */
	@Override
	public boolean hasBackgroundSection() {
		return false;
	}

	private Settings s() {
		return (Settings) settings();
	}

	// ---- clicks ----

	private static void prune(ArrayDeque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > WINDOW) {
			clicks.removeFirst();
		}
	}

	private void sampleClicks(Minecraft mc) {
		long now = System.currentTimeMillis();
		boolean left = mc.options.keyAttack.isDown();
		if (left && !leftWasDown) {
			leftClicks.addLast(now);
		}
		leftWasDown = left;
		boolean right = mc.options.keyUse.isDown();
		if (right && !rightWasDown) {
			rightClicks.addLast(now);
		}
		rightWasDown = right;
		prune(leftClicks, now);
		prune(rightClicks, now);
	}

	// ---- bindings ----

	/** The eight the module starts with, in the order the block lays them out. */
	private static List<Key> defaultKeys(Minecraft mc) {
		List<Key> out = new ArrayList<>(8);
		out.add(new Key("up", mc.options.keyUp.getName(), Form.SQUARE, true));
		out.add(new Key("left", mc.options.keyLeft.getName(), Form.SQUARE, true));
		out.add(new Key("down", mc.options.keyDown.getName(), Form.SQUARE, true));
		out.add(new Key("right", mc.options.keyRight.getName(), Form.SQUARE, true));
		out.add(new Key("attack", mc.options.keyAttack.getName(), Form.HALF, true));
		out.add(new Key("use", mc.options.keyUse.getName(), Form.HALF, true));
		out.add(new Key("sneak", mc.options.keyShift.getName(), Form.BAR, true));
		out.add(new Key("jump", mc.options.keyJump.getName(), Form.BAR, true));
		return out;
	}

	private boolean isDown(Minecraft mc, Key key) {
		if (key.builtin) {
			return mapping(mc, key.binding).isDown();
		}
		com.mojang.blaze3d.platform.InputConstants.Key raw = rawOf(key);
		return raw != null && dev.clientify.client.util.HoldableKey.isHeld(raw);
	}

	/** Null when nothing has been picked yet, or when the stored name no longer parses. */
	private static com.mojang.blaze3d.platform.InputConstants.Key rawOf(Key key) {
		if (key.rawKey == null || key.rawKey.isEmpty()) {
			return null;
		}
		try {
			return com.mojang.blaze3d.platform.InputConstants.getKey(key.rawKey);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static KeyMapping mapping(Minecraft mc, String binding) {
		KeyMapping found = KeyMapping.get(binding);
		return found != null ? found : mc.options.keyUp;
	}

	/** What the settings list calls a key: the action's name, not the key it happens to be on. */
	private static String actionName(String binding) {
		return Component.translatable(binding).getString();
	}

	/** What a key is called in the settings list. An added key has no action, only a key. */
	private static String rowName(Key key) {
		return key.builtin ? actionName(key.binding) : SettingsRowsScreen.rawKeyName(key.rawKey);
	}

	private String chipLabel(Settings s, Key key, Minecraft mc) {
		if (key.customText != null && !key.customText.isEmpty()) {
			return key.customText;
		}
		if (!key.builtin) {
			return shortKeyName(key);
		}
		KeyMapping mapping = mapping(mc, key.binding);
		return chipLabelFor(s, key, mapping);
	}

	/**
	 * What an added key says on the board.
	 *
	 * <p>Mouse buttons get the short form the built-in mouse keys already use: their full
	 * names are three words wide and no key is.
	 */
	private static String shortKeyName(Key key) {
		com.mojang.blaze3d.platform.InputConstants.Key raw = rawOf(key);
		if (raw == null) {
			return SettingsRowsScreen.rawKeyName(key.rawKey);
		}
		if (raw.getType() != com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
			return raw.getDisplayName().getString();
		}
		return switch (raw.getValue()) {
			case 0 -> "LMB";
			case 1 -> "RMB";
			case 2 -> "MMB";
			default -> "M" + (raw.getValue() + 1);
		};
	}

	private String chipLabelFor(Settings s, Key key, KeyMapping mapping) {
		if ("attack".equals(key.id)) {
			return "LMB";
		}
		if ("use".equals(key.id)) {
			return "RMB";
		}
		if (s.arrows) {
			String arrow = switch (key.id) {
				case "up" -> "↑";
				case "left" -> "←";
				case "down" -> "↓";
				case "right" -> "→";
				default -> null;
			};
			if (arrow != null) {
				return arrow;
			}
		}
		return mapping.getTranslatedKeyMessage().getString();
	}

	private String chipSub(Settings s, Key key) {
		if (!s.showCps) {
			return null;
		}
		if ("attack".equals(key.id)) {
			return leftClicks.size() + " CPS";
		}
		if ("use".equals(key.id)) {
			return rightClicks.size() + " CPS";
		}
		return null;
	}

	// ---- the grouped block ----

	private Look groupedLook(Settings s) {
		grouped.size = s.keySize;
		grouped.radius = s.keyRadius;
		grouped.outline = s.outline;
		grouped.outlineThickness = s.outlineThickness;
		if (s.anim == null) {
			s.anim = Anim.NONE;
		}
		grouped.anim = s.anim;
		grouped.animSpeed = s.animSpeed;
		grouped.blur = s.blur;
		grouped.idleColor = s.idleColor;
		grouped.pressedColor = s.pressedColor;
		grouped.textColor = s.idleTextColor;
		grouped.pressedTextColor = s.pressedTextColor;
		grouped.outlineColor = s.outlineColor;
		return grouped;
	}

	/** True while a built-in key belongs in the block, by the grouped visibility switches. */
	private static boolean groupedShows(Settings s, String id) {
		return switch (id) {
			case "attack", "use" -> s.showMouse;
			case "sneak" -> s.showSneak;
			case "jump" -> s.showSpace;
			default -> true;
		};
	}

	private List<Chip> groupedChips(Minecraft mc) {
		return blockChips(mc, false);
	}

	/**
	 * The block's layout.
	 *
	 * <p>{@code everyKey} lays out all eight whether or not the block is showing them, which is what
	 * seeding individual mode wants: a key the block was hiding still has a natural slot, and
	 * dropping it at the origin instead because it happened to be switched off is not one.
	 */
	private List<Chip> blockChips(Minecraft mc, boolean everyKey) {
		Settings s = s();
		Look look = groupedLook(s);
		float u = Math.max(8f, s.keySize);
		float g = Math.max(0f, s.gap);
		float full = u * 3 + g * 2;
		float bar = u * 0.6f;
		List<Chip> out = new ArrayList<>(8);
		float y = 0;
		out.add(chip(mc, s, look, "up", u + g, y, u, u));
		y += u + g;
		out.add(chip(mc, s, look, "left", 0, y, u, u));
		out.add(chip(mc, s, look, "down", u + g, y, u, u));
		out.add(chip(mc, s, look, "right", (u + g) * 2, y, u, u));
		y += u + g;
		if (everyKey || s.showMouse) {
			float half = (full - g) / 2f;
			out.add(chip(mc, s, look, "attack", 0, y, half, u));
			out.add(chip(mc, s, look, "use", half + g, y, half, u));
			y += u + g;
		}
		// The wide keys are shorter as well as longer: a full-height bar for a space reads as
		// another block of keys rather than as the one under your thumb.
		if (everyKey || s.showSneak) {
			out.add(chip(mc, s, look, "sneak", 0, y, full, bar));
			y += bar + g;
		}
		if (everyKey || s.showSpace) {
			out.add(chip(mc, s, look, "jump", 0, y, full, bar));
		}
		return out;
	}

	private Chip chip(Minecraft mc, Settings s, Look look, String id, float x, float y, float w, float h) {
		Key key = keyById(mc, s, id);
		return new Chip(id, x, y, w, h, chipLabel(s, key, mc), chipSub(s, key),
				isDown(mc, key), "jump".equals(id), look);
	}

	/** The stored key with that id, seeding the list the first time anything asks. */
	private Key keyById(Minecraft mc, Settings s, String id) {
		keys(mc, s);
		Key found = byId.get(id);
		return found != null ? found : new Key(id, mc.options.keyUp.getName(), Form.SQUARE, true);
	}

	/**
	 * Every key, seeded from the eight built-ins the first time and topped up afterwards.
	 *
	 * <p>Topping up matters: a config written before a key existed would otherwise be missing it
	 * for good, and the block would quietly lose a row.
	 */
	private List<Key> keys(Minecraft mc, Settings s) {
		// Seeding is a once-per-settings job, not a once-per-key-per-frame one. The identity check
		// is what catches a profile switch or a reset handing us a different Settings entirely.
		if (keyedFor != s) {
			seed(mc, s);
			keyedFor = s;
			keyCount = -1;
		}
		// Rebuilt on a size change so Add Key and Remove Key cannot leave a stale lookup behind.
		if (keyCount != s.keys.size()) {
			byId.clear();
			for (Key k : s.keys) {
				byId.put(k.id, k);
			}
			keyCount = s.keys.size();
		}
		return s.keys;
	}

	private void seed(Minecraft mc, Settings s) {
		if (s.keys.isEmpty()) {
			s.keys.addAll(defaultKeys(mc));
			Look from = groupedLook(s);
			for (Key k : s.keys) {
				k.look.copyFrom(from);
			}
			return;
		}
		// Topping up matters: a config written before a key existed would otherwise be missing it
		// for good, and the block would quietly lose a row.
		for (Key candidate : defaultKeys(mc)) {
			boolean have = false;
			for (Key k : s.keys) {
				if (candidate.id.equals(k.id)) {
					have = true;
					break;
				}
			}
			if (!have) {
				candidate.look.copyFrom(groupedLook(s));
				s.keys.add(candidate);
			}
		}
	}

	// ---- animation ----

	/**
	 * Walks every key's press a step toward where it is going, once a frame.
	 *
	 * <p>Timed off the wall clock rather than off ticks: a press is a thing you feel, and at twenty
	 * steps a second the fade would arrive in visible stages.
	 */
	private void advance(List<Chip> chips) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		for (Chip c : chips) {
			Press p = presses.computeIfAbsent(c.id(), k -> new Press());
			Anim anim = animOf(c.look());
			float speed = Math.max(0.1f, c.look().animSpeed);

			// Every press sends a ring, and old ones keep going. A ring that has run its course is
			// dropped from the front, which is why the queue never grows.
			if (c.down() && !p.wasDown && anim.ripples()) {
				p.rings.addLast(now);
			}
			p.wasDown = c.down();
			long life = (long) (RIPPLE_TIME * 1_000_000_000L / speed);
			while (!p.rings.isEmpty() && now - p.rings.peekFirst() > life) {
				p.rings.removeFirst();
			}

			if (anim == Anim.NONE) {
				p.t = c.down() ? 1f : 0f;
				continue;
			}
			float step = dt / Math.max(0.01f, ANIM_TIME / speed);
			float target = c.down() ? 1f : 0f;
			if (p.t < target) {
				p.t = Math.min(target, p.t + step);
			} else if (p.t > target) {
				p.t = Math.max(target, p.t - step);
			}
		}
	}

	/**
	 * The rings still travelling out of a key, drawn widest and faintest last.
	 *
	 * <p>Scissored to the key: a ring is a thing happening on that key, and letting one sail across
	 * its neighbours turns a row of taps into a mess.
	 */
	private void drawRings(GuiGraphicsExtractor g, Chip chip, int x, int y, int w, int h, int radius,
			int color, float unit) {
		Press p = presses.get(chip.id());
		if (p == null || p.rings.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		float life = RIPPLE_TIME / Math.max(0.1f, chip.look().animSpeed);
		// Far enough to leave by the corners rather than stopping short of them.
		float reach = (float) Math.hypot(w, h);
		// Thin: three rings in the air at once merge into a disc if each is a fat band.
		int thickness = Math.max(1, Math.round(1.2f * unit));
		int alpha = (color >>> 24) & 0xFF;
		g.enableScissor(x, y, x + w, y + h);
		for (long start : p.rings) {
			float age = Math.min(1f, (now - start) / 1_000_000_000f / life);
			int d = Math.round(reach * age);
			if (d < thickness * 2) {
				continue;
			}
			// Fading as it widens, so it leaves rather than stops.
			int a = Math.round(alpha * (1f - age));
			if (a <= 0) {
				continue;
			}
			int cx = x + w / 2 - d / 2;
			int cy = y + h / 2 - d / 2;
			Draw.thickBorder(g, cx, cy, d, d, d / 2, thickness, (a << 24) | (color & 0xFFFFFF));
		}
		g.disableScissor();
	}

	/** Older configs stored a Fade that no longer exists, and Gson leaves those null. */
	private static Anim animOf(Look look) {
		if (look.anim == null) {
			look.anim = Anim.NONE;
		}
		return look.anim;
	}

	private float pressT(String id) {
		Press p = presses.get(id);
		return p == null ? 0f : p.t;
	}


	// ---- individual placement ----

	/** One key's own size, in the same shapes the block uses, so seeding lines up. */
	private static float[] sizeOf(Settings s, Key key) {
		float u = Math.max(8f, key.look.size);
		float g = Math.max(0f, s.gap);
		float full = u * 3 + g * 2;
		if (!key.builtin) {
			// Its own box, falling back to a square of the key size while nothing has been set.
			return new float[] {key.width > 0 ? key.width : u, key.height > 0 ? key.height : u};
		}
		return switch (key.form) {
			case BAR -> new float[] {full, u * 0.6f};
			case HALF -> new float[] {(full - g) / 2f, u};
			default -> new float[] {u, u};
		};
	}

	/**
	 * Where a key sits when each is placed separately, seeded on first use from where the block had
	 * it — switching modes should move nothing until you move it yourself.
	 */
	private void seedPositions(Minecraft mc, Settings s, float screenW, float screenH) {
		boolean anyUnplaced = false;
		for (Key k : keys(mc, s)) {
			if (!k.placed) {
				anyUnplaced = true;
				break;
			}
		}
		if (!anyUnplaced) {
			return;
		}
		Rect block = bounds(mc, screenW, screenH);
		for (Chip c : blockChips(mc, true)) {
			for (Key k : s.keys) {
				if (k.id.equals(c.id()) && !k.placed) {
					partMoveTo(k.pos, block.x() + c.x() * s.scale, block.y() + c.y() * s.scale,
							c.w() * s.scale, c.h() * s.scale, screenW, screenH);
					k.placed = true;
				}
			}
		}
		// A key added later, or one the block was not showing, has nowhere to have been seeded
		// from. It goes where the block starts, which is at least on screen and easy to find.
		for (Key k : s.keys) {
			if (!k.placed) {
				float[] size = sizeOf(s, k);
				partMoveTo(k.pos, block.x(), block.y(), size[0] * s.scale, size[1] * s.scale,
						screenW, screenH);
				k.placed = true;
			}
		}
	}

	private List<Chip> individualChips(Minecraft mc, float screenW, float screenH) {
		Settings s = s();
		seedPositions(mc, s, screenW, screenH);
		List<Key> all = keys(mc, s);
		List<Chip> out = new ArrayList<>(all.size());
		for (Key k : all) {
			if (!k.shown) {
				continue;
			}
			float[] size = sizeOf(s, k);
			Rect r = partBounds(k.pos, size[0] * s.scale, size[1] * s.scale, screenW, screenH);
			out.add(new Chip(k.id, r.x(), r.y(), r.w(), r.h(), chipLabel(s, k, mc),
					chipSub(s, k), isDown(mc, k), "jump".equals(k.id), k.look));
		}
		return out;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		Settings s = s();
		return Math.max(8f, s.keySize) * 3 + Math.max(0f, s.gap) * 2;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		Settings s = s();
		float u = Math.max(8f, s.keySize);
		float g = Math.max(0f, s.gap);
		float h = u * 2 + g; // the two letter rows
		if (s.showMouse) {
			h += g + u;
		}
		float bar = u * 0.6f;
		if (s.showSneak) {
			h += g + bar;
		}
		if (s.showSpace) {
			h += g + bar;
		}
		return h;
	}

	@Override
	public List<Draggable> draggables(Minecraft mc, float screenW, float screenH) {
		Settings s = s();
		if (s.mode != Mode.INDIVIDUAL) {
			return super.draggables(mc, screenW, screenH);
		}
		seedPositions(mc, s, screenW, screenH);
		List<Draggable> out = new ArrayList<>();
		for (Key k : keys(mc, s)) {
			if (!k.shown) {
				continue;
			}
			float[] size = sizeOf(s, k);
			float w = size[0] * s.scale;
			float h = size[1] * s.scale;
			out.add(new Draggable() {
				@Override
				public Rect bounds() {
					return partBounds(k.pos, w, h, screenW, screenH);
				}

				@Override
				public void moveTo(float x, float y) {
					partMoveTo(k.pos, x, y, w, h, screenW, screenH);
				}

				@Override
				public void scaleBy(float delta) {
					// Scrolling one key changes that key, which is the whole point of the mode.
					k.look.size = Math.max(8f, Math.min(80f, k.look.size + delta * 20f));
				}
			});
		}
		return out;
	}

	// ---- render ----

	@Override
	public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Settings s = s();
		sampleClicks(mc);

		List<Chip> chips;
		float originX = 0;
		float originY = 0;
		if (s.mode == Mode.GROUPED) {
			Rect r = bounds(mc, g.guiWidth(), g.guiHeight());
			chips = groupedChips(mc);
			originX = r.x();
			originY = r.y();
		} else {
			// Individual chips already carry screen positions, so the origin adds nothing.
			chips = individualChips(mc, g.guiWidth(), g.guiHeight());
		}
		advance(chips);

		for (Chip chip : chips) {
			float scale = s.mode == Mode.GROUPED ? s.scale : 1f;
			int x = Math.round(originX + chip.x() * scale);
			int y = Math.round(originY + chip.y() * scale);
			int w = Math.round(chip.w() * scale);
			int h = Math.round(chip.h() * scale);
			drawChip(g, mc, s, chip, x, y, w, h);
		}
	}


	private void drawChip(GuiGraphicsExtractor g, Minecraft mc, Settings s, Chip chip, int x, int y, int w, int h) {
		Look look = chip.look();
		float t = pressT(chip.id());
		// A key drawn twice its size wants its text and its corners twice the size too, so both are
		// taken as a share of the size the module was drawn for rather than as a count of pixels.
		float unit = s.scale * (Math.max(8f, look.size) / BASE_SIZE);
		int radius = Math.round(Math.max(0, look.radius) * unit);
		int idleBg = look.idleColor.argbAt(0, 1);
		int downBg = look.pressedColor.argbAt(0, 1);
		int idleFg = look.textColor.argbAt(0, 1);
		int downFg = look.pressedTextColor.argbAt(0, 1);

		Anim anim = animOf(look);
		// Sink presses the key away from you, so it loses a little of itself on every side.
		if (anim.sinks() && t > 0) {
			int inset = Math.round(t * 2f * unit);
			x += inset;
			y += inset;
			w = Math.max(1, w - inset * 2);
			h = Math.max(1, h - inset * 2);
		}

		// A ripple is the whole answer to a press: lighting the key as well leaves the ring with
		// nothing to travel across.
		boolean lit = chip.down() && !anim.ripples();
		int bx = x;
		int by = y;
		int bw = w;
		int bh = h;
		Runnable fill = () -> {
			// The key's own colour IS its background, so the blur belongs under it — the same
			// capture every other module's background blur draws from.
			if (look.blur && dev.clientify.client.hud.BlurBackdrop.prepare(mc)) {
				Draw.backdropRounded(g, dev.clientify.client.hud.BlurBackdrop.TEXTURE_ID,
						bx, by, bw, bh, radius, 0, 0, 1f, g.guiWidth(), g.guiHeight(),
						dev.clientify.client.hud.BlurBackdrop.vFlip());
			}
			Draw.smoothRounded(g, bx, by, bw, bh, radius, lit ? downBg : idleBg);
		};
		// A key colour is this module's background, so Flat Backgrounds has to reach it as well.
		// Without this, two overlapping keys — or a key over another module's chip — stacked their
		// alpha into a darker patch, while every module that goes through drawChrome did not.
		//
		// A PRESSED key is never masked. The mask works by dropping the later background wherever
		// an earlier one already painted, which is only ever cosmetic for a decorative chip — but
		// here the colour IS the state, so deferring it hid the press entirely under anything the
		// key overlapped. Reading the keystroke beats one frame of darker corner.
		//
		// Only the fill is masked. The outline and the ripple rings are drawn after it, and the
		// mask claims the area a chip covered so nothing later paints there: clipping a ring to
		// what is still unclaimed would cut the animation in half as it travelled outward.
		if (dev.clientify.client.hud.ChromeMask.enabled() && !lit) {
			dev.clientify.client.hud.ChromeMask.draw(g, bx, by, bw, bh, radius, fill);
		} else {
			fill.run();
		}
		if (anim.ripples()) {
			drawRings(g, chip, x, y, w, h, radius, downBg, unit);
		}
		if (look.outline) {
			// Outward, exactly as every other module draws its border: the line sits AROUND the key
			// rather than eating into it, and its corner follows the key's own.
			int t2 = Math.max(1, Math.round(look.outlineThickness * unit));
			Draw.thickBorder(g, x - t2, y - t2, w + t2 * 2, h + t2 * 2,
					radius == 0 ? 0 : radius + t2, t2, look.outlineColor.argbAt(0, 1));
		}

		int fg = chip.down() ? downFg : idleFg;
		if (chip.line()) {
			// A space bar is a bar. Its own line rather than the word, centred in the chip.
			int thickness = Math.max(1, Math.round(s.spaceThickness * unit));
			int span = Math.max(1, Math.round(w * Math.max(0.05f, Math.min(1f, s.spaceLength))));
			Draw.smoothRounded(g, x + (w - span) / 2, y + (h - thickness) / 2, span,
					thickness, thickness / 2, fg);
			return;
		}
		drawLabel(g, mc, s, chip, x, y, w, h, fg, unit);
	}

	/** Narrowed until the text fits inside the key, rather than running out over the rest. */
	private static float fitted(Minecraft mc, ModuleSettings s, String text, int w, float unit) {
		float room = w - 4;
		if (room <= 0 || text == null || text.isEmpty()) {
			return unit;
		}
		float wide = HudText.width(mc, s, text, unit) - HudText.trailing(s, unit);
		return wide <= room ? unit : Math.max(unit * 0.35f, unit * room / wide);
	}

	/**
	 * The label, and the clicks under it when there are any.
	 *
	 * <p>Centred on the ink rather than on the line box: a font's line height carries slack under
	 * the baseline for descenders, and centring against that sits every label a pixel high.
	 */
	private void drawLabel(GuiGraphicsExtractor g, Minecraft mc, Settings s, Chip chip, int x, int y, int w,
			int h, int fg, float unit) {
		String label = chip.label();
		unit = fitted(mc, s, label, w, unit);
		float ink = HudText.lineHeight(mc, s, unit) - HudText.trailing(s, unit);
		if (chip.sub() == null) {
			float tw = HudText.width(mc, s, label, unit) - HudText.trailing(s, unit);
			HudText.draw(g, mc, s, label, x + (w - tw) / 2f, y + (h - ink) / 2f, fg, unit);
			return;
		}
		float subUnit = unit * CPS_SCALE;
		float subInk = HudText.lineHeight(mc, s, subUnit) - HudText.trailing(s, subUnit);
		float top = y + (h - (ink + subInk)) / 2f;
		float tw = HudText.width(mc, s, label, unit) - HudText.trailing(s, unit);
		HudText.draw(g, mc, s, label, x + (w - tw) / 2f, top, fg, unit);
		String sub = chip.sub();
		float sw = HudText.width(mc, s, sub, subUnit) - HudText.trailing(s, subUnit);
		HudText.draw(g, mc, s, sub, x + (w - sw) / 2f, top + ink, fg, subUnit);
	}

	// ---- settings ----

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = s();
		Settings d = new Settings();
		Minecraft mc = Minecraft.getInstance();

		rows.add(screen.withTooltip(
				screen.cycleRow("Mode", () -> s.mode.label(),
						() -> s.mode = cycle(s.mode, -1), () -> s.mode = cycle(s.mode, 1),
						() -> s.mode = d.mode),
				"Grouped draws the keys as one block from one set of\n"
						+ "settings. Individual gives every key its own place,\n"
						+ "size and colours, seeded from where the block had it."));
		rows.add(screen.withTooltip(
				screen.dualToggle("Arrow Keys", () -> s.arrows, v -> s.arrows = v,
						"Show CPS", () -> s.showCps, v -> s.showCps = v),
				"Draws the movement keys as arrows instead of the\nletters they are bound to.",
				"Writes clicks per second in small under each mouse\n"
						+ "button. Counted off the press, once a frame, so fast\n"
						+ "clicking is not rounded away."));
		if (s.mode == Mode.INDIVIDUAL) {
			// No Space Bar switch to hang them off in this mode, so they stand on their own.
			addSpaceRows(screen, rows, s, d);
		}

		if (s.mode == Mode.GROUPED) {
			appendGroupedRows(screen, rows, s, d);
		} else {
			appendIndividualRows(screen, rows, s, d, mc);
		}
	}

	private void appendGroupedRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Settings s, Settings d) {
		rows.add(screen.withTooltip(
				screen.dualToggleGear(
						"Mouse Buttons", () -> s.showMouse, v -> s.showMouse = v, null, () -> false,
						"Space Bar", () -> s.showSpace, v -> s.showSpace = v,
						() -> expand("space"), () -> "space".equals(expandedKey)),
				"Adds a row for the attack and use buttons under\nthe movement keys.",
				"A wide bar for the jump key, drawn as a line. Its\n"
						+ "gear sets how thick and how long that line is."));
		if ("space".equals(expandedKey)) {
			screen.groupCard(rows, group -> addSpaceRows(screen, group, s, d));
		}
		rows.add(screen.withTooltip(
				screen.toggle("Sneak Key", () -> s.showSneak, v -> s.showSneak = v,
						() -> s.showSneak = d.showSneak),
				"A wide bar for the sneak key, showing whatever you\nhave it bound to."));
		rows.add(screen.sliderRow("Key Gap", 0f, 8f, 1f, () -> s.gap, v -> s.gap = v, "%.0f",
				() -> s.gap = d.gap));
		appendLookRows(screen, rows, new LookAccess() {
			@Override
			public float size() {
				return s.keySize;
			}

			@Override
			public void size(float v) {
				s.keySize = v;
			}

			@Override
			public int radius() {
				return s.keyRadius;
			}

			@Override
			public void radius(int v) {
				s.keyRadius = v;
			}

			@Override
			public boolean outline() {
				return s.outline;
			}

			@Override
			public void outline(boolean v) {
				s.outline = v;
			}

			@Override
			public float outlineThickness() {
				return s.outlineThickness;
			}

			@Override
			public void outlineThickness(float v) {
				s.outlineThickness = v;
			}

			@Override
			public boolean blur() {
				return s.blur;
			}

			@Override
			public void blur(boolean v) {
				s.blur = v;
			}

			@Override
			public Anim anim() {
				return s.anim;
			}

			@Override
			public void anim(Anim v) {
				s.anim = v;
			}

			@Override
			public float animSpeed() {
				return s.animSpeed;
			}

			@Override
			public void animSpeed(float v) {
				s.animSpeed = v;
			}

			@Override
			public ModuleSettings.ColorSpec color(int which) {
				return switch (which) {
					case 0 -> s.idleColor;
					case 1 -> s.pressedColor;
					case 2 -> s.idleTextColor;
					case 3 -> s.pressedTextColor;
					default -> s.outlineColor;
				};
			}
		}, new Look(), true);
	}

	/** How the space bar line is drawn. Shared by the gear card and individual mode. */
	private void addSpaceRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Settings s, Settings d) {
		rows.add(screen.withTooltip(
				screen.sliderRow("Spacebar Thickness", 1f, 8f, 0.5f, () -> s.spaceThickness,
						v -> s.spaceThickness = v, "%.1f", () -> s.spaceThickness = d.spaceThickness),
				"How thick the line across the space bar is drawn."));
		rows.add(screen.withTooltip(
				screen.sliderRow("Spacebar Length", 0.1f, 1f, 0.05f, () -> s.spaceLength,
						v -> s.spaceLength = v, "%.2f", () -> s.spaceLength = d.spaceLength),
				"How much of the bar the line spans. 1 runs it wall\n"
						+ "to wall."));
	}

	private void appendIndividualRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Settings s, Settings d, Minecraft mc) {
		rows.add(screen.note(() -> "Drag each key where you want it in the HUD editor."));
		rows.add(screen.withTooltip(
				screen.button("Copy Options", () -> {
					Look from = groupedLook(s);
					for (Key k : keys(mc, s)) {
						k.look.copyFrom(from);
					}
				}),
				"Copies every setting from Grouped mode onto all the\n"
						+ "individual keys — the way back out of a set you no\n"
						+ "longer like."));
		rows.add(screen.withTooltip(
				screen.button("Add Key", () -> {
					Key added = new Key(freshId(s), "", Form.SQUARE, false);
					added.look.copyFrom(groupedLook(s));
					s.keys.add(added);
					expandedKey = added.id;
					screen.refreshTextFields();
				}),
				"Adds a key to the board. It starts on nothing: open\n"
						+ "its gear, click the Key button and press whatever key\n"
						+ "or mouse button you want it to watch."));

		List<Key> all = keys(mc, s);
		for (int i = 0; i < all.size(); i += 2) {
			Key left = all.get(i);
			Key right = i + 1 < all.size() ? all.get(i + 1) : null;
			rows.add(screen.dualToggleGear(
					rowName(left), () -> left.shown, v -> left.shown = v,
					() -> expand(left.id), () -> left.id.equals(expandedKey),
					right == null ? null : rowName(right),
					right == null ? () -> false : () -> right.shown,
					right == null ? v -> {
					} : v -> right.shown = v,
					right == null ? null : () -> expand(right.id),
					right == null ? () -> false : () -> right.id.equals(expandedKey)));
			if (left.id.equals(expandedKey)) {
				appendKeyCard(screen, rows, s, mc, left);
			}
			if (right != null && right.id.equals(expandedKey)) {
				appendKeyCard(screen, rows, s, mc, right);
			}
		}
	}

	/** One key's own card: which binding it watches, and everything about how it is drawn. */
	/** Where a key sits among the keys that have a text box, which only added keys do. */
	private int fieldIndex(Minecraft mc, Settings s, Key key) {
		int at = 0;
		for (Key k : keys(mc, s)) {
			if (k == key) {
				return at;
			}
			if (!k.builtin) {
				at++;
			}
		}
		return -1;
	}

	private void appendKeyCard(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			Settings s, Minecraft mc, Key key) {
		screen.groupCard(rows, group -> {
			if (!key.builtin) {
				group.add(screen.withTooltip(
						screen.rawKeybindRow("Key", key, () -> key.rawKey, v -> key.rawKey = v,
								() -> key.rawKey = ""),
						"Click, then press the key or mouse button this one\n"
								+ "watches. It does not have to be bound to anything --\n"
								+ "nothing is rebound, it is only watched. Escape\n"
								+ "clears it."));
			}
			if (!key.builtin) {
				group.add(screen.textFieldRow(fieldIndex(mc, s, key)));
			}
			if (!key.builtin) {
				group.add(screen.withTooltip(
						screen.sliderRow("Width", 8f, 120f, 1f,
								() -> key.width > 0 ? key.width : key.look.size,
								v -> key.width = v, "%.0f", () -> key.width = 0f),
						"How wide the key is drawn, before the module scale."));
				group.add(screen.withTooltip(
						screen.sliderRow("Height", 8f, 120f, 1f,
								() -> key.height > 0 ? key.height : key.look.size,
								v -> {
									key.height = v;
									// The text and the corners are a share of the key size, so height
									// carries them with it -- a taller key with the same small print
									// reads as a mistake.
									key.look.size = v;
								}, "%.0f", () -> {
									key.height = 0f;
									key.look.size = new Look().size;
								}),
						"How tall the key is drawn. The text and corners grow\n"
								+ "with it."));
			}
			appendLookRows(screen, group, lookAccess(key.look), new Look(), key.builtin);
			group.add(screen.withTooltip(
					screen.button("Apply To All", () -> {
						for (Key other : keys(mc, s)) {
							if (other != key) {
								other.look.copyFrom(key.look);
							}
						}
					}),
					"Gives every other key this one's look. Copy Options\n"
							+ "does the same from the Grouped settings."));
			if (!key.builtin) {
				group.add(screen.button("Remove Key", () -> {
					s.keys.remove(key);
					presses.remove(key.id);
					expandedKey = null;
					screen.refreshTextFields();
				}));
			}
		});
	}

	/**
	 * The first id nothing is using.
	 *
	 * <p>Counted rather than stamped with the clock: two keys added inside the same millisecond
	 * would have shared an id, and an id is what the animation state and the lookup are keyed on.
	 * It also keeps the config legible.
	 */
	private static String freshId(Settings s) {
		for (int n = 1; ; n++) {
			String id = "custom" + n;
			boolean taken = false;
			for (Key k : s.keys) {
				if (id.equals(k.id)) {
					taken = true;
					break;
				}
			}
			if (!taken) {
				return id;
			}
		}
	}



	private void expand(String id) {
		expandedKey = id.equals(expandedKey) ? null : id;
	}

	// ---- look rows, written once and pointed at either a Look or the grouped settings ----

	/** What the look rows read and write, so grouped and per-key share one set of them. */
	private interface LookAccess {
		float size();

		void size(float v);

		int radius();

		void radius(int v);

		boolean outline();

		default void outline(boolean v) {
		}

		float outlineThickness();

		void outlineThickness(float v);

		boolean blur();

		void blur(boolean v);

		Anim anim();

		void anim(Anim v);

		float animSpeed();

		void animSpeed(float v);

		/** 0 idle, 1 pressed, 2 text, 3 pressed text, 4 outline. */
		ModuleSettings.ColorSpec color(int which);
	}

	private static LookAccess lookAccess(Look look) {
		return new LookAccess() {
			@Override
			public float size() {
				return look.size;
			}

			@Override
			public void size(float v) {
				look.size = v;
			}

			@Override
			public int radius() {
				return look.radius;
			}

			@Override
			public void radius(int v) {
				look.radius = v;
			}

			@Override
			public boolean outline() {
				return look.outline;
			}

			@Override
			public void outline(boolean v) {
				look.outline = v;
			}

			@Override
			public float outlineThickness() {
				return look.outlineThickness;
			}

			@Override
			public void outlineThickness(float v) {
				look.outlineThickness = v;
			}

			@Override
			public boolean blur() {
				return look.blur;
			}

			@Override
			public void blur(boolean v) {
				look.blur = v;
			}

			@Override
			public Anim anim() {
				return look.anim;
			}

			@Override
			public void anim(Anim v) {
				look.anim = v;
			}

			@Override
			public float animSpeed() {
				return look.animSpeed;
			}

			@Override
			public void animSpeed(float v) {
				look.animSpeed = v;
			}

			@Override
			public ModuleSettings.ColorSpec color(int which) {
				return switch (which) {
					case 0 -> look.idleColor;
					case 1 -> look.pressedColor;
					case 2 -> look.textColor;
					case 3 -> look.pressedTextColor;
					default -> look.outlineColor;
				};
			}
		};
	}

	private void appendLookRows(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			LookAccess a, Look d, boolean withSize) {
		if (withSize) {
			rows.add(screen.withTooltip(
					screen.sliderRow("Key Size", 12f, 40f, 1f, a::size, a::size, "%.0f",
							() -> a.size(d.size)),
					"How big one key is before the module scale. Text and\n"
							+ "corners grow with it."));
		}
		rows.add(screen.sliderRow("Key Rounding", 0f, 12f, 1f, () -> (float) a.radius(),
				v -> a.radius(Math.round(v)), "%.0f", () -> a.radius(d.radius)));
		rows.add(screen.withTooltip(
				screen.cycleRow("Animation", () -> a.anim().label(),
						() -> a.anim(cycle(a.anim(), -1)), () -> a.anim(cycle(a.anim(), 1)),
						() -> a.anim(d.anim)),
				"How a key answers a press. Ripple sends a ring out from\n"
						+ "the middle on each press and leaves the key itself\n"
						+ "alone; a new ring does not cut the last one short, so\n"
						+ "clicking fast puts several in the air. Sink presses\n"
						+ "the key away from you. The last does both."));
		if (a.anim() != Anim.NONE) {
			rows.add(screen.sliderRow("Animation Speed", 0.25f, 4f, 0.05f, a::animSpeed, a::animSpeed,
					"%.2f", () -> a.animSpeed(d.animSpeed)));
		}
		rows.add(screen.withTooltip(
				screen.toggle("Outline", a::outline, a::outline, () -> a.outline(d.outline)),
				"A line round the key, in the colour below."));
		if (a.outline()) {
			rows.add(screen.sliderRow("Outline Thickness", 1f, 4f, 0.5f, a::outlineThickness,
					a::outlineThickness, "%.1f", () -> a.outlineThickness(d.outlineThickness)));
			screen.addColorRows(rows, "Outline Color", () -> a.color(4),
					() -> a.color(4).copyFrom(d.outlineColor));
		}
		rows.add(screen.withTooltip(
				screen.blurToggle("Blur", a::blur, a::blur, () -> a.blur(d.blur)),
				"Blurs the world behind the key, under its colour.\n"
						+ "A translucent key colour is what lets it show."));
		screen.addColorRows(rows, "Key Color", () -> a.color(0),
				() -> a.color(0).copyFrom(d.idleColor));
		screen.addColorRows(rows, "Pressed Color", () -> a.color(1),
				() -> a.color(1).copyFrom(d.pressedColor));
		screen.addColorRows(rows, "Text Color", () -> a.color(2),
				() -> a.color(2).copyFrom(d.textColor));
		screen.addColorRows(rows, "Pressed Text Color", () -> a.color(3),
				() -> a.color(3).copyFrom(d.pressedTextColor));
	}
}
