package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.TextHudModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Lunar-style sprint/movement state indicator. State priority:
 * riding > gliding > flying > sprint-toggle-armed/active > sprint-held > not sprinting.
 *
 * <p>When vanilla "Toggle Sprint" is on, {@code keySprint.isDown()} is the armed toggle state
 * (not physical key hold) — so we show Toggled even while standing still, because the next
 * forward walk will sprint.
 */
public class SprintModule extends TextHudModule {
	public static class Settings extends ModuleSettings {
		public String textSprintToggled = "[Sprinting (Toggled)]";
		public String textSprintHeld = "[Sprinting (Vanilla)]";
		public String textNotSprinting = "[Not Sprinting]";
		public String textFlying = "[Flying]";
		/** Shown while the boost is actually being applied; %mult% is the Fly Speed setting. */
		public String textFlyingBoost = "[Flying (%mult%x boost)]";
		public String textGliding = "[Gliding]";
		public String textRiding = "[Riding %entity%]";
		/**
		 * Lunar-style creative/spectator fly speed multiplier. Applied only while the sprint key
		 * is down, the way vanilla's own fly sprint works — an always-on boost makes precise
		 * flying (placing blocks, lining up a build) impossible.
		 */
		public boolean flyBoost = false;
		public float flyMultiplier = 4f;

		public Settings() {
			anchor = Anchor.BOTTOM_LEFT;
			offsetX = 5;
			offsetY = -5;
			background = false;
		}
	}

	public SprintModule() {
		super("sprint", "Sprint Indicator");
	}

	@Override
	public String description() {
		return "Shows your sprint and movement state.";
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
	public List<TextField> textFields() {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		return List.of(
				new TextField("Sprinting (Toggled)", () -> s.textSprintToggled, v -> s.textSprintToggled = v,
						d.textSprintToggled),
				new TextField("Sprinting (Held)", () -> s.textSprintHeld, v -> s.textSprintHeld = v,
						d.textSprintHeld),
				new TextField("Not Sprinting", () -> s.textNotSprinting, v -> s.textNotSprinting = v,
						d.textNotSprinting),
				new TextField("Flying", () -> s.textFlying, v -> s.textFlying = v, d.textFlying),
				new TextField("Flying (Boosted)", () -> s.textFlyingBoost, v -> s.textFlyingBoost = v,
						d.textFlyingBoost),
				new TextField("Gliding", () -> s.textGliding, v -> s.textGliding = v, d.textGliding),
				new TextField("Riding (%entity%)", () -> s.textRiding, v -> s.textRiding = v, d.textRiding));
	}

	/** Vanilla's default flying speed — the fallback when nothing else set one. */
	private static final float BASE_FLY_SPEED = 0.05f;

	/**
	 * The fastest the boost will ask for ON A SERVER, whatever the slider says.
	 *
	 * <p>A server teleports you back when one tick of movement covers more than 100 blocks squared
	 * ({@code ServerGamePacketListenerImpl.shouldCheckPlayerMovement} — and creative flight is NOT
	 * exempt; only the host of a singleplayer world, a dimension change, or the gamerule being off
	 * skip it). Vanilla doubles the flying speed again while sprinting and flight settles at
	 * roughly eleven times that per tick, so eight times over came to nearly nine blocks a tick:
	 * seventy-seven squared flat, and a hundred and fifty-five once a dive adds a vertical
	 * component. That is the set-back.
	 *
	 * <p>This ceiling holds it near five and a half blocks a tick, which stays inside the budget
	 * even diagonally. Your own world is not checked at all, so nothing is capped there.
	 */
	private static final float MAX_SERVER_FLY_SPEED = 0.25f;
	private boolean boosting;
	/** The speed in force before the boost, so releasing sprint restores it rather than 0.05 —
	 * creative servers do set their own fly speed, and this now toggles constantly. */
	private float preBoostSpeed = BASE_FLY_SPEED;

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		rows.add(screen.toggle("Fly Boost", () -> s.flyBoost, v -> s.flyBoost = v, () -> s.flyBoost = false));
		if (s.flyBoost) {
			rows.add(screen.withTooltip(
					screen.sliderRow("Fly Speed", 1f, 8f, 0.5f, () -> s.flyMultiplier,
							v -> s.flyMultiplier = v, "%.1fx", () -> s.flyMultiplier = 4f),
					"How much faster than a normal sprint-fly. On a server\n"
							+ "this is held to about five times over: past that, one\n"
							+ "tick of movement breaks the distance a server will\n"
							+ "accept and it teleports you back. Your own world is\n"
							+ "not movement-checked, so it runs the full range."));
		}
	}

	/**
	 * True while the boost is actually in force: the sprint key has to be physically HELD, so the
	 * boost is a thing you reach for rather than a permanent speed change.
	 *
	 * <p>The physical key rather than {@code keySprint.isDown()} — with vanilla "Toggle Sprint" on
	 * that is the latched toggle, which would quietly make the boost a toggle too. A screen being
	 * open counts as not held, which is what the game does with every other key.
	 */
	private boolean boostActive(Minecraft mc) {
		Settings s = (Settings) settings();
		return isEnabled() && s.flyBoost && mc.player != null && mc.gui.screen() == null
				&& mc.player.getAbilities().mayfly
				&& dev.clientify.client.util.HoldableKey.isHeld(mc.options.keySprint);
	}

	/**
	 * What to write so the player actually flies at the multiplier on the slider, capped to what a
	 * server will accept.
	 *
	 * <p>Written against the speed the server last gave us rather than vanilla's 0.05, so a
	 * creative server that sets its own flight speed is multiplied rather than overruled.
	 */
	private float boostedSpeed(Minecraft mc, Settings s) {
		float base = preBoostSpeed > 0f ? preBoostSpeed : BASE_FLY_SPEED;
		float wanted = base * s.flyMultiplier;
		// The host of a singleplayer world is never movement-checked, so let it have the lot.
		return mc.hasSingleplayerServer() ? wanted : Math.min(MAX_SERVER_FLY_SPEED, wanted);
	}

	@Override
	public void tick(Minecraft mc) {
		Settings s = (Settings) settings();
		if (mc.player == null) {
			// Between worlds: forget the boost rather than restore one world's speed into another.
			boosting = false;
			return;
		}
		var abilities = mc.player.getAbilities();
		if (boostActive(mc)) {
			if (!boosting) {
				preBoostSpeed = abilities.getFlyingSpeed();
				boosting = true;
			}
			abilities.setFlyingSpeed(boostedSpeed(mc, s));
		} else if (boosting) {
			abilities.setFlyingSpeed(preBoostSpeed);
			boosting = false;
		}
	}

	@Override
	protected String label(Minecraft mc) {
		return "";
	}

	@Override
	protected String value(Minecraft mc) {
		return "";
	}

	@Override
	protected List<List<Seg>> lineSegments(Minecraft mc) {
		Settings s = (Settings) settings();
		LocalPlayer p = mc.player;
		String text;
		if (p == null) {
			text = s.textNotSprinting;
		} else if (p.getVehicle() != null) {
			text = s.textRiding.replace("%entity%", p.getVehicle().getName().getString());
		} else if (p.isFallFlying()) {
			text = s.textGliding;
		} else if (p.getAbilities().flying) {
			text = boostActive(mc) ? s.textFlyingBoost.replace("%mult%", multiplierText(s)) : s.textFlying;
		} else {
			boolean toggleMode = mc.options.toggleSprint().get();
			// In toggle mode, isDown() is the latched toggle (armed for next walk / actively sprinting).
			boolean sprintKeyActive = mc.options.keySprint.isDown();
			if (toggleMode && sprintKeyActive) {
				text = s.textSprintToggled;
			} else if (p.isSprinting() || (!toggleMode && sprintKeyActive)) {
				text = s.textSprintHeld;
			} else {
				text = s.textNotSprinting;
			}
		}
		return text.isBlank() ? List.of() : List.of(List.of(new Seg(text, false)));
	}

	/** The multiplier as the slider reads it: 4, not 4.0, but 4.5 when it is on a half step. */
	private static String multiplierText(Settings s) {
		float m = s.flyMultiplier;
		return m == Math.rint(m) ? String.valueOf((int) m)
				: String.format(java.util.Locale.ROOT, "%.1f", m);
	}
}
