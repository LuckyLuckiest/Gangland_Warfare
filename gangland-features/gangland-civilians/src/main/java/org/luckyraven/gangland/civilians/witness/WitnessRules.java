package org.luckyraven.gangland.civilians.witness;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * {@code Witness} block of {@code npc/civilians.yml}: how far a civilian sees a crime and how a 911 call runs.
 *
 * @since 0.20.0
 */
public record WitnessRules(boolean enabled, double range, int callSeconds, boolean callerGlow, double scareRange,
                           double scareAngle, boolean reportAssault, int assaultRepeatSeconds) {

	/**
	 * TDD stub: neutral values. The shipped defaults (true, 24.0, 8, true, 16.0, 12.0, true, 10) come with the green
	 * chunk.
	 */
	public static final WitnessRules DEFAULT = new WitnessRules(false, 0.0, 0, false, 0.0, 0.0, false, 0);

	/** Reads the {@code Witness} block of civilians.yml; a null reader, missing block or missing key is its default. */
	public static WitnessRules parse(@Nullable NodeReader root) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
