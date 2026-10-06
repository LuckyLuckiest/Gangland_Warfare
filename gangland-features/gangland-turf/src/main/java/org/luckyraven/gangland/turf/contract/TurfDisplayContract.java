package org.luckyraven.gangland.turf.contract;

/**
 * Visual-presentation toggles for turf crossings. Implemented in the turf module, sourced from {@code turf/turf_settings.yml} so
 * the listener does not read the settings file directly.
 */
public interface TurfDisplayContract {

	/**
	 * Whether the big {@code Player#sendTitle} flash fires when a player enters a turf
	 * ({@code turf_settings.yml: Show_Enter_Title}). The action-bar announcement is sent regardless and is unaffected
	 * by this toggle.
	 */
	boolean isEnterTitleEnabled();
}
