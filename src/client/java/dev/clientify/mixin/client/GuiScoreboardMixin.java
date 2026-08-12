package dev.clientify.mixin.client;

import dev.clientify.client.modules.ScoreboardModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla's sidebar and feeds the prepared lines to the draggable Scoreboard
 * module, which draws them with its own position, font, colors and chrome.
 */
@Mixin(Gui.class)
public abstract class GuiScoreboardMixin {
	@Shadow
	@Final
	private static Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER;

	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void clientify$sidebarTweaks(GuiGraphics g, Objective objective, CallbackInfo ci) {
		ScoreboardModule.Settings s = ScoreboardModule.active();
		ScoreboardModule m = ScoreboardModule.get();
		if (s == null || m == null) {
			return;
		}
		ci.cancel();
		if (s.hide) {
			m.updateContent(null, List.of());
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Scoreboard scoreboard = objective.getScoreboard();
		NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<ScoreboardModule.Line> lines = new ArrayList<>();
		scoreboard.listPlayerScores(objective)
				.stream()
				.filter(entry -> !entry.isHidden())
				.sorted(SCORE_DISPLAY_ORDER)
				.limit(Math.max(1, s.maxLines))
				.forEach(entry -> {
					PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
					Component name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
					Component score = entry.formatValue(numberFormat);
					lines.add(new ScoreboardModule.Line(name, score, mc.font.width(score)));
				});
		m.updateContent(objective.getDisplayName(), lines);
		m.renderSidebar(g, mc);
	}
}
