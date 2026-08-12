package dev.clientify.client.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.clientify.client.ClientifyClient;
import dev.clientify.mixin.client.SpriteContentsAccessor;
import dev.clientify.mixin.client.TextureAtlasInvoker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Rewrites block textures on the atlas — the pixels themselves, not something drawn over them.
 *
 * <p>Everything built on this comes out as part of the block: lit with it, hidden behind walls with
 * it, fogged and mipmapped with it, because that is what it now is. It also costs nothing per
 * frame; the work happens when a setting changes, not when a block is on screen.
 *
 * <p>Each texture is described by an {@link Op}: a {@link Paint} that rewrites one frame, and a
 * signature that changes whenever the paint would come out differently. {@link #sync} compares the
 * signatures against what is on the atlas and touches only the difference, so it can be called
 * every tick. The pristine pixels are kept so a texture can be handed back without a resource
 * reload, and the sprite is watched so a resource pack swap gets everything painted on again.
 */
public final class BlockTextures {
	/**
	 * Rewrites ONE frame of a sprite, in place.
	 *
	 * <p>Frames matter because an animated texture (fire, for one) is stacked into a single tall
	 * image: painting it as one picture would smear each frame's change across its neighbours.
	 */
	@FunctionalInterface
	public interface Paint {
		void apply(NativeImage image, int x0, int y0, int w, int h);
	}

	/** What a texture should look like, and a string that differs whenever that changes. */
	public record Op(String signature, Paint paint) {
	}

	/** Pristine level-0 pixels, by texture id. The smaller levels are rebuilt from these. */
	private static final Map<Identifier, int[]> ORIGINALS = new HashMap<>();
	/** The signature currently on the atlas, by texture id. */
	private static final Map<Identifier, String> APPLIED = new HashMap<>();
	/** The sprite each texture was painted on, so a rebuilt atlas can be spotted. */
	private static final Map<Identifier, TextureAtlasSprite> PAINTED = new HashMap<>();
	/** Ids that are not on the atlas at all, so the warning is said once and not every tick. */
	private static final Set<Identifier> UNKNOWN = new HashSet<>();

	/** Asked what a texture should look like while it is loading, before an atlas exists. */
	private static java.util.function.Supplier<Map<Identifier, Op>> provider;

	public static void provider(java.util.function.Supplier<Map<Identifier, Op>> supplier) {
		provider = supplier;
	}

	private BlockTextures() {
	}

	/**
	 * Paints a texture as it loads, if it is one we want painted.
	 *
	 * <p>Everything the module wants, not only the animated ones. Repainting a live atlas works
	 * on vanilla and reaches nothing on Sodium — glass stayed vanilla there however correct the
	 * sprite was in memory, which a screenshot of Sodium's own frame is what finally showed.
	 * Painted before the atlas is built, the pixels are simply what gets uploaded, and every
	 * renderer sees them.
	 */
	public static boolean paintAtLoad(Identifier name, NativeImage image, int frameW, int frameH) {
		if (provider == null || image == null || frameW <= 0 || frameH <= 0) {
			return false;
		}
		Op op;
		try {
			op = provider.get().get(name);
		} catch (RuntimeException e) {
			return false;
		}
		if (op == null) {
			return false;
		}
		// Taken BEFORE painting. The live path restores from this when a value changes, and a
		// snapshot taken afterwards would restore to the painted state rather than to vanilla.
		ORIGINALS.computeIfAbsent(name, k -> snapshot(image));
		for (int y = 0; y + frameH <= image.getHeight(); y += frameH) {
			for (int x = 0; x + frameW <= image.getWidth(); x += frameW) {
				op.paint().apply(image, x, y, frameW, frameH);
			}
		}
		APPLIED.put(name, op.signature());
		return true;
	}

	// ---- paints ----

	/** A frame of solid colour, {@code thickness} pixels deep — the ore outlines. */
	public static Paint border(int argb, int thickness) {
		return (image, x0, y0, w, h) -> {
			int t = Math.max(1, Math.min(Math.min(w, h) / 2, thickness));
			forEach(image, x0, y0, w, h, (x, y, px) ->
					onEdge(x, y, w, h, t) ? argb : px);
		};
	}

	/**
	 * Glass: every pixel's alpha scaled, with the outer ring optionally left alone or recoloured.
	 *
	 * <p>Alpha rather than a straight cut because glass sits on the translucent chunk layer in this
	 * version, so a half-transparent pixel really is drawn half-transparent — the pane can be faded
	 * to any degree instead of being either vanilla or gone.
	 */
	public static Paint glass(float alphaScale, boolean keepEdge, Integer edgeColour, int edgeThickness,
			float edgeAlpha) {
		return (image, x0, y0, w, h) -> {
			// Thickness is in vanillas sixteen pixel tiles. On a higher resolution pack the same
			// count of pixels is a quarter of the frame it draws on a sixteen, so scale it to the
			// texture and the frame stays the same width whatever pack is loaded.
			int scaled = Math.round(edgeThickness * Math.max(w, h) / 16f);
			int t = Math.max(1, Math.min(Math.min(w, h) / 2, scaled));
			forEach(image, x0, y0, w, h, (x, y, px) -> {
				if (keepEdge && onEdge(x, y, w, h, t)) {
					int edge = edgeColour == null ? px : edgeColour;
					return scaleAlpha(edge, edgeAlpha);
				}
				return scaleAlpha(px, alphaScale);
			});
		};
	}

	/**
	 * The colour swapped and the shape kept — for string, where the shape is the whole point.
	 *
	 * <p>The chosen alpha now scales what was there rather than being ignored: keeping the
	 * original alpha outright meant the picker's transparency did nothing, since vanilla's
	 * string is what decides which pixels exist at all.
	 *
	 * <p>Bold widens the line by a pixel in every direction, taken from a copy of the original
	 * mask — grown in place, one pass would feed on its own output and swallow the frame.
	 */
	public static Paint recolour(int argb, boolean bold) {
		float alphaScale = ((argb >>> 24) & 0xFF) / 255f;
		return (image, x0, y0, w, h) -> {
			boolean[] drawn = new boolean[w * h];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					drawn[y * w + x] = ((image.getPixel(x0 + x, y0 + y) >>> 24) & 0xFF) > 0;
				}
			}
			int flat = Math.max(0, Math.min(255, Math.round(255 * alphaScale)));
			forEach(image, x0, y0, w, h, (x, y, px) -> {
				boolean here = ((px >>> 24) & 0xFF) > 0;
				if (!here && (!bold || !nextToDrawn(drawn, w, h, x, y, 2))) {
					return px;
				}
				// One alpha for the whole line. Vanillas string fades at its edges, and a line
				// solid in places and see-through in others does not read as one line at all.
				return (flat << 24) | (argb & 0xFFFFFF);
			});
		};
	}

	/** Within {@code reach} pixels of something drawn: two makes a one pixel line four across. */
	private static boolean nextToDrawn(boolean[] drawn, int w, int h, int x, int y, int reach) {
		for (int dy = -reach; dy <= reach; dy++) {
			for (int dx = -reach; dx <= reach; dx++) {
				int nx = x + dx;
				int ny = y + dy;
				if (nx >= 0 && ny >= 0 && nx < w && ny < h && drawn[ny * w + nx]) {
					return true;
				}
			}
		}
		return false;
	}

	/** Nothing left to draw. Cutout blocks discard a zero-alpha pixel outright. */
	public static Paint hide() {
		return (image, x0, y0, w, h) ->
				forEach(image, x0, y0, w, h, (x, y, px) -> px & 0xFFFFFF);
	}

	/**
	 * Only the bottom {@code keep} of every frame survives — a shorter flame.
	 *
	 * <p>Fire is baked into the chunk mesh, so its quads cannot be shrunk per frame from outside;
	 * clearing the top of the texture leaves the same quad drawing less of itself, which is the
	 * same picture and costs nothing.
	 */
	public static Paint cropTop(float keep) {
		return (image, x0, y0, w, h) -> {
			int cut = Math.round(h * (1f - Math.min(1f, Math.max(0f, keep))));
			forEach(image, x0, y0, w, h, (x, y, px) -> y < cut ? (px & 0xFFFFFF) : px);
		};
	}

	// ---- applying ----

	/**
	 * Brings the atlas in line with {@code wanted}: paints what is new or changed, hands back what
	 * is no longer asked for, and repaints anything a resource reload took with it.
	 */
	public static void sync(Minecraft mc, Map<Identifier, Op> wanted) {
		if (wanted.isEmpty() && APPLIED.isEmpty()) {
			// Nothing painted and nothing asked for, which is every tick for anyone not using
			// these settings. Leaving early skips a texture lookup and a copy of the key set.
			return;
		}
		TextureAtlas atlas = blockAtlas(mc);
		if (atlas == null || RenderSystem.getDevice() == null) {
			return;
		}
		boolean changed = false;
		for (Identifier id : List.copyOf(APPLIED.keySet())) {
			Op op = wanted.get(id);
			if (!PAINTED.containsKey(id)) {
				// Painted while loading, before there was an atlas to record it against.
				PAINTED.put(id, atlas.getSprite(id));
				continue;
			}
			boolean reloaded = PAINTED.get(id) != atlas.getSprite(id);
			if (reloaded) {
				// The pixels we were holding belong to an atlas that no longer exists; the new
				// sprite is untouched, so forget everything and let the paint below start over.
				ORIGINALS.remove(id);
				APPLIED.remove(id);
				PAINTED.remove(id);
			} else if (op == null || !op.signature().equals(APPLIED.get(id))) {
				changed |= restore(atlas, id);
			}
		}
		for (Map.Entry<Identifier, Op> entry : wanted.entrySet()) {
			if (!APPLIED.containsKey(entry.getKey())) {
				changed |= apply(atlas, entry.getKey(), entry.getValue());
			}
		}
		if (changed) {
			// One rebuild for the whole batch rather than one per texture.
			flush(atlas);
		}
	}

	/** Hands every painted texture back and forgets them — for switching a feature off. */
	public static void restoreAll(Minecraft mc) {
		TextureAtlas atlas = blockAtlas(mc);
		if (atlas == null) {
			return;
		}
		boolean changed = false;
		for (Identifier id : List.copyOf(APPLIED.keySet())) {
			changed |= restore(atlas, id);
		}
		if (changed) {
			flush(atlas);
		}
	}

	private static boolean apply(TextureAtlas atlas, Identifier id, Op op) {
		TextureAtlasSprite sprite = atlas.getSprite(id);
		if (sprite == null || !(sprite.contents() instanceof SpriteContentsAccessor access)) {
			return false;
		}
		SpriteContents contents = sprite.contents();
		// An id that is not on this atlas comes back as the missing-texture sprite, and painting
		// THAT would put our colours on every unknown block in the game. Say so once: a texture
		// named wrongly here fails silently otherwise, which is how ancient debris went unpainted
		// for three rounds while every other ore worked.
		if (!id.equals(contents.name())) {
			if (UNKNOWN.add(id)) {
				ClientifyClient.LOGGER.warn("No sprite named {} on the block atlas; not painting it", id);
			}
			return false;
		}
		NativeImage[] mips = access.clientify$mips();
		if (mips == null || mips.length == 0 || mips[0] == null) {
			return false;
		}
		NativeImage full = mips[0];
		// A snapshot from a different pack is worse than none: it is the wrong size and the wrong
		// picture. Take a fresh one when the texture underneath has changed shape.
		// A snapshot from a different pack is worse than none: wrong size, wrong picture.
		int[] kept = ORIGINALS.get(id);
		if (kept == null || kept.length != full.getWidth() * full.getHeight()) {
			ORIGINALS.put(id, snapshot(full));
		} else {
			// Painting on top of a previous paint compounds it -- a fade applied twice is twice as
			// faint. Starting from the pristine pixels every time makes a repaint mean the same
			// thing whether it is the first or the fiftieth, so a colour change lands at once
			// instead of waiting for the textures to be loaded again.
			for (int y = 0, i = 0; y < full.getHeight(); y++) {
				for (int x = 0; x < full.getWidth(); x++, i++) {
					full.setPixel(x, y, kept[i]);
				}
			}
		}
		int fw = contents.width();
		int fh = contents.height();
		for (int y = 0; y + fh <= full.getHeight(); y += fh) {
			for (int x = 0; x + fw <= full.getWidth(); x += fw) {
				op.paint().apply(full, x, y, fw, fh);
			}
		}
		remip(access, contents.name(), mips);
		APPLIED.put(id, op.signature());
		PAINTED.put(id, sprite);
		return true;
	}

	private static boolean restore(TextureAtlas atlas, Identifier id) {
		TextureAtlasSprite sprite = atlas.getSprite(id);
		int[] original = ORIGINALS.remove(id);
		APPLIED.remove(id);
		PAINTED.remove(id);
		if (original == null || sprite == null
				|| !(sprite.contents() instanceof SpriteContentsAccessor access)) {
			return false;
		}
		NativeImage[] mips = access.clientify$mips();
		if (mips == null || mips.length == 0 || mips[0] == null) {
			return false;
		}
		NativeImage full = mips[0];
		if (original.length != full.getWidth() * full.getHeight()) {
			// A pack swap can replace a texture with one of another size -- an animated ore is a
			// tall strip of frames, not a single tile. The pixels we kept belong to the texture
			// that is gone, and walking the new one through them ran off the end and crashed.
			return false;
		}
		for (int y = 0, i = 0; y < full.getHeight(); y++) {
			for (int x = 0; x < full.getWidth(); x++, i++) {
				full.setPixel(x, y, original[i]);
			}
		}
		remip(access, sprite.contents().name(), mips);
		return true;
	}

	/**
	 * Rebuilds the smaller mip levels from the painted one, through vanilla's own generator.
	 *
	 * <p>Averaging them by hand is close but not the same: the generator scales alpha to hold a
	 * cutout texture's coverage as it shrinks, honours the sprite's {@code mipmap_strategy}, and
	 * bleeds colour into transparent pixels so the distance never picks up dark fringes. Painting
	 * each level directly, as this first did, turns a two pixel border into the entire texture by
	 * the 2x2 level and blocks become slabs of colour at a distance.
	 */
	private static void remip(SpriteContentsAccessor access, Identifier name, NativeImage[] mips) {
		if (mips.length <= 1) {
			return;
		}
		// MEAN rather than the sprites own strategy. A cutout strategy scales alpha to hold the
		// textures coverage as it shrinks, which for a texture we have just made half transparent
		// means snapping it either side of a half: glass vanished below 0.50 and was solid above
		// it, with nothing in between. Averaging keeps what was painted.
		NativeImage[] fresh = MipmapGenerator.generateMipLevels(
				name, new NativeImage[] {mips[0]}, mips.length - 1,
				net.minecraft.client.renderer.texture.MipmapStrategy.MEAN,
				access.clientify$alphaCutoffBias());
		if (fresh == null || fresh.length == 0 || fresh[0] != mips[0]) {
			// Not what this expects; leave the old chain in place rather than leak or free the
			// image the atlas is about to read.
			return;
		}
		List<NativeImage> stale = new ArrayList<>();
		for (int level = 1; level < mips.length; level++) {
			NativeImage old = mips[level];
			if (old == null) {
				continue;
			}
			boolean reused = false;
			for (NativeImage kept : fresh) {
				reused |= kept == old;
			}
			if (!reused) {
				stale.add(old);
			}
		}
		access.clientify$setMips(fresh);
		for (NativeImage old : stale) {
			old.close();
		}
	}

	/**
	 * Asks the atlas to lay its sprites out again, now that some of their pixels have changed.
	 *
	 * <p>Writing into the atlas directly at a sprite's x and y is the obvious move and the wrong
	 * one here: this version composes the atlas by RENDERING each sprite into it, so those
	 * coordinates are not a destination a direct write can trust, and one ore's frame landed across
	 * another ore's texture. Vanilla's own compose step knows where everything goes.
	 */
	private static void flush(TextureAtlas atlas) {
		if (atlas instanceof TextureAtlasInvoker invoker) {
			try {
				invoker.clientify$rebuildContents();
			} catch (Throwable t) {
				ClientifyClient.LOGGER.warn("Could not rebuild the block atlas", t);
			}
		}
	}

	// ---- helpers ----

	/** The block texture ids for a block — vanilla ores name theirs after the block itself. */
	public static List<Identifier> texturesOf(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		List<Identifier> out = new ArrayList<>(1);
		out.add(Identifier.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath()));
		return out;
	}

	/** A block texture id by name, for the ones that are not named after a block. */
	public static Identifier block(String path) {
		return Identifier.withDefaultNamespace("block/" + path);
	}

	/**
	 * The block atlas, or null while there is nothing on it to paint.
	 *
	 * <p>The emptiness check is the whole point: the atlas object exists long before it is stitched,
	 * and asking a bare one for a sprite throws rather than returning nothing.
	 */
	private static TextureAtlas blockAtlas(Minecraft mc) {
		if (!(mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS) instanceof TextureAtlas atlas)) {
			return null;
		}
		if (atlas instanceof TextureAtlasInvoker invoker) {
			Map<Identifier, TextureAtlasSprite> sprites = invoker.clientify$sprites();
			return sprites == null || sprites.isEmpty() ? null : atlas;
		}
		return atlas;
	}

	private static int scaleAlpha(int argb, float scale) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * scale);
		return (Math.min(255, Math.max(0, alpha)) << 24) | (argb & 0xFFFFFF);
	}

	private static boolean onEdge(int x, int y, int w, int h, int thickness) {
		return x < thickness || y < thickness || x >= w - thickness || y >= h - thickness;
	}

	@FunctionalInterface
	private interface PixelOp {
		/** The new colour for the pixel at frame-local {@code x, y}, currently {@code argb}. */
		int apply(int x, int y, int argb);
	}

	private static void forEach(NativeImage image, int x0, int y0, int w, int h, PixelOp op) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int px = image.getPixel(x0 + x, y0 + y);
				int out = op.apply(x, y, px);
				if (out != px) {
					image.setPixel(x0 + x, y0 + y, out);
				}
			}
		}
	}

	private static int[] snapshot(NativeImage image) {
		int[] pixels = new int[image.getWidth() * image.getHeight()];
		for (int y = 0, i = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++, i++) {
				pixels[i] = image.getPixel(x, y);
			}
		}
		return pixels;
	}
}
