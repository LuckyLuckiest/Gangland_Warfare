package org.luckyraven.gangland.copsncrooks.npc;

import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * A single step in a proactive navigation plan. Each step is either a plain destination waypoint
 * ({@link NavObstacle#NONE}) or an obstacle that requires a special action before the NPC can proceed (opening a door,
 * climbing a ladder).
 */
final class NavStep {

	final NavObstacle obstacle;
	final Block       obstacleBlock;
	final boolean     descending;    // only meaningful for CLIMB_LADDER
	Location waypoint;               // mutable — live destination tracking updates the final NONE step

	NavStep(Location waypoint, NavObstacle obstacle, Block obstacleBlock) {
		this(waypoint, obstacle, obstacleBlock, false);
	}

	NavStep(Location waypoint, NavObstacle obstacle, Block obstacleBlock, boolean descending) {
		this.waypoint      = waypoint;
		this.obstacle      = obstacle;
		this.obstacleBlock = obstacleBlock;
		this.descending    = descending;
	}
}
