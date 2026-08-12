package dev.clientify.client.config;

import dev.clientify.client.config.ModuleSettings.ColorSpec;

/**
 * Menu theme, saved once (not per-profile): panel/accent colors — each a full ColorSpec so
 * the menu itself can run chroma/gradient effects — the blur toggles/strengths, and the
 * module-defaults template (SETTINGS → Module Defaults → APPLY TO ALL). Alpha bytes carry
 * the translucency (#AARRGGBB).
 */
public class GlobalSettings {
	public ColorSpec accentColor = new ColorSpec("#80F35D12");
	public ColorSpec panelColor = new ColorSpec("#57000000");
	public boolean menuBlur = false;
	/** 1–10; independent of the vanilla accessibility option (OptionsMixin substitutes it). */
	public int menuBlurStrength = 6;
	/** 1–10; radius for module background blur (chips with bgBlur on). */
	public int moduleBlurStrength = 6;
	/** Editor: snap a dragged module's centre to the screen centre lines. */
	public boolean editorSnap = true;
	/** Switch profiles automatically to whichever one is bound to the server you join. */
	public boolean profilePerServer = false;
	/** Server address (or "singleplayer") → profile name. Root level, like the profiles are. */
	public java.util.Map<String, String> serverProfiles = new java.util.LinkedHashMap<>();
	/** Draw each module background only where no other one already is, so overlaps don't blend. */
	public boolean flatBackgrounds = false;

	/** Appearance template edited in SETTINGS; APPLY TO ALL copies it onto every module. */
	public ModuleSettings moduleDefaults = new ModuleSettings();
}
