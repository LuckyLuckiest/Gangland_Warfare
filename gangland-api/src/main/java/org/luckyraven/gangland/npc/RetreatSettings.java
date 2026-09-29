package org.luckyraven.gangland.npc;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * When a badly hurt NPC breaks off to cover ({@link AbstractNpc#takeCover}) instead of pursuing: Gangland's own
 * threshold on top of Keystone's retreat primitive, which leaves "when" to the caller.
 *
 * @param enabled        {@code false} never retreats.
 * @param healthFraction retreat at or below this fraction of max health (0-1).
 * @param radius         blocks searched for a spot the threat cannot see (passed to {@code takeCover}).
 * @since 1.13.0
 */
public record RetreatSettings(boolean enabled, double healthFraction, double radius) {

	/** Enabled, at 30% health, cover searched within 12 blocks — matches the shipped cops.yml / civilians.yml. */
	public static final RetreatSettings DEFAULT = new RetreatSettings(true, 0.3, 12.0);

	/**
	 * Reads {@code Enabled}, {@code Health_Fraction} [0,1] and {@code Radius} [2,32] from {@code node} over
	 * {@code defaults} key by key; out-of-range values are clamped and reported at the key's line.
	 */
	public static RetreatSettings read(@Nullable NodeReader node, ConfigReport report, RetreatSettings defaults) {
		if (node == null) return defaults;
		return new RetreatSettings(node.get("Enabled").asBool().orDefault(defaults.enabled()),
		                           TacticsConfig.readClampedDouble(node, report, "Health_Fraction",
		                                                           defaults.healthFraction(), 0, 1),
		                           TacticsConfig.readClampedDouble(node, report, "Radius", defaults.radius(), 2,
		                                                           32));
	}

	/** Whether an NPC at {@code health} of {@code maxHealth} should be in cover now. */
	public boolean shouldRetreat(double health, double maxHealth) {
		return enabled && maxHealth > 0 && health <= maxHealth * healthFraction;
	}
}
