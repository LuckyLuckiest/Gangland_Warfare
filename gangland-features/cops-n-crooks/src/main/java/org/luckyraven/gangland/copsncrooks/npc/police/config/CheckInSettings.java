package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * Radio check-ins and silent officers ({@code cops.yml}'s {@code Cops.Check_In} block, 0.21.2): a cop killed where
 * nobody saw it says nothing; dispatch asks for him {@code checkInSeconds} after his last check-in and sends units to
 * that spot {@code silentSeconds} after it.
 *
 * @param enabled         {@code false}: a dead cop's squad radios at once (pre-0.21.2). The silent path also needs
 *                        {@code Kill_Cop} in {@code wanted.yml}'s {@code Witnesses.Crimes}.
 * @param checkInSeconds  a cop on patrol checks in this often; after the same time dispatch says {@code Radio_Check}.
 * @param silentSeconds   after this long since his last check-in dispatch says {@code Officer_Missing}.
 * @param sceneRadius     the killer seen by any cop within this many blocks of the scene: the kill counts.
 * @param sceneSeconds    how long the scene stays open.
 * @param pursuitPresumed the dead cop's group was hunting the killer: the kill counts at {@code Officer_Missing}.
 * @param showTimer       the killer sees the countdown to {@code Officer_Missing} on his action bar.
 * @since 0.21.2
 */
public record CheckInSettings(boolean enabled, int checkInSeconds, int silentSeconds, double sceneRadius,
                              int sceneSeconds, boolean pursuitPresumed, boolean showTimer) {

	/** Equals the shipped cops.yml (PLAN.md owner decisions 2, 6 and 8). */
	public static final CheckInSettings DEFAULT = new CheckInSettings(true, 10, 20, 32.0, 60, true, true);
}
