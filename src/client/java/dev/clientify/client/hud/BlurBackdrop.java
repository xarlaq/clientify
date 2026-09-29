package dev.clientify.client.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.clientify.client.ClientifyClient;
import dev.clientify.client.config.ClientifyConfig;
import dev.clientify.client.config.GlobalSettings;
import dev.clientify.client.gui.PanelScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Per-module frosted backdrop with ZERO custom shaders: once per frame (lazily, only if a
 * visible module has background blur on) the main render target — the finished world, no GUI
 * yet at HUD record time — is copied into our own target and run through VANILLA's
 * "minecraft:blur" post chain (the pause-menu blur, proven on GL/Sodium/VulkanMod). Chips
 * then blit their own screen region from the blurred texture.
 *
 * <p>Blur radius rides the option the post chain reads; {@link #blurOverride()} (called from
 * OptionsMixin) substitutes Clientify's independent sliders: the MENU slider while a
 * Clientify screen with menu-blur is open, the MODULE slider while a blurred chip is visible.
 *
 * <p>GL samples render targets bottom-up, VulkanMod top-down — same v-flip rule FasterHUD
 * verified in production.
 */
public final class BlurBackdrop {
	public static final Identifier TEXTURE_ID = ClientifyClient.id("blur_backdrop");
	private static final Identifier BLUR_CHAIN = Identifier.withDefaultNamespace("blur");

	private static final boolean V_FLIP = !FabricLoader.getInstance().isModLoaded("vulkanmod");

	private static TextureTarget target;
	private static CrossFrameResourcePool pool;
	private static WrapTexture wrapper;
	private static boolean captured;
	private static boolean available;
	/**
	 * Something asked for the backdrop while this frame's GUI was being built, and the copy that
	 * fills it is still to be made - by {@link #capture}, once the world is drawn.
	 */
	private static boolean wanted;
	/**
	 * What has already been said about the blur, so a per-frame condition is reported once instead
	 * of every frame. Chip blur failing is silent by design — the chip just draws flat — which is
	 * fine for the player and useless for working out why.
	 */
	private static String said;

	private BlurBackdrop() {
	}

	public static boolean vFlip() {
		return V_FLIP;
	}

	/** Marks a new frame; the next {@link #prepare} call re-captures. */
	public static void newFrame() {
		captured = false;
	}

	/**
	 * Why blur cannot work on this setup, or null when it can.
	 *
	 * <p>VulkanMod gets as far as it possibly could and still comes out sharp. The
	 * {@code minecraft:blur} post chain exists there, the framebuffer copy works, {@code process}
	 * returns without complaint, and the radius reaches {@code getMenuBackgroundBlurriness} — logged
	 * at radius 10 on 0.6.8. Vanilla passes no radius to {@code process} either; it rides the
	 * GlobalSettingsUniform, and that is where the trail goes cold on a renderer that fills its
	 * uniforms its own way. Nothing on this side is left to fix.
	 *
	 * <p>So the switches say so instead of pretending. The alternative was a hand-rolled blur —
	 * sampling the capture several times at small offsets, which needs no shader — but it would
	 * either replace vanilla's gaussian for everybody or add a VulkanMod-only path that cannot be
	 * tested here, since VulkanMod does not run in the dev client. Neither is worth a soft edge.
	 */
	public static String unsupportedReason() {
		return FabricLoader.getInstance().isModLoaded("vulkanmod")
				? "Not supported with VulkanMod."
				: null;
	}

	/** True when a visible module wants a blurred background this frame. */
	public static boolean wantsBlur() {
		if (unsupportedReason() != null) {
			return false; // nothing to override the blurriness option for
		}
		for (HudModule module : ModuleManager.all()) {
			if (module.isEnabled() && module.wantsBlur()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * OptionsMixin hook: the value vanilla reads as "Menu Background Blurriness".
	 * Null = no override (vanilla behavior).
	 */
	public static Integer blurOverride() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return null;
		}
		GlobalSettings gs = ClientifyConfig.global();
		if (mc.gui.screen() instanceof PanelScreen) {
			return gs.menuBlur ? clampStrength(gs.menuBlurStrength) : null;
		}
		// The blur radius is a per-frame global, so overriding it while ANY vanilla screen is
		// open would also change that screen's own blur. Our module blur only applies in-world.
		if (mc.gui.screen() != null) {
			return null;
		}
		if (wantsBlur()) {
			return clampStrength(gs.moduleBlurStrength);
		}
		return null;
	}

	private static int clampStrength(int v) {
		return Math.max(1, Math.min(10, v));
	}

	/**
	 * Asks for the blurred backdrop this frame and says whether it can be had. False = use the flat
	 * fallback.
	 *
	 * <p>Called while the GUI is being built, which on 26.x is before the world is drawn: a frame
	 * goes update, extract, render. A copy made here would be last frame's finished picture, panel
	 * and all, and blurring that into itself frame after frame turns a menu panel flat grey. So
	 * nothing is copied here; {@link #capture} does it at render time, from the same picture
	 * 1.21.11 copied - the world, or the title panorama, with no GUI on it yet.
	 */
	public static boolean prepare(Minecraft mc) {
		if (captured) {
			wanted |= available;
			return available;
		}
		captured = true;
		available = false;

		String unsupported = unsupportedReason();
		if (unsupported != null) {
			// Bailing before the copy matters: this used to run a full-screen capture and a post
			// chain every frame on VulkanMod and throw the result away.
			say("unavailable on this renderer — skipping the capture entirely");
			return false;
		}

		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		if (main == null || main.getColorTexture() == null) {
			say("no main render target to copy from — chips will draw flat");
			return false;
		}
		if (mc.getShaderManager().getPostChain(BLUR_CHAIN, LevelTargetBundle.MAIN_TARGETS) == null) {
			say("this renderer has no minecraft:blur post chain — chips will draw flat");
			return false;
		}
		// Made now, so the texture the GUI is about to be built against exists this frame.
		ensureTarget(mc, main.width, main.height);
		available = true;
		wanted = true;
		return true;
	}

	/**
	 * Copies and blurs the backdrop, if anything asked for it this frame. GuiRendererMixin calls this
	 * as the GUI renderer starts: the world (or the title panorama) is drawn and no GUI is yet.
	 */
	public static void capture(Minecraft mc) {
		if (!wanted) {
			return;
		}
		wanted = false;
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		PostChain chain = mc.getShaderManager().getPostChain(BLUR_CHAIN, LevelTargetBundle.MAIN_TARGETS);
		if (main == null || main.getColorTexture() == null || chain == null) {
			return;
		}
		try {
			ensureTarget(mc, main.width, main.height);
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
					main.getColorTexture(), target.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
			chain.process(target, pool);
			pool.endFrame();
		} catch (Throwable t) {
			// Swallowed rather than allowed to kill the GUI: a chip without its blur is a cosmetic
			// loss, and a throw out of here takes every module on screen with it.
			say("the blur pass failed on this renderer (" + t.getClass().getSimpleName()
					+ ": " + t.getMessage() + ") — chips will draw flat");
			return;
		}

		// The radius is not passed to process() — vanilla does not pass one either. It rides the
		// GlobalSettingsUniform, which is filled once a frame from getMenuBackgroundBlurriness(),
		// which OptionsMixin substitutes. Reported here because a radius of 0 makes this whole pass
		// an exact copy: the chip then shows the world behind it sharp, which reads as "no blur".
		Integer radius = blurOverride();
		say("blur ready at " + main.width + "x" + main.height + ", vFlip=" + V_FLIP
				+ ", radius=" + (radius == null ? "vanilla's own" : radius));
	}

	/** Says something about the blur once, and only when it is not what was said last. */
	private static void say(String what) {
		if (what.equals(said)) {
			return;
		}
		said = what;
		ClientifyClient.LOGGER.info("Chip blur: {}", what);
	}

	private static void ensureTarget(Minecraft mc, int width, int height) {
		if (pool == null) {
			pool = new CrossFrameResourcePool(3);
		}
		if (target == null || target.width != width || target.height != height) {
			if (target != null) {
				target.destroyBuffers();
			}
			target = new TextureTarget("Clientify blur backdrop", width, height,
					com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, null);
			if (wrapper == null) {
				wrapper = new WrapTexture();
				mc.getTextureManager().register(TEXTURE_ID, wrapper);
			}
			wrapper.bind(target.getColorTexture(), target.getColorTextureView());
		}
	}

	/** Exposes the target's color texture to GuiGraphicsExtractor.blit without owning it. */
	private static final class WrapTexture extends AbstractTexture {
		void bind(GpuTexture gpuTexture, GpuTextureView view) {
			this.texture = gpuTexture;
			this.textureView = view;
		}

		@Override
		public void close() {
			// Detach only — the TextureTarget owns and destroys the actual GPU texture.
			this.texture = null;
			this.textureView = null;
		}
	}
}
