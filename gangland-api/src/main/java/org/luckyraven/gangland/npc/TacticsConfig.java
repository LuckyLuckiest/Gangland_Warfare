package org.luckyraven.gangland.npc;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.NpcEngagement;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;
import org.luckyraven.keystone.persistence.config.Severity;

/**
 * Squad-tactics tuning for one NPC family (cops, gang members, ...): how far its ranged shooters fan out around the
 * target, layered on top of Keystone's own {@link NpcEngagement}. Gangland's own config surface — Keystone itself
 * reads no YAML.
 *
 * @param engagement  how a ranged member works its firing band; {@link NpcEngagement#LEGACY} when {@code Enabled} is
 *                    {@code false} in YAML (the 1.12.0 pursuit positioning: no formation and no
 *                    order/contact/reload/check-fire signals; {@code NpcSquad.memberDown} still signals casualties).
 * @param formationArc degrees the squad's shooters fan out over around the target (0-360), passed to
 *                    {@link NpcSquad#setFormationArc}. Melee members always surround at 360/n regardless of this
 *                    value.
 * @since 1.13.0
 */
public record TacticsConfig(NpcEngagement engagement, double formationArc) {

	private static final long MS_PER_TICK = 50L;

	/** Fan posts enabled ({@link NpcEngagement#DEFAULT}), a 160-degree horseshoe. */
	public static final TacticsConfig DEFAULT = new TacticsConfig(NpcEngagement.DEFAULT, 160.0);

	/**
	 * Reads {@code Enabled}, {@code Formation_Arc}, {@code Strafe_Degrees}, {@code Reposition_Ticks} and
	 * {@code Moving_Aim_Error} from {@code node}, falling back to {@code defaults} key by key when {@code node} is
	 * {@code null} or a key is absent. {@code Enabled: false} returns {@link NpcEngagement#LEGACY} paired with the
	 * read (or defaulted) arc — the arc is always read, even when tactics are disabled. Every value is clamped into
	 * its documented range and reported at the key's line, rather than silently falling back to the default.
	 */
	public static TacticsConfig read(@Nullable NodeReader node, ConfigReport report, TacticsConfig defaults) {
		if (node == null) return defaults;

		boolean enabled = node.get("Enabled").asBool().orDefault(defaults.engagement().enabled());

		double formationArc  = readClampedDouble(node, report, "Formation_Arc", defaults.formationArc(), 0, 360);
		double strafeDegrees = readClampedDouble(node, report, "Strafe_Degrees",
		                                         defaults.engagement().strafeDegrees(), 0, 45);
		int repositionTicks  = readClampedInt(node, report, "Reposition_Ticks",
		                                      (int) (defaults.engagement().repositionMs() / MS_PER_TICK),
		                                      20, Integer.MAX_VALUE);
		double movingAimError = readClampedDouble(node, report, "Moving_Aim_Error",
		                                          defaults.engagement().movingAimError(), 0, 1);

		NpcEngagement engagement = enabled
				? new NpcEngagement(true, strafeDegrees, repositionTicks * MS_PER_TICK, movingAimError)
				: NpcEngagement.LEGACY;

		return new TacticsConfig(engagement, formationArc);
	}

	static double readClampedDouble(NodeReader node, ConfigReport report, String key, double fallback,
	                                        double min, double max) {
		NodeReader.NodeAccess access = node.get(key);
		double                value  = access.asDouble().orDefault(fallback);
		if (value < min || value > max) {
			double clamped = Math.max(min, Math.min(max, value));
			report.add(Severity.WARNING, locOf(node, access), path(node, key),
			           "value " + value + " outside [" + min + ", " + max + "], clamped to " + clamped,
			           "config.range");
			return clamped;
		}
		return value;
	}

	private static int readClampedInt(NodeReader node, ConfigReport report, String key, int fallback, int min,
	                                  int max) {
		NodeReader.NodeAccess access = node.get(key);
		int                   value  = access.asInt().orDefault(fallback);
		if (value < min || value > max) {
			int clamped = Math.max(min, Math.min(max, value));
			report.add(Severity.WARNING, locOf(node, access), path(node, key),
			           "value " + value + " outside [" + min + ", " + max + "], clamped to " + clamped,
			           "config.range");
			return clamped;
		}
		return value;
	}

	private static org.luckyraven.keystone.persistence.config.SourceLocation locOf(NodeReader node,
	                                                                               NodeReader.NodeAccess access) {
		return access.node() != null ? access.node().location() : node.mapping().location();
	}

	private static String path(NodeReader node, String key) {
		String base = node.mapping().path();
		return base == null || base.isEmpty() ? key : base + "." + key;
	}
}
