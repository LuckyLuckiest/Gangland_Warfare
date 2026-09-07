package org.luckyraven.gangland.copsncrooks.npc;

/**
 * Provides navigation and pathfinding configuration values shared by all gangland NPC types.
 * <p>
 * Implementations are backed by the {@code NPC_Navigation} section of {@code settings.yml}, ensuring cops and civilians
 * use identical pathfinding tuning unless overridden.
 */
public interface NpcNavigationConfig {

	/**
	 * Returns the tick rate at which AI should be evaluated.
	 */
	int getAiTickRate();

	/**
	 * Ticks between navigation path recalculations.
	 */
	int getNavigationRecalculationTicks();

	/**
	 * AI ticks between movement-progress samples for stuck detection.
	 */
	int getStuckCheckIntervalTicks();

	/**
	 * Consecutive stuck samples before navigation is considered stuck.
	 */
	int getMaxStuckChecks();

	/**
	 * Consecutive stuck samples before navigation is considered permanently hopeless.
	 */
	int getMaxHopelessStuckChecks();

	/**
	 * Distance threshold below which a hopeless NPC can still reach the target directly (blocks).
	 */
	double getHopelessCloseThreshold();

	/**
	 * Minimum distance the NPC must travel between samples to count as progress (blocks).
	 */
	double getMinProgressDistance();

	/**
	 * Minimum distance from the target for a ranged NPC to hold position (blocks).
	 */
	double getRangedMinDistance();

	/**
	 * Maximum distance from the target for a ranged NPC to hold position (blocks).
	 */
	double getRangedMaxDistance();

	/**
	 * Minimum number of AI ticks that must elapse from the last path request before a path-loss recovery re-path is
	 * allowed.
	 */
	int getMinRepathAfterLossTicks();
}
