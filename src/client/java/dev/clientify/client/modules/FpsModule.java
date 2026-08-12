package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.hud.Templated;
import dev.clientify.client.hud.TextHudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public class FpsModule extends TextHudModule implements Templated {
	private static final String PLACEHOLDER = "%fps%";

	public static class Settings extends ModuleSettings {
		public String template = "%fps% FPS";

		public Settings() {
			enabled = true; // the starter module — visible out of the box
		}
	}

	public FpsModule() {
		super("fps", "FPS");
	}

	@Override
	public String description() {
		return "Display your FPS on the HUD.";
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
		return "%fps% FPS";
	}

	@Override
	protected String label(Minecraft mc) {
		return "FPS";
	}

	@Override
	protected String value(Minecraft mc) {
		return Integer.toString(mc.getFps());
	}

	/** Static chip width: the value slot reserves at least 3 wide digits, so the chip never jitters. */
	@Override
	protected String measureText(Seg seg) {
		if (!seg.isValue()) {
			return seg.text();
		}
		return "8".repeat(Math.max(3, seg.text().length()));
	}

	/** Splits the template around %fps%: literals are label-colored, the number value-colored. */
	@Override
	protected List<Seg> segments(Minecraft mc) {
		String template = ((Settings) settings()).template;
		int at = template.indexOf(PLACEHOLDER);
		if (at < 0) {
			return List.of(new Seg(template.isEmpty() ? "FPS" : template, false));
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
