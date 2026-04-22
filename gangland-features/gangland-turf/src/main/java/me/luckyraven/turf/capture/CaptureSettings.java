package me.luckyraven.turf.capture;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Tuning knobs for the capture loop — passed in from impl's settings binder so the capture code doesn't read config
 * directly.
 */
@Getter
@RequiredArgsConstructor
public final class CaptureSettings {

	/**
	 * Base time (seconds) for a single attacker to fill the capture bar from 0 to 100 with no defenders.
	 */
	private final int   captureDurationSeconds;
	/**
	 * Cooldown window after a capture completes, during which the turf cannot be contested again.
	 */
	private final int   cooldownMinutes;
	/**
	 * After a challenger leaves the turf, how many seconds of grace before the contest cancels as ABANDONED.
	 */
	private final int   abandonGraceSeconds;
	/**
	 * Gang offline grace — turfs are protected for this long after the last gang member logs off.
	 */
	private final int   postLogoffProtectionMinutes;
	/**
	 * Gangs idle longer than this auto-release every turf they own.
	 */
	private final int   inactivityAutoReleaseDays;
	/**
	 * Progress levels (0..100) that fire TurfCaptureProgressEvent on upward crossings.
	 */
	private final int[] progressMilestones;
}
