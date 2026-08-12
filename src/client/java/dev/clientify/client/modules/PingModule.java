package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.Templated;
import dev.clientify.client.hud.TextHudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import net.minecraft.util.Util;

/**
 * Real measured ping: sends the vanilla play-state ping request on a user-set interval and
 * times the pong (the same packets vanilla's F3+2 ping monitor uses). Works on any 1.20.2+
 * server, unlike the tab-list latency which the server only refreshes sporadically.
 */
public class PingModule extends TextHudModule implements Templated {
	private static final String PLACEHOLDER = "%ping%";

	/** RTT of the newest pong; -1 = no measurement yet. Written from the packet handler. */
	private static volatile int lastPing = -1;
	private long lastSentAt;

	public static class Settings extends ModuleSettings {
		public String template = "%ping% ms";
		/** Seconds between ping requests. */
		public float refreshInterval = 2.0f;

		public Settings() {
			offsetY = 22;
		}
	}

	public PingModule() {
		super("ping", "Ping");
	}

	/** Called by ClientPacketListenerMixin with the pong's echoed send-timestamp. */
	public static void onPongResponse(long echoedMillis) {
		long rtt = Util.getMillis() - echoedMillis;
		if (rtt >= 0 && rtt < 60000) {
			lastPing = (int) rtt;
		}
	}

	@Override
	public String description() {
		return "Displays your measured ping to the server.";
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
	public void tick(Minecraft mc) {
		if (!isEnabled() || mc.getConnection() == null || mc.player == null) {
			return;
		}
		long now = Util.getMillis();
		long interval = (long) (((Settings) settings()).refreshInterval * 1000f);
		if (now - lastSentAt >= Math.max(250, interval)) {
			lastSentAt = now;
			mc.getConnection().send(new ServerboundPingRequestPacket(now));
		}
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		rows.add(screen.sliderRow("Refresh Interval", 0.5f, 10f, 0.5f, () -> s.refreshInterval,
				v -> s.refreshInterval = v, "%.1fs", () -> s.refreshInterval = 2.0f));
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
		return "%ping% ms";
	}

	@Override
	protected String label(Minecraft mc) {
		return "ms";
	}

	@Override
	protected String value(Minecraft mc) {
		return lastPing < 0 ? "?" : Integer.toString(lastPing);
	}

	@Override
	protected List<Seg> segments(Minecraft mc) {
		String template = ((Settings) settings()).template;
		int at = template.indexOf(PLACEHOLDER);
		if (at < 0) {
			return List.of(new Seg(template.isEmpty() ? "Ping" : template, false));
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

	/** Static chip width: reserve at least 2 wide digits so the chip doesn't jitter. */
	@Override
	protected String measureText(Seg seg) {
		if (!seg.isValue()) {
			return seg.text();
		}
		return "8".repeat(Math.max(2, seg.text().length()));
	}
}
