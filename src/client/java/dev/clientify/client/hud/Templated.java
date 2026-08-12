package dev.clientify.client.hud;

/** A module whose HUD text is a user-editable template with a live placeholder. */
public interface Templated {
	String template();

	void setTemplate(String template);

	/** The placeholder token, e.g. "%fps%" — shown as a hint in the settings screen. */
	String placeholder();

	/** The out-of-the-box template, used by the reset button. */
	String defaultTemplate();
}
