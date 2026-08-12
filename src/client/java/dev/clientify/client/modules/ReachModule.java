package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.Templated;
import dev.clientify.client.hud.TextHudModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reach display: on every entity hit, measures the true reach — the distance from your eye
 * to the point where your view ray enters the target's (pick-inflated) hitbox at the moment
 * of the attack. Shown for a configurable time after each hit, then falls back to 0.00.
 */
public class ReachModule extends TextHudModule implements Templated {
	private static final String PLACEHOLDER = "%reach%";

	private static ReachModule instance;
	private static double lastReach;
	private static long lastHitAt;

	public static class Settings extends ModuleSettings {
		public String template = "%reach% blocks";
		/** Seconds each hit's distance stays on screen. */
		public float displayTime = 2.0f;

		public Settings() {
			offsetY = 39;
		}
	}

	public ReachModule() {
		super("reach", "Reach Display");
		instance = this;
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide() && instance.isEnabled()
					&& player == Minecraft.getInstance().player && entity != null) {
				Vec3 eye = player.getEyePosition();
				Vec3 end = eye.add(player.getViewVector(1.0f).scale(8.0));
				Optional<Vec3> hit = entity.getBoundingBox().inflate(entity.getPickRadius()).clip(eye, end);
				if (hit.isPresent()) {
					lastReach = eye.distanceTo(hit.get());
					lastHitAt = Util.getMillis();
				}
			}
			return InteractionResult.PASS;
		});
	}

	@Override
	public String description() {
		return "Shows how far away your last hit landed.";
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
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		rows.add(screen.sliderRow("Display Time", 0.5f, 10f, 0.5f, () -> s.displayTime,
				v -> s.displayTime = v, "%.1fs", () -> s.displayTime = 2.0f));
	}

	@Override
	public String template() {
		return ((Settings) settings()).template;
	}

	@Override
	public void setTemplate(String template) {
		((Settings) settings()).template = template;
	}

	@Override
	public String placeholder() {
		return PLACEHOLDER;
	}

	@Override
	public String defaultTemplate() {
		return "%reach% blocks";
	}

	@Override
	protected String label(Minecraft mc) {
		return "blocks";
	}

	@Override
	protected String value(Minecraft mc) {
		long shownFor = (long) (((Settings) settings()).displayTime * 1000f);
		boolean live = lastHitAt != 0 && Util.getMillis() - lastHitAt <= shownFor;
		return String.format(Locale.ROOT, "%.2f", live ? lastReach : 0.0);
	}

	@Override
	protected List<Seg> segments(Minecraft mc) {
		String template = ((Settings) settings()).template;
		int at = template.indexOf(PLACEHOLDER);
		if (at < 0) {
			return List.of(new Seg(template.isEmpty() ? "Reach" : template, false));
		}
		List<Seg> segs = new ArrayList<>(3);
		if (at > 0) {
			segs.add(new Seg(template.substring(0, at), false));
		}
		segs.add(new Seg(value(mc), true));
		String after = template.substring(at + PLACEHOLDER.length());
		if (!after.isEmpty()) {
			segs.add(new Seg(after, false));
		}
		return segs;
	}
}
