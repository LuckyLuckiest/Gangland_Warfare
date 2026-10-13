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

	/** The shipped {@code Witness} block. */
	public static final WitnessRules DEFAULT = new WitnessRules(true, 24.0, 8, true, 16.0, 12.0, true, 10);

	/** Reads the {@code Witness} block of civilians.yml; a null reader, missing block or missing key is its default. */
	public static WitnessRules parse(@Nullable NodeReader root) {
		if (root == null) return DEFAULT;

		NodeReader block = root.get("Witness").asMapping().reader();
		if (block == null) return DEFAULT;

		WitnessRules d = DEFAULT;
		return new WitnessRules(
				block.get("Enable").asBool().orDefault(d.enabled()),
				block.get("Range").asDouble().min(0).orDefault(d.range()),
				block.get("Call_Seconds").asInt().min(0).orDefault(d.callSeconds()),
				block.get("Caller_Glow").asBool().orDefault(d.callerGlow()),
				block.get("Scare_Range").asDouble().min(0).orDefault(d.scareRange()),
				block.get("Scare_Angle").asDouble().min(0).orDefault(d.scareAngle()),
				block.get("Report_Assault").asBool().orDefault(d.reportAssault()),
				block.get("Assault_Repeat_Seconds").asInt().min(0).orDefault(d.assaultRepeatSeconds()));
	}
}
