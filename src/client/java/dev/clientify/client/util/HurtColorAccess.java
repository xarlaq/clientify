package dev.clientify.client.util;

/** Duck interface added to OverlayTexture by OverlayTextureMixin. */
public interface HurtColorAccess {
	/** Vanilla's hurt-flash color (rows 0-7 of the 16x16 overlay texture). */
	int VANILLA_HURT_COLOR = 0xB2FF0000;

	void clientify$setHurtColor(int argb);
}
