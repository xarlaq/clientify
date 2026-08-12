package dev.clientify.client.modules;

import dev.clientify.client.config.ModuleSettings;
import dev.clientify.client.config.ModuleSettings.ColorSpec;
import dev.clientify.client.gui.ModuleSettingsScreen;
import dev.clientify.client.gui.SettingsRowsScreen;
import dev.clientify.client.hud.HudModule;
import dev.clientify.client.util.Colors;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Custom entity hitboxes (a configurable replacement for F3+B). Per category — players,
 * aggressive monsters, passive animals, exp orbs, dropped items, arrows — each with its own
 * line color/width, optional fill, and (for living things) look-direction arrow and eye-height
 * line. Plus a highlight for the entity under the crosshair (target) and recently-hurt
 * entities. Boxes are emitted as vanilla gizmos so they are depth-tested exactly like F3+B.
 */
public class HitboxModule extends HudModule {
	/** Per-category style. Serialized by GSON, so all fields public. */
	public static class Cat {
		public boolean enabled = false;
		public ColorSpec color = new ColorSpec("#FFFFFF");
		public float lineWidth = 2.5f;
		public boolean fill = false;
		public ColorSpec fillColor = new ColorSpec("#FFFFFF");
		public float fillOpacity = 0.25f;
		public boolean lookDirection = false;
		public ColorSpec lookColor = new ColorSpec("#0000FF");
		public boolean eyeHeight = false;
		public ColorSpec eyeColor = new ColorSpec("#FF0000");

		public Cat() {
		}

		public Cat(boolean enabled, String color) {
			this.enabled = enabled;
			this.color = new ColorSpec(color);
		}

		void copyFrom(Cat o) {
			enabled = o.enabled;
			color.copyFrom(o.color);
			lineWidth = o.lineWidth;
			fill = o.fill;
			fillColor.copyFrom(o.fillColor);
			fillOpacity = o.fillOpacity;
			lookDirection = o.lookDirection;
			lookColor.copyFrom(o.lookColor);
			eyeHeight = o.eyeHeight;
			eyeColor.copyFrom(o.eyeColor);
		}
	}

	public static class Settings extends ModuleSettings {
		public Cat players = new Cat(true, "#FFFFFF");
		public Cat monsters = new Cat(true, "#FFFFFF");
		public Cat animals = new Cat(true, "#FFFFFF");
		public Cat orbs = new Cat(false, "#FFFFFF");
		public Cat items = new Cat(false, "#FFFFFF");
		public Cat arrows = new Cat(false, "#FFFFFF");
		public boolean hideStuckArrows = true;
		public Cat target = new Cat(false, "#FFAA00");
		public Cat hurt = new Cat(false, "#FF4AD2");
		public boolean limitDistance = false;
		public float maxDistance = 64;

		public Settings() {
			enabled = false;
		}
	}

	private static HitboxModule instance;
	private transient String expandedSection;

	/** True when this module should replace vanilla's F3+B boxes (EntityHitboxDebugRendererMixin). */
	public static boolean replacesVanilla() {
		return instance != null && instance.isEnabled();
	}

	public HitboxModule() {
		super("hitbox", "Hitboxes");
		instance = this;
	}

	@Override
	public String description() {
		return "Draws customizable entity hitboxes (replaces F3+B).";
	}

	@Override
	public String category() {
		return "MECHANIC";
	}

	@Override
	public boolean isHudElement() {
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

	private void expand(String key) {
		expandedSection = key.equals(expandedSection) ? null : key;
	}

	@Override
	public void appendSettings(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows) {
		Settings s = (Settings) settings();
		Settings d = new Settings();
		// Two category switches per line; the expanded one's options card follows the pair.
		pair(screen, rows, "Players", "players", s.players, "Monsters", "monsters", s.monsters);
		catBody(screen, rows, "players", s.players, d.players, true, false, s, d);
		catBody(screen, rows, "monsters", s.monsters, d.monsters, true, false, s, d);
		pair(screen, rows, "Animals", "animals", s.animals, "Exp Orbs", "orbs", s.orbs);
		catBody(screen, rows, "animals", s.animals, d.animals, true, false, s, d);
		catBody(screen, rows, "orbs", s.orbs, d.orbs, false, false, s, d);
		pair(screen, rows, "Dropped Items", "items", s.items, "Arrows", "arrows", s.arrows);
		catBody(screen, rows, "items", s.items, d.items, false, false, s, d);
		catBody(screen, rows, "arrows", s.arrows, d.arrows, false, true, s, d);
		pair(screen, rows, "Target", "target", s.target, "Hurt Entities", "hurt", s.hurt);
		catBody(screen, rows, "target", s.target, d.target, false, false, s, d);
		catBody(screen, rows, "hurt", s.hurt, d.hurt, false, false, s, d);
		rows.add(screen.toggle("Limit Render Distance", () -> s.limitDistance, v -> s.limitDistance = v,
				() -> s.limitDistance = false));
		if (s.limitDistance) {
			rows.add(screen.sliderRow("Max Distance", 16f, 128f, 4f, () -> s.maxDistance,
					v -> s.maxDistance = v, "%.0f", () -> s.maxDistance = 64f));
		}
	}

	private void pair(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows,
			String lLabel, String lKey, Cat lCat, String rLabel, String rKey, Cat rCat) {
		rows.add(screen.dualToggleGear(
				lLabel, () -> lCat.enabled, v -> lCat.enabled = v, () -> expand(lKey), () -> lKey.equals(expandedSection),
				rLabel, () -> rCat.enabled, v -> rCat.enabled = v, () -> expand(rKey), () -> rKey.equals(expandedSection)));
	}

	private void catBody(ModuleSettingsScreen screen, List<SettingsRowsScreen.Row> rows, String key,
			Cat cat, Cat def, boolean living, boolean isArrow, Settings s, Settings d) {
		if (!key.equals(expandedSection)) {
			return;
		}
		screen.groupCard(rows, group -> {
			screen.addColorRows(group, "Line Color", () -> cat.color, () -> cat.color.copyFrom(def.color));
			group.add(screen.sliderRow("Line Width", 1f, 6f, 0.5f, () -> cat.lineWidth, v -> cat.lineWidth = v,
					"%.1f", () -> cat.lineWidth = def.lineWidth));
			group.add(screen.toggle("Fill", () -> cat.fill, v -> cat.fill = v, () -> cat.fill = def.fill));
			if (cat.fill) {
				screen.addColorRows(group, "Fill Color", () -> cat.fillColor,
						() -> cat.fillColor.copyFrom(def.fillColor));
				group.add(screen.sliderRow("Fill Opacity", 5f, 80f, 5f, () -> cat.fillOpacity * 100f,
						v -> cat.fillOpacity = v / 100f, "%.0f%%", () -> cat.fillOpacity = def.fillOpacity));
			}
			if (living) {
				group.add(screen.toggle("Look Direction", () -> cat.lookDirection, v -> cat.lookDirection = v,
						() -> cat.lookDirection = def.lookDirection));
				if (cat.lookDirection) {
					screen.addColorRows(group, "Look Color", () -> cat.lookColor,
							() -> cat.lookColor.copyFrom(def.lookColor));
				}
				group.add(screen.toggle("Eye Height", () -> cat.eyeHeight, v -> cat.eyeHeight = v,
						() -> cat.eyeHeight = def.eyeHeight));
				if (cat.eyeHeight) {
					screen.addColorRows(group, "Eye Color", () -> cat.eyeColor,
							() -> cat.eyeColor.copyFrom(def.eyeColor));
				}
			}
			if (isArrow) {
				group.add(screen.toggle("Hide Stuck Arrows", () -> s.hideStuckArrows, v -> s.hideStuckArrows = v,
						() -> s.hideStuckArrows = d.hideStuckArrows));
			}
		});
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

	// ---- gizmo emission (called from DebugRendererMixin, collector armed) ----

	private Cat catFor(Entity e, Settings s) {
		if (e instanceof Player) {
			return s.players;
		}
		if (e instanceof Enemy) {
			return s.monsters;
		}
		if (e instanceof LivingEntity) {
			return s.animals;
		}
		if (e instanceof ExperienceOrb) {
			return s.orbs;
		}
		if (e instanceof ItemEntity) {
			return s.items;
		}
		if (e instanceof AbstractArrow) {
			return s.arrows;
		}
		return null;
	}

	private boolean living(Cat cat, Settings s) {
		return cat == s.players || cat == s.monsters || cat == s.animals;
	}

	private static AABB boxOf(Entity e, float partialTick) {
		Vec3 delta = e.getPosition(partialTick).subtract(e.position());
		return e.getBoundingBox().move(delta);
	}

	private static void drawBox(AABB box, Cat cat) {
		int stroke = cat.color.chrome();
		GizmoStyle style = cat.fill
				? GizmoStyle.strokeAndFill(stroke, cat.lineWidth, Colors.withAlpha(cat.fillColor.chrome(), cat.fillOpacity))
				: GizmoStyle.stroke(stroke, cat.lineWidth);
		Gizmos.cuboid(box, style);
	}

	public static void emitGizmos(Frustum frustum, double camX, double camY, double camZ, float partialTick) {
		HitboxModule m = instance;
		Minecraft mc = Minecraft.getInstance();
		if (m == null || !m.isEnabled() || m.settings() == null || mc.level == null) {
			return;
		}
		// Only show ours when vanilla hitboxes (F3+B) are on — we replace them (vanilla's are
		// cancelled by EntityHitboxDebugRendererMixin). Off = nothing shows.
		if (!mc.debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
			return;
		}
		Settings s = (Settings) m.settings();
		// Distance cap is opt-in; off = vanilla behavior (every rendered entity in the frustum).
		double maxSq = s.limitDistance ? (double) s.maxDistance * s.maxDistance : Double.MAX_VALUE;
		Entity targetEntity = mc.crosshairPickEntity;
		boolean firstPerson = mc.options.getCameraType() == CameraType.FIRST_PERSON;

		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity.isInvisible()
					|| (entity == mc.getCameraEntity() && firstPerson)
					|| entity.distanceToSqr(camX, camY, camZ) > maxSq
					|| !frustum.isVisible(entity.getBoundingBox())) {
				continue;
			}
			Cat cat = m.catFor(entity, s);
			boolean catEnabled = cat != null && cat.enabled;
			if (catEnabled && cat == s.arrows && s.hideStuckArrows
					&& entity.getDeltaMovement().lengthSqr() < 1.0E-5) {
				catEnabled = false;
			}
			boolean isTarget = s.target.enabled && entity == targetEntity;
			boolean isHurt = s.hurt.enabled && entity instanceof LivingEntity le && le.hurtTime > 0;

			// One box per entity — Target/Hurt RECOLOR the box (priority target > hurt > category).
			Cat boxStyle = isTarget ? s.target : (isHurt ? s.hurt : (catEnabled ? cat : null));
			if (boxStyle == null) {
				continue;
			}

			AABB box = boxOf(entity, partialTick);
			drawBox(box, boxStyle);
			// Look-direction / eye-height use the category's own settings (living categories only).
			if (catEnabled && m.living(cat, s) && entity instanceof LivingEntity le) {
				if (cat.eyeHeight) {
					double eyeY = box.minY + le.getEyeHeight();
					Gizmos.cuboid(new AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ),
							GizmoStyle.stroke(cat.eyeColor.chrome(), cat.lineWidth));
				}
				if (cat.lookDirection) {
					Vec3 eye = le.getPosition(partialTick).add(0, le.getEyeHeight(), 0);
					Gizmos.arrow(eye, eye.add(le.getViewVector(partialTick).scale(2.0)),
							cat.lookColor.chrome(), cat.lineWidth);
				}
			}
		}
	}
}
