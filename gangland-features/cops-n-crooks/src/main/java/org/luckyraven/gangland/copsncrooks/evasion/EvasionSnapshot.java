package org.luckyraven.gangland.copsncrooks.evasion;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only picture of one player's evasion state, for the HUD (0.12 F3) and anyone listening to
 * {@code EvasionStateChangeEvent}.
 *
 * @param state            the current state
 * @param level            the wanted level the snapshot was taken at
 * @param remainingSeconds unseen seconds left before the next drop (equals {@code totalSeconds} unless SEARCHING)
 * @param totalSeconds     unseen seconds a drop takes at {@code level}
 * @param zoneCenter       the search zone center while SEARCHING, else {@code null}
 * @param zoneRadius       the search zone radius in blocks while SEARCHING, else {@code 0}
 */
public record EvasionSnapshot(EvasionState state, int level, double remainingSeconds, double totalSeconds,
                              @Nullable Location zoneCenter, double zoneRadius) {

	private static final EvasionSnapshot NONE = new EvasionSnapshot(EvasionState.NONE, 0, 0D, 0D, null, 0D);

	/**
	 * The snapshot of a player evasion does not drive.
	 */
	public static EvasionSnapshot none() {
		return NONE;
	}
}
