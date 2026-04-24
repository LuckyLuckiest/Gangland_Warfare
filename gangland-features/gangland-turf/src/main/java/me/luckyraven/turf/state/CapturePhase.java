package me.luckyraven.turf.state;

/**
 * Sub-state of {@link TurfState#CONTESTING} for unclaimed turfs only. Owned-turf captures run as a single phase and
 * ignore this value.
 *
 * <ul>
 *   <li>{@link #CLAIM} — progress fills 0→100 and the turf becomes "potentially" the capturing gang's. Rolling back
 *       to 0 releases the turf (or transfers to the dominant opposer at Phase 2, see
 *       {@code CaptureService.tickContestingUnclaimed}).</li>
 *   <li>{@link #CONSOLIDATE} — progress fills 0→100 to complete the capture. Rolling back to 0 reverts to
 *       {@link #CLAIM} at progress=100 with the same capturing gang.</li>
 * </ul>
 */
public enum CapturePhase {
	CLAIM,
	CONSOLIDATE
}
