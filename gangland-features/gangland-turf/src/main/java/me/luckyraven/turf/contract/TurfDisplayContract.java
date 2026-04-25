package me.luckyraven.turf.contract;

/**
 * Visual-presentation toggles for turf crossings. Implemented in gangland-impl, sourced from {@code settings.yml} so
 * the turf module does not import the impl-side {@code Settings} reader directly.
 */
public interface TurfDisplayContract {

	/**
	 * Whether the big {@code Player#sendTitle} flash fires when a player enters a turf
	 * ({@code settings.yml: Turf.Show_Enter_Title}). The action-bar announcement is sent regardless and is unaffected
	 * by this toggle.
	 */
	boolean isEnterTitleEnabled();
}
