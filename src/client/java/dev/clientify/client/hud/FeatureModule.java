package dev.clientify.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Base for non-visual feature/tweak modules: no HUD chip, no own rendering. */
public abstract class FeatureModule extends HudModule {
	protected FeatureModule(String id, String displayName) {
		super(id, displayName);
	}

	@Override
	public boolean isHudElement() {
		return false;
	}

	@Override
	public float unscaledWidth(Minecraft mc) {
		return 0;
	}

	@Override
	public float unscaledHeight(Minecraft mc) {
		return 0;
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
	}
}
