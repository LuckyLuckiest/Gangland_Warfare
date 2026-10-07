package org.luckyraven.gangland.copsncrooks.npc.police.config;

/**
 * Where a wanted level's cops come from ({@code cops.yml}'s {@code Cops.Dispatch}): units leave the nearest station
 * and arrive after an ETA that grows with the distance.
 *
 * @param enabled           {@code false} keeps the 0.15 instant ring spawn.
 * @param unitSpeed         blocks a unit covers per second on its way.
 * @param minEtaSeconds     shortest ETA.
 * @param maxEtaSeconds     longest ETA.
 * @param stationRadius     spawners within this many blocks of a station belong to it.
 * @param rejoinGraceSeconds how long a player's pending units survive his logout.
 * @since 0.16.0
 */
public record DispatchSettings(boolean enabled, double unitSpeed, int minEtaSeconds, int maxEtaSeconds,
                               double stationRadius, int rejoinGraceSeconds) {

	/** Matches the shipped cops.yml: 10 blocks/s, ETA 0-40 s, station radius 32, rejoin grace 15 s. */
	public static final DispatchSettings DEFAULT = new DispatchSettings(true, 10.0, 0, 40, 32.0, 15);

	/** The DEFAULT numbers with dispatch off (the legacy instant path). */
	public static final DispatchSettings DISABLED = new DispatchSettings(false, DEFAULT.unitSpeed, DEFAULT.minEtaSeconds,
	                                                                     DEFAULT.maxEtaSeconds, DEFAULT.stationRadius,
	                                                                     DEFAULT.rejoinGraceSeconds);

	/** The ETA in milliseconds for a horizontal {@code distance}: clamped to Min..Max seconds, rounded up to a whole second. */
	public long etaMs(double distance) {
		double seconds = unitSpeed > 0 ? distance / unitSpeed : maxEtaSeconds;
		long   whole   = (long) Math.ceil(Math.max(0.0, seconds));
		return Math.max(minEtaSeconds, Math.min(maxEtaSeconds, whole)) * 1000L;
	}
}
