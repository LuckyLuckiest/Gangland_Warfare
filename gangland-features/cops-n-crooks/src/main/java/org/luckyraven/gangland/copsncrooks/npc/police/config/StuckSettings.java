package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.AbstractNpc;
import org.luckyraven.keystone.persistence.config.NodeReader;

/**
 * When a cop that cannot reach its suspect ({@link AbstractNpc#millisUnreachable()}) is taken off the map and sent
 * again from a better spot ({@code Cops.Stuck}). A cop the suspect is looking at is never recycled.
 *
 * @param enabled             {@code false} never recycles.
 * @param recycleSeconds      seconds stranded before the cop is recycled.
 * @param viewDistance        blocks within which a cop in the suspect's view cone and line of sight counts as seen.
 * @param avoidSpawnerSeconds seconds the spawner a recycled cop came from is skipped for its replacements.
 * @since 0.13.0
 */
public record StuckSettings(boolean enabled, int recycleSeconds, double viewDistance, int avoidSpawnerSeconds) {

	/** Enabled, after 12 s stranded, seen within 24 blocks, spawner skipped for 60 s — matches the shipped cops.yml. */
	public static final StuckSettings DEFAULT = new StuckSettings(true, 12, 24.0, 60);

	/**
	 * Reads {@code Enabled}, {@code Recycle_Seconds} (at least 1), {@code View_Distance} [0,128] and
	 * {@code Avoid_Spawner_Seconds} (at least 0) from {@code node} over {@link #DEFAULT} key by key.
	 */
	public static StuckSettings read(@Nullable NodeReader node) {
		if (node == null) return DEFAULT;
		return new StuckSettings(node.get("Enabled").asBool().orDefault(DEFAULT.enabled()),
		                         node.get("Recycle_Seconds").asInt().min(1).orDefault(DEFAULT.recycleSeconds()),
		                         node.get("View_Distance").asDouble().min(0).max(128)
		                             .orDefault(DEFAULT.viewDistance()),
		                         node.get("Avoid_Spawner_Seconds").asInt().min(0)
		                             .orDefault(DEFAULT.avoidSpawnerSeconds()));
	}
}
