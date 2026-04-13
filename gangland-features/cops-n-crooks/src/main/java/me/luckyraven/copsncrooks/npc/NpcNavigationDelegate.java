package me.luckyraven.copsncrooks.npc;

import lombok.CustomLog;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encapsulates all navigation, pathfinding, stuck detection, ladder climbing, terrain analysis, and pursuit resolution
 * logic for an {@link AbstractNpc}. Created and owned by AbstractNpc — not a bean.
 */
@CustomLog
final class NpcNavigationDelegate {

	// ── Constants ────────────────────────────────────────────────────────────
	private static final double TARGET_MOVED_THRESHOLD_SQ      = 2.25;
	private static final long   LATERAL_COMMITMENT_MS          = 1000L;
	private static final int    LATERAL_EXPLORATION_DIRECTIONS = 8;
	private static final double HEIGHT_CLIMB_THRESHOLD         = 1.5;
	private static final int    MAX_LADDER_CLIMB_TICKS         = 100;
	private static final double NAV_PLAN_REPLAN_SQ             = 9.0;
	private static final double NAV_STEP_ARRIVE_SQ             = 3.0;
	private static final int    NAV_PLAN_STUCK_LIMIT           = 80;

	// ── Owner ────────────────────────────────────────────────────────────────
	private final AbstractNpc owner;

	// ── Navigation config (immutable, read once from NpcNavigationConfig) ───
	private final int    navigationRecalculationTicks;
	private final int    stuckCheckIntervalTicks;
	private final int    maxStuckChecks;
	private final int    maxHopelessStuckChecks;
	private final double hopelessCloseThreshold;
	private final double minProgressDistanceSquared;
	private final double rangedMinDistance;
	private final double rangedMaxDistance;

	// ── NavPlan ──────────────────────────────────────────────────────────────
	private final List<NavStep> navPlan           = new ArrayList<>();
	private       int           navPlanStep       = 0;
	private       Location      navPlanTarget     = null;
	private       int           navPlanStuckTicks = 0;

	// ── Navigation state ─────────────────────────────────────────────────────
	private Location lastNavigationTarget;
	private Location lastProgressLocation;
	private int      navigationThrottleTicks;
	private int      stuckSampleTicks;
	private int      consecutiveStuckChecks;
	private boolean  navigationHopeless;

	// ── Lateral exploration ──────────────────────────────────────────────────
	private int      lateralExplorationSide;
	private Location committedLateralSpot;
	private long     committedLateralExpiresAtMs;

	// ── Ladder climbing ──────────────────────────────────────────────────────
	private boolean ladderClimbActive;
	private int     ladderClimbDestY;
	private Block   ladderClimbBlock;
	private int     ladderClimbTickCount;
	private boolean ladderClimbDescending;

	NpcNavigationDelegate(AbstractNpc owner, NpcNavigationConfig navConfig) {
		this.owner = owner;

		this.navigationRecalculationTicks = navConfig.getNavigationRecalculationTicks();
		this.stuckCheckIntervalTicks      = navConfig.getStuckCheckIntervalTicks();
		this.maxStuckChecks               = navConfig.getMaxStuckChecks();
		this.maxHopelessStuckChecks       = navConfig.getMaxHopelessStuckChecks();
		this.hopelessCloseThreshold       = navConfig.getHopelessCloseThreshold();

		double minProg = navConfig.getMinProgressDistance();
		this.minProgressDistanceSquared = minProg * minProg;

		this.rangedMinDistance = navConfig.getRangedMinDistance();
		this.rangedMaxDistance = navConfig.getRangedMaxDistance();

		this.navigationThrottleTicks = this.navigationRecalculationTicks;
		this.stuckSampleTicks        = 0;
		this.consecutiveStuckChecks  = 0;

		configurePathfinder();
	}

	// ── Public API (called via AbstractNpc facade) ───────────────────────────

	/**
	 * Navigates the NPC toward the given destination using a proactive {@link NavStep} plan.
	 */
	void navigateTo(Location destination) {
		if (ladderClimbActive) {
			navPlanTarget = destination.clone();
			return;
		}

		LivingEntity entity = owner.getEntity();
		if (entity == null) return;

		// ── Replan check ─────────────────────────────────────────────────────
		boolean replan = navPlanTarget == null || navPlan.isEmpty() || navPlanTarget.getWorld() == null ||
		                 !navPlanTarget.getWorld().equals(destination.getWorld()) ||
		                 navPlanTarget.distanceSquared(destination) >= NAV_PLAN_REPLAN_SQ;

		if (replan) {
			navPlan.clear();
			navPlanStep       = 0;
			navPlanStuckTicks = 0;
			navPlanTarget     = destination.clone();
			navPlan.addAll(buildNavPlan(destination));

			for (NavStep s : navPlan) {
				if (s.obstacle == NavObstacle.OPEN_DOOR) {
					executeObstacleAction(s);
				}
			}

			stuckSampleTicks       = 0;
			consecutiveStuckChecks = 0;
			lastProgressLocation   = entity.getLocation().clone();
		}

		// ── Live destination tracking ────────────────────────────────────────
		if (!navPlan.isEmpty()) {
			NavStep lastStep = navPlan.get(navPlan.size() - 1);
			if (lastStep.obstacle == NavObstacle.NONE) {
				Location freshDest = normalizeToStandableLocation(destination);
				if (freshDest != null) {
					lastStep.waypoint = freshDest;
				}
			}
		}

		// ── Plan exhausted ───────────────────────────────────────────────────
		if (navPlanStep >= navPlan.size()) {
			navPlanTarget = null;
			return;
		}

		NavStep step = navPlan.get(navPlanStep);

		// ── Arrival check — advance when close enough to current waypoint ────
		if (step.waypoint != null && step.waypoint.getWorld() != null &&
		    step.waypoint.getWorld().equals(entity.getLocation().getWorld())) {

			double arrivalDistSq;
			if (step.obstacle == NavObstacle.CLIMB_LADDER) {
				double adx = entity.getLocation().getX() - step.waypoint.getX();
				double adz = entity.getLocation().getZ() - step.waypoint.getZ();
				arrivalDistSq = adx * adx + adz * adz;
			} else {
				arrivalDistSq = entity.getLocation().distanceSquared(step.waypoint);
			}

			if (arrivalDistSq <= NAV_STEP_ARRIVE_SQ) {
				if (step.obstacle == NavObstacle.CLIMB_LADDER) {
					activateLadderClimb(step.obstacleBlock, step.descending);
					return;
				}

				navPlanStep++;
				navPlanStuckTicks      = 0;
				consecutiveStuckChecks = 0;
				navigationHopeless     = false;
				lastProgressLocation   = entity.getLocation().clone();

				if (navPlanStep >= navPlan.size()) {
					navPlanTarget = null;
					return;
				}
				step = navPlan.get(navPlanStep);
			}
		}

		// ── Hand Citizens the current step's waypoint (throttled) ────────────
		if (shouldRecalculateNavigation(step.waypoint)) {
			owner.npc.getNavigator().setTarget(step.waypoint);
			lastNavigationTarget    = step.waypoint.clone();
			navigationThrottleTicks = 0;

			if (replan) {
				stuckSampleTicks       = 0;
				consecutiveStuckChecks = 0;
				lastProgressLocation   = entity.getLocation().clone();
			}

			if (!owner.npc.getNavigator().isNavigating()) {
				consecutiveStuckChecks = Math.max(consecutiveStuckChecks, maxStuckChecks - 1);
			}
		}

		// ── Orient NPC toward waypoint ───────────────────────────────────────
		if (step.waypoint != null) {
			Vector navDir = step.waypoint.toVector().subtract(entity.getLocation().toVector()).setY(0);
			if (navDir.lengthSquared() > 0.01) {
				float navYaw = (float) Math.toDegrees(Math.atan2(-navDir.getX(), navDir.getZ()));
				entity.setRotation(navYaw, 0f);
			}
		}
	}

	/**
	 * Stops any current navigation and clears tracking. Call when transitioning out of a behavior state.
	 */
	void stopNavigation() {
		if (ladderClimbActive) return;
		owner.npc.getNavigator().cancelNavigation();
		resetNavigationTracking();
	}

	/**
	 * Cancels the Citizens navigator's current path without resetting tracking state.
	 */
	void pauseNavigation() {
		if (owner.npc.getNavigator().isNavigating()) {
			owner.npc.getNavigator().cancelNavigation();
		}
	}

	boolean isNavigationStuck() {
		return consecutiveStuckChecks >= maxStuckChecks;
	}

	boolean isNavigationHopeless() {
		if (!navPlan.isEmpty()) return false;
		if (!navigationHopeless && consecutiveStuckChecks >= maxHopelessStuckChecks) {
			navigationHopeless = true;
		}
		return navigationHopeless;
	}

	/**
	 * Returns whether the given distance falls within the ranged hold range.
	 */
	boolean isInRangedHoldRange(double distance) {
		return distance >= rangedMinDistance && distance <= rangedMaxDistance;
	}

	/**
	 * Resolves the navigation target while pursuing a player.
	 */
	Location resolvePursuitLocation(Player player) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return null;

		Location npcLocation    = entity.getLocation().clone();
		Location playerLocation = player.getLocation().clone();

		if (npcLocation.getWorld() == null || !npcLocation.getWorld().equals(playerLocation.getWorld())) {
			return playerLocation;
		}

		Location safePlayerSpot = normalizeToStandableLocation(playerLocation);

		return safePlayerSpot != null ? safePlayerSpot : playerLocation;
	}

	/**
	 * Resolves the best reachable position when normal pathfinding has been declared hopeless.
	 */
	Location resolveHopelessFallbackLocation(Player player) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return null;

		Location from = entity.getLocation();
		Location to   = player.getLocation();

		if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return null;

		double heightDelta = to.getY() - from.getY();
		if (heightDelta > HEIGHT_CLIMB_THRESHOLD) {
			Location base = findBaseApproachBelowTarget(to);
			if (base != null && base.distanceSquared(from) > 4.0) return base;
			Location ladderBase = findNearestLadderBase(from, to, 15);
			if (ladderBase != null) return ladderBase;
			if (base != null) return base;
		}

		if (from.distanceSquared(to) <= hopelessCloseThreshold * hopelessCloseThreshold) {
			Location lateral = resolveLateralExploration(from, to);
			if (lateral != null) return lateral;
			Location safe = normalizeToStandableLocation(to);
			return safe != null ? safe : to;
		}

		Location gapWalk = findLastReachableGroundBeforeGap(from, to, 32.0);
		if (gapWalk != null) return gapWalk;

		Location lineApproach = findLineApproachLocation(from, to, 32.0);
		if (lineApproach != null) return lineApproach;

		double distance = from.distance(to);
		return findBestRingApproachLocation(from, to, 1.5, Math.min(distance, 16.0), Math.min(distance * 0.5, 8.0));
	}

	/**
	 * Resolves the navigation target while pursuing a non-player entity.
	 */
	Location resolvePursuitLocation(LivingEntity target) {
		Location targetLocation = target.getLocation().clone();
		Location safe           = normalizeToStandableLocation(targetLocation);
		return safe != null ? safe : targetLocation;
	}

	/**
	 * Resolves the best reachable position when pathfinding has been declared hopeless for a non-player entity target.
	 */
	Location resolveHopelessFallbackLocation(LivingEntity target) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return null;

		Location from = entity.getLocation();
		Location to   = target.getLocation();

		if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return null;

		double heightDelta = to.getY() - from.getY();
		if (heightDelta > HEIGHT_CLIMB_THRESHOLD) {
			Location base = findBaseApproachBelowTarget(to);
			if (base != null && base.distanceSquared(from) > 4.0) return base;
			Location ladderBase = findNearestLadderBase(from, to, 15);
			if (ladderBase != null) return ladderBase;
			if (base != null) return base;
		}

		if (from.distanceSquared(to) <= hopelessCloseThreshold * hopelessCloseThreshold) {
			Location lateral = resolveLateralExploration(from, to);
			if (lateral != null) return lateral;
			Location safe = normalizeToStandableLocation(to);
			return safe != null ? safe : to;
		}

		Location gapWalk = findLastReachableGroundBeforeGap(from, to, 32.0);
		if (gapWalk != null) return gapWalk;

		Location lineApproach = findLineApproachLocation(from, to, 32.0);
		if (lineApproach != null) return lineApproach;

		double distance = from.distance(to);
		return findBestRingApproachLocation(from, to, 1.5, Math.min(distance, 16.0), Math.min(distance * 0.5, 8.0));
	}

	/**
	 * Scans a forward-biased cone of positions and returns the best walkable destination for wandering.
	 */
	Location findForwardWanderDestination(int minDist, int maxDist) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return null;

		Location origin = entity.getLocation();
		if (origin.getWorld() == null) return null;

		float  yaw     = origin.getYaw();
		double midDist = (minDist + maxDist) / 2.0;

		int[] angleOffsets = {0, -20, 20, -45, 45, -70, 70, -90, 90};
		int[] distances    = {minDist, (int) midDist, maxDist};

		Location best      = null;
		double   bestScore = Double.MAX_VALUE;

		for (int angleOffset : angleOffsets) {
			double rad = Math.toRadians(yaw + angleOffset);
			double dx  = -Math.sin(rad);
			double dz  = Math.cos(rad);

			for (int dist : distances) {
				Location candidate = origin.clone().add(dx * dist, 0, dz * dist);
				Location standable = normalizeToStandableLocation(candidate);
				if (!isBasicSafeStandLocation(standable)) continue;

				double score = Math.abs(angleOffset) * 0.5 + Math.abs(dist - midDist) * 0.3 +
				               ThreadLocalRandom.current().nextDouble(0, 18.0);
				if (score < bestScore) {
					bestScore = score;
					best      = standable;
				}
			}
		}

		if (best != null) return best;

		// Fallback: full 360 sweep
		for (int angle = 0; angle < 360; angle += 45) {
			double rad = Math.toRadians(angle);
			double dx  = -Math.sin(rad);
			double dz  = Math.cos(rad);

			for (int dist : distances) {
				Location candidate = origin.clone().add(dx * dist, 0, dz * dist);
				Location standable = normalizeToStandableLocation(candidate);
				if (!isBasicSafeStandLocation(standable)) continue;
				return standable;
			}
		}

		return null;
	}

	/**
	 * Updates navigation progress tracking for throttling and stuck detection.
	 */
	void updateNavigationProgress() {
		navigationThrottleTicks++;

		LivingEntity entity = owner.getEntity();

		if (entity == null) {
			return;
		}

		if (ladderClimbActive) {
			tickLadderClimb();
			return;
		}

		// NavPlan step-level stuck detection
		if (!navPlan.isEmpty()) {
			navPlanStuckTicks++;
			if (navPlanStuckTicks >= NAV_PLAN_STUCK_LIMIT) {
				navPlanTarget     = null;
				navPlanStuckTicks = 0;
			}
		}

		if (lastNavigationTarget == null) {
			stuckSampleTicks = 0;
			return;
		}

		stuckSampleTicks++;

		if (lastProgressLocation == null) {
			lastProgressLocation = entity.getLocation().clone();
			stuckSampleTicks     = 0;
			return;
		}

		if (stuckSampleTicks < stuckCheckIntervalTicks) return;

		Location currentLocation = entity.getLocation();

		if (currentLocation.getWorld() == null ||
		    !Objects.equals(currentLocation.getWorld(), lastProgressLocation.getWorld())) {
			lastProgressLocation   = currentLocation.clone();
			stuckSampleTicks       = 0;
			consecutiveStuckChecks = 0;
			return;
		}

		double progress = currentLocation.distanceSquared(lastProgressLocation);

		if (progress < minProgressDistanceSquared) {
			consecutiveStuckChecks++;
		} else {
			consecutiveStuckChecks = 0;
			navigationHopeless     = false;
			committedLateralSpot   = null;
			lastProgressLocation   = currentLocation.clone();
		}

		stuckSampleTicks = 0;
	}

	// ── Private helpers ──────────────────────────────────────────────────────

	private void configurePathfinder() {
		if (owner.npc == null) return;
		net.citizensnpcs.api.ai.NavigatorParameters params = owner.npc.getNavigator().getDefaultParameters();
		params.range(64f);
		params.pathfinderType(net.citizensnpcs.api.ai.PathfinderType.MINECRAFT);
	}

	private void resetNavigationTracking() {
		lastNavigationTarget    = null;
		lastProgressLocation    = null;
		navigationThrottleTicks = navigationRecalculationTicks;
		stuckSampleTicks        = 0;
		consecutiveStuckChecks  = 0;
		navigationHopeless      = false;
		committedLateralSpot    = null;

		if (ladderClimbActive) {
			LivingEntity entity = owner.getEntity();
			if (entity != null) {
				entity.setGravity(true);
			}
		}

		ladderClimbActive    = false;
		ladderClimbBlock     = null;
		ladderClimbTickCount = 0;

		navPlanStuckTicks = 0;
	}

	private boolean shouldRecalculateNavigation(Location target) {
		if (target == null) return false;

		if (lastNavigationTarget == null || lastNavigationTarget.getWorld() == null || target.getWorld() == null ||
		    !lastNavigationTarget.getWorld().equals(target.getWorld())) {
			return true;
		}

		boolean targetMoved = lastNavigationTarget.distanceSquared(target) >= TARGET_MOVED_THRESHOLD_SQ;

		if (navigationThrottleTicks < navigationRecalculationTicks) {
			return targetMoved;
		}

		return targetMoved || isNavigationStuck();
	}

	private Location resolveLateralExploration(Location from, Location to) {
		long now = System.currentTimeMillis();

		if (committedLateralSpot != null && now < committedLateralExpiresAtMs &&
		    committedLateralSpot.getWorld() != null && committedLateralSpot.getWorld().equals(from.getWorld())) {
			return committedLateralSpot;
		}

		if (committedLateralSpot != null) {
			lateralExplorationSide++;
		}

		Vector toTarget = to.toVector().subtract(from.toVector()).setY(0);
		if (toTarget.lengthSquared() < 1e-4) {
			committedLateralSpot = null;
			return null;
		}
		toTarget.normalize();

		double[] radii = {5.0, 7.0, 9.0};
		int      start = Math.floorMod(lateralExplorationSide, LATERAL_EXPLORATION_DIRECTIONS);

		for (int offset = 0; offset < LATERAL_EXPLORATION_DIRECTIONS; offset++) {
			int    sideIndex = Math.floorMod(start + offset, LATERAL_EXPLORATION_DIRECTIONS);
			double angleRad  = Math.toRadians(90.0 + (360.0 / LATERAL_EXPLORATION_DIRECTIONS) * sideIndex);
			double cos       = Math.cos(angleRad);
			double sin       = Math.sin(angleRad);

			Vector rotated = new Vector(toTarget.getX() * cos - toTarget.getZ() * sin, 0,
			                            toTarget.getX() * sin + toTarget.getZ() * cos);

			for (double radius : radii) {
				Location candidate = from.clone().add(rotated.clone().multiply(radius));
				Location safe      = normalizeToStandableLocation(candidate);
				if (safe != null && safe.distanceSquared(from) >= 9.0) {
					committedLateralSpot        = safe;
					committedLateralExpiresAtMs = now + LATERAL_COMMITMENT_MS;
					lateralExplorationSide      = sideIndex;
					return safe;
				}
			}
		}

		committedLateralSpot = null;
		return null;
	}

	// ── Terrain analysis ─────────────────────────────────────────────────────

	private Location normalizeToStandableLocation(Location location) {
		if (location == null || location.getWorld() == null) return null;

		World world = location.getWorld();
		int   baseX = location.getBlockX();
		int   baseZ = location.getBlockZ();

		for (int yOffset = 2; yOffset >= -4; yOffset--) {
			Location candidate = new Location(world, baseX + 0.5, location.getY() + yOffset, baseZ + 0.5,
			                                  location.getYaw(), location.getPitch());
			if (isBasicSafeStandLocation(candidate)) return candidate;
		}

		return null;
	}

	private boolean isSafeStandLocation(Location location) {
		return isBasicSafeStandLocation(location) && !isFrontedByImmediateGap(location);
	}

	private boolean isBasicSafeStandLocation(Location location) {
		if (location == null || location.getWorld() == null) return false;

		Block feet  = location.getBlock();
		Block head  = feet.getRelative(0, 1, 0);
		Block below = feet.getRelative(0, -1, 0);

		if (!feet.isPassable() || !head.isPassable()) return false;

		Material feetType = feet.getType();
		if (feetType == Material.NETHER_PORTAL || feetType == Material.END_PORTAL || feetType == Material.END_GATEWAY)
			return false;

		Material headType = head.getType();
		if (headType == Material.NETHER_PORTAL || headType == Material.END_PORTAL || headType == Material.END_GATEWAY)
			return false;

		if (below.isPassable()) return false;

		Material supportType = below.getType();
		return supportType != Material.LAVA && supportType != Material.WATER && supportType != Material.CACTUS &&
		       supportType != Material.MAGMA_BLOCK;
	}

	private boolean isFrontedByImmediateGap(Location location) {
		LivingEntity entity = owner.getEntity();
		if (entity == null) return false;

		Vector facing = entity.getLocation().toVector().subtract(location.toVector());
		facing.setY(0.0);
		if (facing.lengthSquared() <= 0.0001) return false;

		Vector   step  = facing.normalize();
		Location front = location.clone().add(step.getX(), 0.0, step.getZ());
		World    world = front.getWorld();
		int      baseX = front.getBlockX();
		int      baseZ = front.getBlockZ();

		for (int yOffset = 2; yOffset >= -4; yOffset--) {
			Location candidate = new Location(world, baseX + 0.5, front.getY() + yOffset, baseZ + 0.5);
			if (isBasicSafeStandLocation(candidate)) return false;
		}

		return true;
	}

	private boolean isWalkableStep(Location from, Location to) {
		if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
			return false;
		}

		int deltaY = to.getBlockY() - from.getBlockY();
		if (deltaY > 1 || deltaY < -1) return false;

		double dx = from.getX() - to.getX();
		double dz = from.getZ() - to.getZ();
		if (dx * dx + dz * dz > 1.25 * 1.25) return false;

		return isBasicSafeStandLocation(to);
	}

	private boolean hasClearShot(Location from, Location to) {
		if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
			return false;
		}

		Location start  = from.clone().add(0.0, 1.2, 0.0);
		Location end    = to.clone().add(0.0, 1.0, 0.0);
		Vector   delta  = end.toVector().subtract(start.toVector());
		double   length = delta.length();

		if (length <= 0.0001) return true;

		var hit = from.getWorld().rayTraceBlocks(start, delta.normalize(), length, FluidCollisionMode.NEVER, true);
		return hit == null;
	}

	// ── Approach finders ─────────────────────────────────────────────────────

	private Location findLastReachableGroundBeforeGap(Location from, Location to, double maxDistance) {
		if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
			return null;
		}

		Vector horizontal = to.toVector().subtract(from.toVector());
		horizontal.setY(0.0);

		if (horizontal.lengthSquared() <= 0.0001) return normalizeToStandableLocation(from);

		double   totalDistance = Math.min(horizontal.length(), maxDistance);
		Vector   direction     = horizontal.normalize().multiply(0.5);
		Location lastSafe      = normalizeToStandableLocation(from);
		Location cursor        = from.clone();

		for (double travelled = 0.5; travelled <= totalDistance; travelled += 0.5) {
			cursor = cursor.clone().add(direction);

			Location standable = normalizeToStandableLocation(cursor);
			if (standable == null) return lastSafe;
			if (!isWalkableStep(lastSafe, standable)) return lastSafe;

			lastSafe = standable;
		}

		return lastSafe;
	}

	private Location findLineApproachLocation(Location npcLocation, Location playerLocation, double maxDistance) {
		Vector direction = npcLocation.toVector().subtract(playerLocation.toVector());

		if (direction.lengthSquared() <= 0.0001) return normalizeToStandableLocation(playerLocation);

		direction.normalize();

		if (npcLocation.getWorld() == null || !npcLocation.getWorld().equals(playerLocation.getWorld())) {
			return null;
		}

		double searchDistance = Math.min(npcLocation.distance(playerLocation), maxDistance);

		for (double offset = 0.0; offset <= searchDistance; offset += 1.0) {
			Location candidate     = playerLocation.clone().add(direction.clone().multiply(offset));
			Location safeCandidate = normalizeToStandableLocation(candidate);
			if (safeCandidate != null) return safeCandidate;
		}

		return null;
	}

	private Location findBestRingApproachLocation(Location npcLocation, Location playerLocation, double minRadius,
	                                              double maxRadius, double idealRadius) {
		Location bestLocation = null;
		double   bestScore    = Double.MAX_VALUE;

		for (double radius = minRadius; radius <= maxRadius; radius += 1.5) {
			for (int angle = 0; angle < 360; angle += 22) {
				double radians = Math.toRadians(angle);
				Location candidate = playerLocation.clone()
				                                   .add(Math.cos(radians) * radius, 0.0, Math.sin(radians) * radius);

				Location safeCandidate = normalizeToStandableLocation(candidate);
				if (safeCandidate == null || !isSafeStandLocation(safeCandidate)) continue;
				if (safeCandidate.getWorld() == null || !safeCandidate.getWorld().equals(npcLocation.getWorld())) {
					continue;
				}

				double score = safeCandidate.distanceSquared(npcLocation);
				score += Math.abs(radius - idealRadius) * 3.0;

				if (owner.isUsingRangedWeapon()) {
					if (!hasClearShot(safeCandidate, playerLocation)) score += 20.0;
				} else {
					score += radius * 2.0;
				}

				if (bestLocation == null || score < bestScore) {
					bestLocation = safeCandidate;
					bestScore    = score;
				}
			}
		}

		return bestLocation;
	}

	private Location findBaseApproachBelowTarget(Location target) {
		if (target == null || target.getWorld() == null) return null;

		LivingEntity entity = owner.getEntity();
		if (entity == null) return null;

		World world = target.getWorld();
		int   baseX = target.getBlockX();
		int   baseZ = target.getBlockZ();
		int   npcY  = entity.getLocation().getBlockY();

		for (int radius = 0; radius <= 4; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
					Location probe     = new Location(world, baseX + dx + 0.5, npcY, baseZ + dz + 0.5);
					Location standable = normalizeToStandableLocation(probe);
					if (standable != null) return standable;
				}
			}
		}

		return null;
	}

	// ── Ladder climbing ──────────────────────────────────────────────────────

	private Location findNearestLadderBase(Location from, Location to, int searchRadius) {
		if (to == null || to.getWorld() == null) return null;

		World world   = to.getWorld();
		int   npcY    = from != null ? from.getBlockY() : to.getBlockY();
		int   targetY = to.getBlockY();

		int centerX = from != null ? (from.getBlockX() + to.getBlockX()) / 2 : to.getBlockX();
		int centerZ = from != null ? (from.getBlockZ() + to.getBlockZ()) / 2 : to.getBlockZ();

		int minScanY = Math.max(world.getMinHeight(), Math.min(npcY, targetY) - 2);
		int maxScanY = Math.min(world.getMaxHeight() - 1, Math.max(npcY, targetY) + 2);

		Location nearest     = null;
		double   nearestDist = Double.MAX_VALUE;

		for (int dx = -searchRadius; dx <= searchRadius; dx++) {
			for (int dz = -searchRadius; dz <= searchRadius; dz++) {
				for (int y = minScanY; y <= maxScanY; y++) {
					if (world.getBlockAt(centerX + dx, y, centerZ + dz).getType() != Material.LADDER) continue;

					int bottomY = y;
					while (bottomY > minScanY &&
					       world.getBlockAt(centerX + dx, bottomY - 1, centerZ + dz).getType() == Material.LADDER) {
						bottomY--;
					}

					Location base = new Location(world, centerX + dx + 0.5, bottomY, centerZ + dz + 0.5);

					double dist = from != null ? from.distanceSquared(base) : 0;
					if (dist < nearestDist) {
						nearestDist = dist;
						nearest     = base;
					}

					break;
				}
			}
		}

		return nearest;
	}

	private void tickLadderClimb() {
		LivingEntity entity = owner.getEntity();
		if (entity == null) {
			return;
		}
		if (entity.isDead()) {
			ladderClimbActive = false;
			ladderClimbBlock  = null;
			return;
		}

		ladderClimbTickCount++;
		if (ladderClimbTickCount >= MAX_LADDER_CLIMB_TICKS) {
			endLadderClimb(entity, true);
			return;
		}

		double  currentY = entity.getLocation().getY();
		boolean arrived  = ladderClimbDescending ? currentY <= ladderClimbDestY : currentY >= ladderClimbDestY;

		if (arrived) {
			endLadderClimb(entity, false);
			return;
		}

		Location cur       = entity.getLocation();
		double   remaining = Math.abs(ladderClimbDestY - cur.getY());
		double   stepY     = Math.min(1.0, remaining);
		double   targetX   = ladderClimbBlock != null ? ladderClimbBlock.getX() + 0.5 : cur.getX();
		double   targetZ   = ladderClimbBlock != null ? ladderClimbBlock.getZ() + 0.5 : cur.getZ();
		double   targetY   = ladderClimbDescending ? cur.getY() - stepY : cur.getY() + stepY;

		Location dest = new Location(cur.getWorld(), targetX, targetY, targetZ, cur.getYaw(), cur.getPitch());
		entity.teleport(dest);
	}

	private void endLadderClimb(LivingEntity entity, boolean timedOut) {
		if (!timedOut && ladderClimbBlock != null) {
			Location exitLoc = findStandableAtLadderExit(entity, ladderClimbBlock, ladderClimbDestY);
			if (exitLoc != null) {
				entity.teleport(exitLoc);
			}
		}

		ladderClimbActive = false;
		ladderClimbBlock  = null;
		entity.setGravity(true);

		navPlanTarget          = null;
		consecutiveStuckChecks = 0;
		stuckSampleTicks       = 0;
		lastProgressLocation   = entity.getLocation().clone();
		lastNavigationTarget   = null;
	}

	private Location findStandableAtLadderExit(LivingEntity entity, Block ladder, int exitY) {
		World world = ladder.getWorld();
		int   lx    = ladder.getX();
		int   lz    = ladder.getZ();

		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			int nx = lx + d[0];
			int nz = lz + d[1];

			Block below = world.getBlockAt(nx, exitY - 1, nz);
			Block feet  = world.getBlockAt(nx, exitY, nz);
			Block head  = world.getBlockAt(nx, exitY + 1, nz);

			if (below.getType().isSolid() && feet.isPassable() && head.isPassable()) {
				return new Location(world, nx + 0.5, exitY, nz + 0.5, entity.getLocation().getYaw(),
				                    entity.getLocation().getPitch());
			}
		}

		return normalizeToStandableLocation(new Location(world, lx + 0.5, exitY, lz + 0.5));
	}

	private Block findLadderBlockAtBase(Location base) {
		if (base == null || base.getWorld() == null) return null;
		World world = base.getWorld();
		int   bx    = base.getBlockX();
		int   by    = base.getBlockY();
		int   bz    = base.getBlockZ();

		for (int yOff = 0; yOff <= 2; yOff++) {
			Block b = world.getBlockAt(bx, by + yOff, bz);
			if (b.getType() == Material.LADDER) return b;
		}

		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			for (int yOff = 0; yOff <= 1; yOff++) {
				Block b = world.getBlockAt(bx + d[0], by + yOff, bz + d[1]);
				if (b.getType() == Material.LADDER) return b;
			}
		}
		return null;
	}

	private void activateLadderClimb(Block ladder, boolean descending) {
		if (ladder == null || ladderClimbActive) return;

		World world = ladder.getWorld();

		int topY = ladder.getY();
		for (int y = ladder.getY() + 1; y <= ladder.getY() + 32; y++) {
			if (world.getBlockAt(ladder.getX(), y, ladder.getZ()).getType() == Material.LADDER) {
				topY = y;
			} else {
				break;
			}
		}

		int bottomY = ladder.getY();
		for (int y = ladder.getY() - 1; y >= ladder.getY() - 32; y--) {
			if (world.getBlockAt(ladder.getX(), y, ladder.getZ()).getType() == Material.LADDER) {
				bottomY = y;
			} else {
				break;
			}
		}

		ladderClimbBlock      = ladder;
		ladderClimbDescending = descending;
		ladderClimbDestY      = descending ? bottomY : topY + 1;
		ladderClimbTickCount  = 0;
		ladderClimbActive     = true;
		owner.npc.getNavigator().cancelNavigation();

		LivingEntity climbEntity = owner.getEntity();
		if (climbEntity != null) {
			climbEntity.setGravity(false);
		}
	}

	private void executeObstacleAction(NavStep step) {
		if (step.obstacle != NavObstacle.OPEN_DOOR) return;
		if (owner.plugin == null || step.obstacleBlock == null) return;

		Block     door = step.obstacleBlock;
		BlockData raw  = door.getBlockData();
		if (!(raw instanceof Openable openable) || openable.isOpen()) return;

		boolean isIron     = door.getType() == Material.IRON_DOOR;
		Sound   closeSound = isIron ? Sound.BLOCK_IRON_DOOR_CLOSE : Sound.BLOCK_WOODEN_DOOR_CLOSE;
		openable.setOpen(true);
		door.setBlockData(openable);
		door.getWorld()
		    .playSound(door.getLocation(), isIron ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);

		Bukkit.getScheduler().runTaskLater(owner.plugin, () -> {
			BlockData current = door.getBlockData();
			if (current instanceof Openable toClose && toClose.isOpen()) {
				toClose.setOpen(false);
				door.setBlockData(toClose);
				door.getWorld().playSound(door.getLocation(), closeSound, 1f, 1f);
			}
		}, 40L);
	}

	// ── NavPlan — proactive path planning ────────────────────────────────────

	private List<NavStep> buildNavPlan(Location destination) {
		List<NavStep> steps = new ArrayList<>();

		LivingEntity entity = owner.getEntity();
		if (entity == null) {
			steps.add(new NavStep(destination, NavObstacle.NONE, null));
			return steps;
		}

		Location from  = entity.getLocation();
		World    world = from.getWorld();
		if (world == null || destination.getWorld() == null || !world.equals(destination.getWorld())) {
			steps.add(new NavStep(destination, NavObstacle.NONE, null));
			return steps;
		}

		Location destStandable = normalizeToStandableLocation(destination);
		if (destStandable == null) destStandable = destination;

		// ── Phase 1: horizontal door scan at NPC floor Y ──────────────────
		Block  foundDoor  = null;
		Vector horizontal = new Vector(destination.getX() - from.getX(), 0, destination.getZ() - from.getZ());
		if (horizontal.lengthSquared() > 0.0001) {
			double scanDist = Math.min(horizontal.length(), 30.0);
			horizontal.normalize();
			int fromY = from.getBlockY();

			outer:
			for (double d = 0.5; d <= scanDist; d += 0.5) {
				int x = (int) Math.floor(from.getX() + horizontal.getX() * d);
				int z = (int) Math.floor(from.getZ() + horizontal.getZ() * d);
				for (int yOff = 0; yOff <= 1; yOff++) {
					Block block = world.getBlockAt(x, fromY + yOff, z);
					if (!Tag.DOORS.isTagged(block.getType())) continue;
					BlockData raw = block.getBlockData();
					if (!(raw instanceof Bisected b) || b.getHalf() != Bisected.Half.BOTTOM) continue;
					if (raw instanceof Openable o && o.isOpen()) continue;
					foundDoor = block;
					break outer;
				}
			}
		}

		// ── Phase 2: elevation — find ladder if significant height difference ─
		Block    ladderBlock      = null;
		Location ladderApproach   = null;
		boolean  ladderDescending = false;
		double   heightDelta      = destination.getY() - from.getY();
		if (Math.abs(heightDelta) > HEIGHT_CLIMB_THRESHOLD) {
			ladderDescending = heightDelta < 0;
			double xzDist = Math.sqrt(
					Math.pow(destination.getX() - from.getX(), 2) + Math.pow(destination.getZ() - from.getZ(), 2));
			int ladderSearchRadius = Math.clamp((int) Math.ceil(xzDist / 2) + 5, 15, 30);

			Location ladderBase = findNearestLadderBase(from, destination, ladderSearchRadius);

			if (ladderBase != null) {
				ladderBlock = findLadderBlockAtBase(ladderBase);

				if (ladderBlock != null) {
					double ladderX = ladderBlock.getX() + 0.5;
					double ladderZ = ladderBlock.getZ() + 0.5;
					double dirX    = from.getX() - ladderX;
					double dirZ    = from.getZ() - ladderZ;
					double dirLen  = Math.sqrt(dirX * dirX + dirZ * dirZ);

					if (dirLen > 0.01) {
						int approachY = from.getBlockY() + 1;
						if (ladderDescending) {
							int topY = ladderBlock.getY();
							for (int y = ladderBlock.getY() + 1; y <= ladderBlock.getY() + 32; y++) {
								if (world.getBlockAt(ladderBlock.getX(), y, ladderBlock.getZ()).getType() ==
								    Material.LADDER) {
									topY = y;
								} else {
									break;
								}
							}
							approachY = topY + 1;
						}

						Location rawApproach = new Location(world, ladderX + dirX / dirLen, approachY,
						                                    ladderZ + dirZ / dirLen);
						Location standable = normalizeToStandableLocation(rawApproach);
						ladderApproach = standable != null ? standable : ladderBase;
					} else {
						ladderApproach = ladderBase;
					}
				}
			}
		}

		// ── Phase 3: build step list ──────────────────────────────────────
		if (foundDoor != null) {
			Location doorWaypoint = ladderApproach != null ? ladderApproach : destStandable;
			steps.add(new NavStep(doorWaypoint, NavObstacle.OPEN_DOOR, foundDoor));
		}

		if (ladderApproach != null) {
			steps.add(new NavStep(ladderApproach, NavObstacle.CLIMB_LADDER, ladderBlock, ladderDescending));
		}

		steps.add(new NavStep(destStandable, NavObstacle.NONE, null));
		return steps;
	}
}
