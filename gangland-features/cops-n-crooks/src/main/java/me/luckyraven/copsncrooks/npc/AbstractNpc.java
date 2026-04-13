package me.luckyraven.copsncrooks.npc;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.entity.EntityMarkManager;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.downed.DownedPlayerRegistry;
import me.luckyraven.util.timer.SequenceTimer;
import me.luckyraven.weapon.SelectiveFire;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import me.luckyraven.weapon.raytrace.WeaponRaytracer;
import me.luckyraven.weapon.raytrace.WeaponShooting;
import me.luckyraven.weapon.types.gun.GunWeapon;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.PathfinderType;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Base class for all gangland Citizens NPC types (cops, civilians, etc.).
 * <p>
 * Contains all shared logic: weapon handling, attack pipeline (gangland → vanilla ranged → melee), reload scheduling,
 * Citizens navigation with stuck-detection and hopeless-fallback resolution, and NPC lifecycle management.
 * <p>
 * Subclasses provide type-specific configuration by implementing the abstract methods.
 */
@CustomLog
public abstract class AbstractNpc {

	/**
	 * Minimum squared-distance the target must move before an in-flight navigation is torn down and re-paths. Keeps
	 * Citizens from being told to re-target the same (possibly unreachable) spot every few ticks — that re-issue is
	 * what produces the visible "wiggle between blocks" glitch.
	 */
	private static final double        TARGET_MOVED_THRESHOLD_SQ      = 2.25;
	private static final long          LATERAL_COMMITMENT_MS          = 1000L;
	private static final int           LATERAL_EXPLORATION_DIRECTIONS = 8;
	private static final double        HEIGHT_CLIMB_THRESHOLD         = 1.5;
	private static final int           MAX_LADDER_CLIMB_TICKS         = 100;
	private static final double        NAV_PLAN_REPLAN_SQ             = 9.0;  // replan when target moves 3+ blocks
	private static final double        NAV_STEP_ARRIVE_SQ             = 3.0;  // step complete within ~1.7 blocks
	private static final int           NAV_PLAN_STUCK_LIMIT           = 80;   // force replan after ~4 s on one step
	// ── Core ─────────────────────────────────────────────────────────────────
	@Getter
	protected final      NPC           npc;
	@Getter
	protected final      Location      spawnLocation;
	protected final      int           aiTickRate;
	// ── Navigation config (read once from NpcNavigationConfig) ──────────────
	private final        int           navigationRecalculationTicks;
	private final        int           stuckCheckIntervalTicks;
	private final        int           maxStuckChecks;
	private final        int           maxHopelessStuckChecks;
	private final        double        hopelessCloseThreshold;
	private final        double        minProgressDistanceSquared;
	private final        double        rangedMinDistance;
	private final        double        rangedMaxDistance;
	// ── NavPlan ──────────────────────────────────────────────────────────────
	private final        List<NavStep> navPlan                        = new ArrayList<>();
	// ── Weapon ───────────────────────────────────────────────────────────────
	@Getter
	protected            Weapon        heldWeapon;
	protected            boolean       reloading;
	protected            JavaPlugin    plugin;
	// ── Difficulty ───────────────────────────────────────────────────────────
	@Getter
	protected            NpcDifficulty difficulty;
	// ── State ────────────────────────────────────────────────────────────────
	@Getter
	protected            boolean       markedForRemoval;
	@Getter
	@Setter
	protected            int           despawnTicks;
	protected            int           attackCooldown;
	/**
	 * Last target {@link #attack}/{@link #attackEntity} engaged — used to detect a fresh acquisition for
	 * reaction-time.
	 */
	private              LivingEntity  previousAttackTarget;
	// ── Navigation state ─────────────────────────────────────────────────────
	private              Location      lastNavigationTarget;
	private              Location      lastProgressLocation;
	private              int           navigationThrottleTicks;
	private              int           stuckSampleTicks;
	private              int           consecutiveStuckChecks;
	private              boolean       navigationHopeless;
	/**
	 * Counter used by {@link #resolveLateralExploration} to alternate sides across commitment windows so the NPC probes
	 * both the left and the right walk-around directions over time instead of always aiming at the same failing angle.
	 */
	private              int           lateralExplorationSide;
	/**
	 * Cached lateral walk-around target returned by {@link #resolveLateralExploration}. Once computed, the same spot is
	 * returned on every subsequent call within the commitment window so the NPC actually has time to walk toward it.
	 * Cleared by {@link #updateNavigationProgress} the moment real progress is detected, and by
	 * {@link #resetNavigationTracking} on state transitions. Timeout is tracked in
	 * {@link #committedLateralExpiresAtMs}.
	 */
	private              Location      committedLateralSpot;
	private              long          committedLateralExpiresAtMs;
	// ── Ladder climbing ──────────────────────────────────────────────────────
	private              boolean       ladderClimbActive;
	private              int           ladderClimbDestY;
	private              Block         ladderClimbBlock;
	private              int           ladderClimbTickCount;
	private              boolean       ladderClimbDescending;
	private              int           navPlanStep                    = 0;
	private              Location      navPlanTarget                  = null;
	private              int           navPlanStuckTicks              = 0;

	protected AbstractNpc(NPC npc, Location spawnLocation, NpcNavigationConfig navConfig, NpcDifficulty difficulty) {
		this.npc           = npc;
		this.spawnLocation = spawnLocation;
		this.aiTickRate    = navConfig.getAiTickRate();
		this.difficulty    = difficulty != null ? difficulty : NpcDifficulty.NORMAL;

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

		this.attackCooldown         = 0;
		this.markedForRemoval       = false;
		this.despawnTicks           = 0;
		this.reloading              = false;
		this.stuckSampleTicks       = 0;
		this.consecutiveStuckChecks = 0;

		configurePathfinder();
	}

	// ── Abstract contract ────────────────────────────────────────────────────

	/**
	 * Returns whether this NPC can use weapons (ranged or melee via weapon pipeline).
	 */
	public abstract boolean canUseWeapons();

	/**
	 * Returns the base melee attack damage for this NPC.
	 */
	public abstract double getAttackDamage();

	/**
	 * Equips this NPC with its configured loadout (armor + weapon).
	 */
	public abstract void equip();

	/**
	 * Called on destruction so subclasses can release resources held by the current behavior.
	 */
	protected abstract void cleanupTransientState();

	// ── Weapon ───────────────────────────────────────────────────────────────

	/**
	 * Assigns the gangland weapon this NPC will fire. Call before {@link #equip()}.
	 */
	public void setHeldWeapon(Weapon weapon, JavaPlugin plugin) {
		this.heldWeapon = weapon;
		this.plugin     = plugin;
	}

	// ── Lifecycle ────────────────────────────────────────────────────────────

	/**
	 * Marks this NPC for removal during the next cleanup cycle.
	 */
	public void markForRemoval() {
		this.markedForRemoval = true;
	}

	/**
	 * Returns the living entity associated with this NPC.
	 */
	public LivingEntity getEntity() {
		return (LivingEntity) npc.getEntity();
	}

	/**
	 * Returns whether the NPC is currently spawned and alive.
	 */
	public boolean isValid() {
		if (!npc.isSpawned() || npc.getEntity() == null || npc.getEntity().isDead()) return false;

		ensureDamageable();
		return true;
	}

	/**
	 * Despawns and destroys the NPC, cleaning up entity marks.
	 */
	public void destroy(EntityMarkManager entityMarkManager) {
		try {
			if (heldWeapon != null) {
				heldWeapon.stopReloading();
			}

			if (npc.isSpawned()) {
				if (entityMarkManager != null && npc.getEntity() != null) {
					entityMarkManager.removeEntityMark(npc.getEntity());
				}
				npc.despawn();
			}
		} finally {
			cleanupTransientState();
			npc.destroy();
		}
	}

	/**
	 * Despawns and destroys the NPC without entity mark cleanup.
	 */
	public void destroy() {
		destroy(null);
	}

	/**
	 * Returns whether this NPC is currently using a ranged weapon.
	 */
	public boolean isUsingRangedWeapon() {
		if (!canUseWeapons()) return false;
		return heldWeapon != null || isHoldingVanillaRangedWeapon();
	}

	// ── Combat ───────────────────────────────────────────────────────────────

	/**
	 * Returns whether the NPC should hold position instead of closing distance (ranged hold check).
	 */
	public boolean shouldHoldPursuitPosition(Player player) {
		if (!isUsingRangedWeapon() || !hasLineOfSight(player)) return false;
		double distance = distanceTo(player);
		return distance >= rangedMinDistance && distance <= rangedMaxDistance;
	}

	/**
	 * Returns whether the attack cooldown has elapsed, the NPC is not reloading, and the held weapon (if any) is not
	 * mid-reload.
	 */
	public boolean canAttack() {
		if (attackCooldown > 0 || reloading) return false;
		return heldWeapon == null || !heldWeapon.isReloading();
	}

	/**
	 * Attacks the target player using the highest-priority available attack: gangland weapon → vanilla ranged → melee
	 * fallback. Does nothing if the target is dead or in a downed state.
	 */
	public void attack(Player player) {
		if (!isValid() || player == null) return;
		if (player.isDead() || DownedPlayerRegistry.isDowned(player.getUniqueId())) return;

		applyReactionTimeOnTargetSwitch(player);

		if (!canAttack()) return;

		faceTarget(player);

		if (canUseWeapons() && heldWeapon != null && hasLineOfSight(player)) {
			performGanglandWeaponAttack();
			return;
		}

		if (canUseWeapons() && isHoldingVanillaRangedWeapon() && hasLineOfSight(player)) {
			performVanillaRangedAttack(player);
			return;
		}

		// Melee fallback still needs a clear line of sight — without this, an NPC that holds
		// a ranged weapon but fails the LOS checks above (wall between it and the target)
		// would fall through to performMeleeAttack and leak knockback onto the player through
		// the wall even though no damage lands.
		if (!hasLineOfSight(player)) return;

		performMeleeAttack(player);
	}

	/**
	 * Attacks the target entity using the gangland weapon (if available and line of sight) or melee fallback. Used for
	 * NPC-to-NPC combat where the target is not a player.
	 */
	public void attackEntity(LivingEntity target) {
		if (!isValid() || target == null) return;
		if (target.isDead()) return;

		applyReactionTimeOnTargetSwitch(target);

		if (!canAttack()) return;

		faceTargetEntity(target);

		if (canUseWeapons() && heldWeapon != null && hasLineOfSight(target)) {
			performGanglandWeaponAttack();
			return;
		}

		// Melee fallback still needs a clear line of sight — see attack(Player) for rationale.
		if (!hasLineOfSight(target)) return;

		performMeleeAttackOnEntity(target);
	}

	/**
	 * Returns the distance between this NPC and the given player.
	 */
	public double distanceTo(Player player) {
		if (!isValid() || player == null) return Double.MAX_VALUE;

		LivingEntity entity = getEntity();
		if (entity == null) return Double.MAX_VALUE;

		Location entityLocation = entity.getLocation();
		Location playerLocation = player.getLocation();

		if (entityLocation.getWorld() == null || !entityLocation.getWorld().equals(playerLocation.getWorld())) {
			return Double.MAX_VALUE;
		}

		return entityLocation.distance(playerLocation);
	}

	/**
	 * Returns whether the NPC has a direct line of sight to the given player.
	 */
	public boolean hasLineOfSight(Player player) {
		if (!isValid() || player == null) return false;
		LivingEntity entity = getEntity();
		if (entity == null) return false;
		return entity.hasLineOfSight(player);
	}

	/**
	 * Returns the distance between this NPC and the given entity.
	 */
	public double distanceTo(LivingEntity target) {
		if (!isValid() || target == null) return Double.MAX_VALUE;

		LivingEntity entity = getEntity();
		if (entity == null) return Double.MAX_VALUE;

		Location entityLocation = entity.getLocation();
		Location targetLocation = target.getLocation();

		if (entityLocation.getWorld() == null || !entityLocation.getWorld().equals(targetLocation.getWorld())) {
			return Double.MAX_VALUE;
		}

		return entityLocation.distance(targetLocation);
	}

	/**
	 * Returns whether the NPC has a direct line of sight to the given entity.
	 */
	public boolean hasLineOfSight(LivingEntity target) {
		if (!isValid() || target == null) return false;
		LivingEntity entity = getEntity();
		if (entity == null) return false;
		return entity.hasLineOfSight(target);
	}

	/**
	 * Returns whether the NPC should hold position instead of closing distance (ranged hold check) for a non-player
	 * entity target.
	 */
	public boolean shouldHoldPursuitPosition(LivingEntity target) {
		if (!isUsingRangedWeapon() || !hasLineOfSight(target)) return false;
		double distance = distanceTo(target);
		return distance >= rangedMinDistance && distance <= rangedMaxDistance;
	}

	/**
	 * Navigates the NPC toward the given destination using a proactive {@link NavStep} plan. On each call the plan is
	 * consulted: if the destination changed significantly or no plan exists, a fresh plan is built by scanning the path
	 * for closed doors and ladder columns. Citizens is then handed only the current step's waypoint, keeping path
	 * segments short and always passable.
	 */
	public void navigateTo(Location destination) {
		if (!isValid() || destination == null) return;
		if (ladderClimbActive) {
			// Keep navPlanTarget fresh so a full replan doesn't fire the instant the climb
			// ends — the player may have moved several blocks during the climb, and we don't
			// want the stale navPlanTarget to trigger an immediate replan + re-climb loop.
			navPlanTarget = destination.clone();
			return;
		}

		LivingEntity entity = getEntity();
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

			// Pre-open every door in the plan NOW so Citizens sees open doorways when
			// it computes its initial path on the first setTarget call below.
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
		// Keep the final step's waypoint current so the NPC tracks a moving
		// target between full replans. Only the NONE destination step is
		// updated — obstacle steps are structural and don't move.
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
				// XZ-only distance — Y differences at ladder bases cause false negatives with 3D checks
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
		if (step.waypoint != null && shouldRecalculateNavigation(step.waypoint)) {
			npc.getNavigator().setTarget(step.waypoint);
			lastNavigationTarget    = step.waypoint.clone();
			navigationThrottleTicks = 0;

			if (replan) {
				stuckSampleTicks       = 0;
				consecutiveStuckChecks = 0;
				lastProgressLocation   = entity.getLocation().clone();
			}

			// If Citizens could not begin navigating (sync path-fail), fast-track
			// stuck detection so the NavPlan stuck limit fires sooner.
			if (!npc.getNavigator().isNavigating()) {
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
	 * Stops any current navigation and clears our tracking. Use when transitioning out of a behavior state — the next
	 * state should start with a clean slate.
	 */
	public void stopNavigation() {
		if (!isValid()) return;
		// During an active ladder climb, don't reset navigation — the climb takes priority.
		// Resetting mid-climb re-enables gravity and the NPC falls, entering an infinite
		// climb→fall→reclimb loop. The climb will complete on its own and advance the NavPlan.
		if (ladderClimbActive) return;
		npc.getNavigator().cancelNavigation();
		resetNavigationTracking();
	}

	// ── Navigation ───────────────────────────────────────────────────────────

	/**
	 * Cancels the Citizens navigator's current path without resetting our tracking state. Use this from per-tick "hold
	 * firing position" branches: the NPC stops walking, but {@link #lastNavigationTarget} and the stuck counters stay
	 * populated so subsequent {@link #navigateTo} calls respect throttling and can still escalate to
	 * {@link #isNavigationHopeless()} when the target is genuinely unreachable.
	 * <p>
	 * Calling the hard-reset {@link #stopNavigation()} from a tick-level handler whose "hold" condition flickers
	 * tick-to-tick (e.g. line-of-sight blinking through a gap mid-transit) causes the NPC to lose all accumulated stuck
	 * state, which prevents re-paths from ever firing and strands the NPC at the first obstacle.
	 */
	public void pauseNavigation() {
		if (!isValid()) return;
		if (npc.getNavigator().isNavigating()) {
			npc.getNavigator().cancelNavigation();
		}
	}

	/**
	 * Returns whether the current navigation appears stuck.
	 */
	public boolean isNavigationStuck() {
		return consecutiveStuckChecks >= maxStuckChecks;
	}

	/**
	 * Returns whether the navigation target appears permanently unreachable.
	 */
	public boolean isNavigationHopeless() {
		if (!navPlan.isEmpty()) return false;
		if (!navigationHopeless && consecutiveStuckChecks >= maxHopelessStuckChecks) {
			navigationHopeless = true;
		}
		return navigationHopeless;
	}

	/**
	 * Resolves the navigation target while pursuing a player.
	 */
	public Location resolvePursuitLocation(Player player) {
		if (!isValid() || player == null) return null;

		LivingEntity entity = getEntity();
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
	public Location resolveHopelessFallbackLocation(Player player) {
		if (!isValid() || player == null) return null;

		LivingEntity entity = getEntity();
		if (entity == null) return null;

		Location from = entity.getLocation();
		Location to   = player.getLocation();

		if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return null;

		// When path is hopeless and the player is elevated, approach the base of their
		// column before doing lateral exploration — lateral moves take the NPC sideways
		// away from the structure, while the base approach keeps it underneath.
		double heightDelta = to.getY() - from.getY();
		if (heightDelta > HEIGHT_CLIMB_THRESHOLD) {
			Location base = findBaseApproachBelowTarget(to);
			if (base != null && base.distanceSquared(from) > 4.0) return base;
			// At column base — seek a nearby climbable ladder before doing lateral exploration,
			// which would move the NPC sideways away from the structure entirely.
			Location ladderBase = findNearestLadderBase(from, to, 15);
			if (ladderBase != null) return ladderBase;
			// No ladder nearby — hold at the column base and suppress lateral exploration.
			if (base != null) return base;
		}

		if (from.distanceSquared(to) <= hopelessCloseThreshold * hopelessCloseThreshold) {
			// Close to the target but path-locked (wall between us) — re-aiming at
			// the target just retries the same failing path. Move laterally instead
			// so Citizens can compute a fresh path from a new starting position.
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
	public Location resolvePursuitLocation(LivingEntity target) {
		if (!isValid() || target == null) return null;

		Location targetLocation = target.getLocation().clone();

		Location safe = normalizeToStandableLocation(targetLocation);
		return safe != null ? safe : targetLocation;
	}

	/**
	 * Resolves the best reachable position when pathfinding has been declared hopeless for a non-player entity target.
	 */
	public Location resolveHopelessFallbackLocation(LivingEntity target) {
		if (!isValid() || target == null) return null;

		LivingEntity entity = getEntity();
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
			// See resolveHopelessFallbackLocation(Player) — same rationale. Lateral
			// exploration breaks the close-range stuck loop by picking a perpendicular
			// step instead of re-aiming at a target the NPC provably can't reach.
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
	 * <p>
	 * Probes 9 angles within ±90° of the entity's current facing direction at three distances ({@code minDist},
	 * midpoint, {@code maxDist}). Each candidate is validated with {@link #normalizeToStandableLocation} and
	 * {@link #isBasicSafeStandLocation}. The best-scoring candidate (forward + near-median distance preferred) is
	 * returned. Falls back to a full 360° sweep at 45° increments if nothing in the forward cone is walkable.
	 *
	 * @param minDist minimum probe distance in blocks
	 * @param maxDist maximum probe distance in blocks
	 *
	 * @return a validated standable location, or {@code null} if none found
	 */
	public Location findForwardWanderDestination(int minDist, int maxDist) {
		LivingEntity entity = getEntity();
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

		// Fallback: full 360° sweep
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
	 * Decrements the attack cooldown by one server tick. Called once per server tick (period = 0), so cooldown values
	 * are always in server ticks regardless of the logical AI tick rate.
	 */
	protected void decrementAttackCooldown() {
		if (attackCooldown > 0) attackCooldown--;
	}

	/**
	 * Updates navigation progress tracking for throttling and stuck detection.
	 */
	protected void updateNavigationProgress() {
		navigationThrottleTicks++;

		LivingEntity entity = getEntity();

		if (entity == null) {
			// Entity temporarily null (e.g. Citizens skin respawn) — skip this tick entirely.
			// Don't reset navigation; the plan and climb state will resume when the entity returns.
			return;
		}

		if (ladderClimbActive) {
			tickLadderClimb();
			return;
		}

		// NavPlan step-level stuck detection — force a full replan when the NPC
		// spends too long on a single step without advancing to the next waypoint.
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
			// Real movement detected — the current hopeless commitment (if any) has
			// served its purpose of getting the NPC to a new position, so drop it
			// and let the regular pursuit branch take over from the fresh location.
			consecutiveStuckChecks = 0;
			navigationHopeless     = false;
			committedLateralSpot   = null;
			lastProgressLocation   = currentLocation.clone();
		}

		stuckSampleTicks = 0;
	}

	// ── Tick helpers (called by subclass tick methods) ───────────────────────

	protected void faceTarget(Player player) {
		Entity entity = npc.getEntity();
		if (entity == null) return;

		Location shooterEye = entity.getLocation().add(0, 1.6, 0);
		Vector   direction  = player.getEyeLocation().toVector().subtract(shooterEye.toVector()).normalize();
		direction = applyAimError(direction);
		Location rotated = entity.getLocation().setDirection(direction);
		entity.teleport(rotated);
	}

	protected void faceTargetEntity(LivingEntity target) {
		Entity entity = npc.getEntity();
		if (entity == null) return;

		Location shooterEye = entity.getLocation().add(0, 1.6, 0);
		Vector   direction  = target.getEyeLocation().toVector().subtract(shooterEye.toVector()).normalize();
		direction = applyAimError(direction);
		Location rotated = entity.getLocation().setDirection(direction);
		entity.teleport(rotated);
	}

	// ── Private attack helpers ────────────────────────────────────────────────

	protected void performGanglandWeaponAttack() {
		if (!(heldWeapon instanceof GunWeapon gun)) return;

		if (heldWeapon.isBroken() || heldWeapon.isMagazineEmpty()) {
			triggerReload();
			return;
		}

		SelectiveFire mode = gun.getCurrentSelectiveFire();
		if (mode == null) mode = SelectiveFire.AUTO;

		switch (mode) {
			case SINGLE -> performSingleShot(gun);
			case BURST -> performBurstFire(gun);
			case AUTO -> performAutoShot(gun);
		}
	}

	protected void triggerReload() {
		if (plugin == null || heldWeapon == null) return;
		if (heldWeapon.isReloading()) return;
		if (heldWeapon.getReloadData() == null) return;
		// player = null: skips all player UI (sounds, action bar, scope) but uses the weapon's
		// actual reload timer so isReloading() gates canAttack() correctly.
		// removeAmmunition = false: NPCs have no inventory to deduct from.
		heldWeapon.reload(plugin, null, false);
	}

	protected void performVanillaRangedAttack(Player player) {
		LivingEntity shooter = getEntity();
		if (shooter == null) return;

		World    world     = shooter.getWorld();
		Location eye       = shooter.getEyeLocation();
		Vector   direction = eye.getDirection().normalize();

		var result = world.rayTrace(eye, direction, 35.0, FluidCollisionMode.NEVER, true, 0.25,
		                            entity -> entity instanceof Player p &&
		                                      p.getUniqueId().equals(player.getUniqueId()));

		world.spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(0.5)), 6, 0.05, 0.05, 0.05, 0.0);
		world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.6f);

		if (result != null && result.getHitEntity() != null) {
			player.damage(getAttackDamage(), shooter);
		}

		attackCooldown = scaleCooldown(15);
	}

	protected void performMeleeAttack(Player player) {
		if (!isValid() || player == null) return;

		LivingEntity entity = getEntity();
		if (entity == null) return;

		double healthBefore = player.getHealth();
		player.damage(getAttackDamage() * difficulty.getMeleeDamageMultiplier(), entity);
		attackCooldown = scaleCooldown(5);

		// Only apply knockback if the hit actually landed — otherwise an absorbed/cancelled
		// damage event would still leave an unexplained velocity push on the target.
		if (player.isDead() || player.getHealth() < healthBefore) {
			Vector knockback = player.getLocation()
			                         .toVector()
			                         .subtract(entity.getLocation().toVector())
			                         .normalize()
			                         .multiply(0.3)
			                         .setY(0.1);
			player.setVelocity(player.getVelocity().add(knockback));
		}
	}

	protected void performMeleeAttackOnEntity(LivingEntity target) {
		if (!isValid() || target == null) return;

		LivingEntity entity = getEntity();
		if (entity == null) return;

		double healthBefore = target.getHealth();
		target.damage(getAttackDamage() * difficulty.getMeleeDamageMultiplier(), entity);
		attackCooldown = scaleCooldown(5);

		// Only apply knockback if the hit actually landed — see performMeleeAttack for rationale.
		if (target.isDead() || target.getHealth() < healthBefore) {
			Vector knockback = target.getLocation()
			                         .toVector()
			                         .subtract(entity.getLocation().toVector())
			                         .normalize()
			                         .multiply(0.3)
			                         .setY(0.1);
			target.setVelocity(target.getVelocity().add(knockback));
		}
	}

	protected void refreshHeldItem() {
		if (!isValid() || heldWeapon == null) return;

		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return;

		ItemStack current = equipment.getItemInMainHand();
		if (current.getType() == Material.AIR) return;

		ItemBuilder builder = new ItemBuilder(current);
		heldWeapon.updateWeaponData(builder);
		equipment.setItemInMainHand(builder.build());
	}

	protected boolean isHoldingVanillaRangedWeapon() {
		if (!isValid()) return false;

		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return false;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return false;

		ItemStack mainHand = equipment.getItemInMainHand();
		Material  type     = mainHand.getType();

		return type == Material.BOW || type == Material.CROSSBOW;
	}

	/**
	 * Picks a standable location offset from {@code from} in one of {@value #LATERAL_EXPLORATION_DIRECTIONS} angular
	 * directions around the NPC-to-target line. Used when the NPC is close to its target but path-locked by an obstacle
	 * between them — navigating to an off-axis point breaks the stuck loop by forcing the NPC into a new position from
	 * which Citizens can compute a different path to the real target next tick.
	 * <p>
	 * Caches its result in {@link #committedLateralSpot} for {@value #LATERAL_COMMITMENT_MS} ms so that back-to-back
	 * calls from the same hopeless-fallback tick loop return the same spot, and the NPC actually has time to walk
	 * toward it before a new direction is probed. When the commitment window expires {@link #lateralExplorationSide}
	 * advances so the next call starts from a different angle — cycling through all 8 directions means a U-shape that
	 * blocks the first few candidates will still eventually have at least one walkable escape spot found.
	 * <p>
	 * Each call tries all 8 directions (starting from {@code lateralExplorationSide}) at radii 5, 7 and 9 blocks. The
	 * first standable, far-enough candidate wins.
	 */
	private Location resolveLateralExploration(Location from, Location to) {
		long now = System.currentTimeMillis();

		if (committedLateralSpot != null && now < committedLateralExpiresAtMs &&
		    committedLateralSpot.getWorld() != null && committedLateralSpot.getWorld().equals(from.getWorld())) {
			return committedLateralSpot;
		}

		// Commitment expired or never computed — rotate to the next angular
		// direction before picking a new candidate so repeated failures eventually
		// probe every direction around the NPC-target line, not just two flanks.
		if (committedLateralSpot != null) {
			lateralExplorationSide++;
		}

		Vector toTarget = to.toVector().subtract(from.toVector()).setY(0);
		if (toTarget.lengthSquared() < 1e-4) {
			committedLateralSpot = null;
			return null;
		}
		toTarget.normalize();

		// Iterate every direction (45° increments) starting from the current side,
		// trying a few radii each. We give the currently committed direction first
		// dibs, but if it has no standable spots we immediately fall through to the
		// other directions in the same call — much better than waiting another full
		// commitment cycle just to find out the current side is completely blocked.
		double[] radii = {5.0, 7.0, 9.0};
		int      start = Math.floorMod(lateralExplorationSide, LATERAL_EXPLORATION_DIRECTIONS);

		for (int offset = 0; offset < LATERAL_EXPLORATION_DIRECTIONS; offset++) {
			int    sideIndex = Math.floorMod(start + offset, LATERAL_EXPLORATION_DIRECTIONS);
			double angleRad  = Math.toRadians(90.0 + (360.0 / LATERAL_EXPLORATION_DIRECTIONS) * sideIndex);
			double cos       = Math.cos(angleRad);
			double sin       = Math.sin(angleRad);

			// Rotate the toTarget vector by (90° + sideIndex * 45°) around Y.
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

	/**
	 * Configures this NPC's Citizens navigator with the defaults the gangland AI relies on.
	 * <p>
	 * <b>range(64)</b> — Citizens' default pathfinding search budget is ~25 blocks. That is
	 * enough for short direct approaches but too small to find a walk-around route for 2- or 3-block obstacles when the
	 * target is a few blocks behind cover; the pathfinder truncates at the foot of the wall, reports an idle navigator,
	 * and our stuck detector then parks the NPC there. Raising the range to 64 blocks gives it enough budget to explore
	 * a route around the obstacle before it gives up.
	 * <p>
	 * <b>useNewPathfinder(false)</b> — when Citizens' "new pathfinder" flag is on (the modern
	 * default), Citizens delegates to the mob entity's own Mojang {@code PathNavigation} for
	 * {@link org.bukkit.entity.Mob}-type NPCs. That native pathfinder has its own hardcoded search budget (~24 blocks
	 * in modern Minecraft) that ignores our {@code range} setting, so civilians spawned as {@code EntityType.ZOMBIE} /
	 * {@code VILLAGER} / etc. can't find walk-around routes longer than a handful of blocks. Cops avoid this because
	 * they spawn as {@code EntityType.PLAYER}, which Citizens always routes through its own A* pathfinder regardless of
	 * this flag. Forcing {@code useNewPathfinder(false)} here makes mob-type civilians fall through to the same
	 * Citizens A* path cops use, so they finally obey the {@code range(64)} setting and can navigate around the U-shape
	 * walls the user was seeing them get stuck against.
	 * <p>
	 */
	private void configurePathfinder() {
		if (npc == null) return;
		NavigatorParameters params = npc.getNavigator().getDefaultParameters();
		params.range(64f);
		params.pathfinderType(PathfinderType.MINECRAFT);
	}

	/**
	 * SINGLE fire mode: fires one shot, then locks for {@code perShot * cooldown} ticks (same window as player
	 * weapons).
	 */
	private void performSingleShot(GunWeapon gun) {
		if (!fireSingleRound(gun)) return;

		int perShot  = Math.max(gun.getProjectileData().getPerShot(), 1);
		int cooldown = Math.max(gun.getProjectileData().getCooldown(), 1);
		attackCooldown = scaleCooldown(Math.max(perShot * cooldown, 5));

		if (heldWeapon.isMagazineEmpty()) {
			triggerReload();
		}
	}

	/**
	 * AUTO fire mode: fires one shot per call, cooldown equals base weapon cooldown. This is the original behavior —
	 * the AI tick loop naturally creates continuous fire.
	 */
	private void performAutoShot(GunWeapon gun) {
		if (!fireSingleRound(gun)) return;

		int cooldown = gun.getProjectileData().getCooldown();
		attackCooldown = scaleCooldown(Math.max(cooldown, 5));

		if (heldWeapon.isMagazineEmpty()) {
			triggerReload();
		}
	}

	/**
	 * BURST fire mode: fires {@code perShot} rounds spaced by {@code cooldown} ticks each using a
	 * {@link SequenceTimer}. The attack cooldown covers the full burst duration plus a post-burst pause equal to one
	 * cooldown interval.
	 */
	private void performBurstFire(GunWeapon gun) {
		if (plugin == null) return;

		int perShot  = Math.max(gun.getProjectileData().getPerShot(), 1);
		int cooldown = Math.max(gun.getProjectileData().getCooldown(), 1);

		// Lock attackCooldown for the full burst + one extra interval as post-burst pause.
		// This prevents canAttack() from returning true mid-burst, since the behavior callers
		// call attack() every AI tick.
		int totalBurstTicks = perShot * cooldown + cooldown;
		attackCooldown = scaleCooldown(Math.max(totalBurstTicks, 5));

		SequenceTimer burstTimer = new SequenceTimer(plugin, 1L, 1L);

		for (int i = 0; i < perShot; i++) {
			int interval = i == 0 ? 0 : cooldown;

			burstTimer.addIntervalTaskPair(interval, timer -> {
				fireSingleRound(gun);

				if (heldWeapon != null && heldWeapon.isMagazineEmpty()) {
					triggerReload();
				}
			});
		}

		burstTimer.start(false);
	}

	/**
	 * Fires a single round from the given gun weapon: consumes ammo, fires the {@link WeaponShootEvent}, resolves the
	 * raytracer, and plays sound effects.
	 *
	 * @return {@code true} if the shot was fired, {@code false} if ammo was empty or the event was cancelled
	 */
	private boolean fireSingleRound(GunWeapon gun) {
		if (heldWeapon.isBroken() || heldWeapon.isMagazineEmpty()) {
			triggerReload();
			return false;
		}

		boolean consumed = heldWeapon.consumeShot();
		if (!consumed) {
			triggerReload();
			return false;
		}

		LivingEntity shooter = getEntity();
		if (shooter == null) return false;

		WeaponShootEvent event = new WeaponShootEvent(heldWeapon, shooter);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			heldWeapon.addAmmunition(1);
			return false;
		}

		var registration = Bukkit.getServicesManager().getRegistration(WeaponRaytracer.class);
		if (registration != null) {
			WeaponShooting.fire(plugin, registration.getProvider(), shooter, gun);
		}
		SoundConfiguration.playSoundsAtLocation(shooter.getEyeLocation(), heldWeapon.getSoundData().getShotCustom(),
		                                        heldWeapon.getSoundData().getShotDefault());
		refreshHeldItem();
		return true;
	}

	/**
	 * Strips any protection that Citizens or Minecraft may have (re-)applied to this NPC's entity. Called every tick
	 * via {@link #isValid()} so the entity can never stay invulnerable for more than one tick.
	 */
	private void ensureDamageable() {
		if (npc.isProtected()) {
			npc.setProtected(false);
		}

		Entity entity = npc.getEntity();
		if (entity == null) return;

		if (entity.isInvulnerable()) {
			entity.setInvulnerable(false);
		}
	}

	/**
	 * Applies a difficulty-driven random angular offset to the given aim direction. Because {@code WeaponShooting}
	 * later reads {@code shooter.getEyeLocation().getDirection()} after the NPC has been teleported to face the
	 * returned vector, the imperfect aim propagates into the bullet path with no changes required to the weapon module.
	 * Weapon-level spread compounds on top of this naturally via {@code SpreadManager.applySpread}.
	 */
	private Vector applyAimError(Vector direction) {
		double error = difficulty.getAimError();
		if (error <= 0) return direction;

		ThreadLocalRandom rng     = ThreadLocalRandom.current();
		double            offsetX = (rng.nextDouble() - 0.5) * error;
		double            offsetY = (rng.nextDouble() - 0.5) * error;
		double            offsetZ = (rng.nextDouble() - 0.5) * error;
		return direction.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
	}

	/**
	 * Injects a difficulty-driven reaction-time delay into {@link #attackCooldown} on the first attack against a
	 * freshly acquired target. The next {@link #canAttack()} check after this method runs will return false until the
	 * reaction window elapses, then sustained fire on the same target uses the normal per-shot cooldown.
	 */
	private void applyReactionTimeOnTargetSwitch(LivingEntity target) {
		if (target == previousAttackTarget) return;
		previousAttackTarget = target;

		int reaction = difficulty.getReactionTimeTicks();
		if (reaction > 0 && attackCooldown < reaction) {
			attackCooldown = reaction;
		}
	}

	// ── Navigation helpers ────────────────────────────────────────────────────

	/**
	 * Scales a base cooldown value by the difficulty's fire-rate multiplier, with a minimum floor of 5 ticks so an
	 * aggressive multiplier cannot collapse the cooldown to zero.
	 */
	private int scaleCooldown(int baseCooldown) {
		int scaled = (int) Math.round(baseCooldown * difficulty.getFireRateMultiplier());
		return Math.max(scaled, 5);
	}

	private boolean shouldRecalculateNavigation(Location target) {
		if (target == null) return false;

		// First navigation, or previous target is in a different/unloaded world —
		// unconditionally accept.
		if (lastNavigationTarget == null || lastNavigationTarget.getWorld() == null || target.getWorld() == null ||
		    !lastNavigationTarget.getWorld().equals(target.getWorld())) {
			return true;
		}

		boolean targetMoved = lastNavigationTarget.distanceSquared(target) >= TARGET_MOVED_THRESHOLD_SQ;

		// Respect the throttle window unless the target genuinely moved. Re-pathing
		// against an idle navigator is what produces the "glitch" — Citizens completes
		// its path to the closest reachable point, goes idle, and we keep resetting
		// setTarget for the same destination, which also resets stuck detection so
		// isNavigationHopeless() never fires.
		if (navigationThrottleTicks < navigationRecalculationTicks) {
			return targetMoved;
		}

		// Throttle window elapsed — re-path if the target moved or the NPC is stuck.
		return targetMoved || isNavigationStuck();
	}

	private void resetNavigationTracking() {
		lastNavigationTarget    = null;
		lastProgressLocation    = null;
		navigationThrottleTicks = navigationRecalculationTicks;
		stuckSampleTicks        = 0;
		consecutiveStuckChecks  = 0;
		navigationHopeless      = false;
		committedLateralSpot    = null;

		// Re-enable gravity if we were mid-climb before clearing the climb state
		if (ladderClimbActive) {
			LivingEntity entity = getEntity();
			if (entity != null) {
				entity.setGravity(true);
			}
		}

		ladderClimbActive    = false;
		ladderClimbBlock     = null;
		ladderClimbTickCount = 0;

		// Preserve the NavPlan across resets so the NPC can resume where it left off
		// after a temporary stop (behavior transition, target flicker). The plan is only
		// fully cleared on an explicit replan inside navigateTo() when the destination
		// changes significantly.
		navPlanStuckTicks = 0;
	}

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

				if (isUsingRangedWeapon()) {
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

	private boolean isSafeStandLocation(Location location) {
		return isBasicSafeStandLocation(location) && !isFrontedByImmediateGap(location);
	}

	private boolean isFrontedByImmediateGap(Location location) {
		LivingEntity entity = getEntity();
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

	private boolean isBasicSafeStandLocation(Location location) {
		if (location == null || location.getWorld() == null) return false;

		Block feet  = location.getBlock();
		Block head  = feet.getRelative(0, 1, 0);
		Block below = feet.getRelative(0, -1, 0);

		if (!feet.isPassable() || !head.isPassable()) return false;

		// Portal blocks are passable so they pass the checks above, but NPCs must never target them
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

	/**
	 * Finds a standable ground-level location at the same X/Z as {@code target} but near the calling NPC's current Y
	 * level. Used when the target is significantly elevated — navigating here instead moves the NPC to the base of the
	 * column/structure the target is perched on, keeping it close rather than wandering sideways.
	 */
	private Location findBaseApproachBelowTarget(Location target) {
		if (target == null || target.getWorld() == null) return null;

		LivingEntity entity = getEntity();
		if (entity == null) return null;

		World world = target.getWorld();
		int   baseX = target.getBlockX();
		int   baseZ = target.getBlockZ();
		int   npcY  = entity.getLocation().getBlockY();

		// Search in an expanding ring around the target's XZ at approximately NPC Y level.
		// Radius 0 = directly below the target; expanding rings find open ground when the
		// target is perched on a solid structure (e.g. tower) whose column is all-solid.
		for (int radius = 0; radius <= 4; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue; // shell only
					Location probe     = new Location(world, baseX + dx + 0.5, npcY, baseZ + dz + 0.5);
					Location standable = normalizeToStandableLocation(probe);
					if (standable != null) return standable;
				}
			}
		}

		return null;
	}

	/**
	 * Scans the corridor between the NPC and the target for ladder columns, searching a rectangular area centred on the
	 * midpoint with a Y range that covers both the NPC's and the target's elevations. Used to guide NPCs toward a
	 * climbable route when the target is elevated and no direct Citizens path exists. Returns the closest result (by
	 * squared distance from {@code from}) among all XZ cells in the search area.
	 *
	 * @param from the NPC's current location — used for proximity sorting and midpoint calculation
	 * @param to the elevated target location — used for midpoint calculation and Y-scan range
	 * @param searchRadius XZ search radius in blocks (measured from the midpoint)
	 *
	 * @return a standable location at the base of the nearest ladder column, or {@code null} if none found
	 */
	private Location findNearestLadderBase(Location from, Location to, int searchRadius) {
		if (to == null || to.getWorld() == null) return null;

		World world   = to.getWorld();
		int   npcY    = from != null ? from.getBlockY() : to.getBlockY();
		int   targetY = to.getBlockY();

		// Centre search on the midpoint between NPC and destination so ladders anywhere
		// in the corridor are found — not just those near the destination.
		int centerX = from != null ? (from.getBlockX() + to.getBlockX()) / 2 : to.getBlockX();
		int centerZ = from != null ? (from.getBlockZ() + to.getBlockZ()) / 2 : to.getBlockZ();

		// Symmetric Y range covering both NPC and target elevations
		int minScanY = Math.max(world.getMinHeight(), Math.min(npcY, targetY) - 2);
		int maxScanY = Math.min(world.getMaxHeight() - 1, Math.max(npcY, targetY) + 2);

		Location nearest     = null;
		double   nearestDist = Double.MAX_VALUE;

		for (int dx = -searchRadius; dx <= searchRadius; dx++) {
			for (int dz = -searchRadius; dz <= searchRadius; dz++) {
				for (int y = minScanY; y <= maxScanY; y++) {
					if (world.getBlockAt(centerX + dx, y, centerZ + dz).getType() != Material.LADDER) continue;

					// Find the lowest rung of this ladder column
					int bottomY = y;
					while (bottomY > minScanY &&
					       world.getBlockAt(centerX + dx, bottomY - 1, centerZ + dz).getType() == Material.LADDER) {
						bottomY--;
					}

					// Raw position at the bottom rung — no normalizeToStandableLocation here
					// because wall-mounted ladders often have no solid ground in their own
					// XZ column. The approach waypoint (built in buildNavPlan) handles
					// placing the NPC on solid ground nearby.
					Location base = new Location(world, centerX + dx + 0.5, bottomY, centerZ + dz + 0.5);

					double dist = from != null ? from.distanceSquared(base) : 0;
					if (dist < nearestDist) {
						nearestDist = dist;
						nearest     = base;
					}

					break; // Only consider the bottom of each XZ column's ladder
				}
			}
		}

		return nearest;
	}

	/**
	 * Drives one tick of manual ladder climbing. Teleports the NPC along the ladder column each tick for deterministic,
	 * physics-independent climbing (both ascending and descending). Exits climb mode once the NPC reaches the
	 * destination Y or a safety timeout fires, then advances the NavPlan so the next step begins.
	 */
	private void tickLadderClimb() {
		LivingEntity entity = getEntity();
		if (entity == null) {
			// Entity temporarily null (e.g. Citizens skin respawn) — skip this tick
			// but keep the climb active so it resumes when the entity returns.
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

		double currentY = entity.getLocation().getY();
		boolean arrived = ladderClimbDescending
		                  ? currentY <= ladderClimbDestY
		                  : currentY >= ladderClimbDestY;

		if (arrived) {
			endLadderClimb(entity, false);
			return;
		}

		// Teleport-based climb — deterministic movement with gravity disabled.
		// This method is called every aiTickRate server ticks (default 10), so each step
		// must cover enough distance to produce visible progress per call.
		Location cur       = entity.getLocation();
		double   remaining = Math.abs(ladderClimbDestY - cur.getY());
		double   stepY     = Math.min(1.0, remaining);
		double   targetX   = ladderClimbBlock != null ? ladderClimbBlock.getX() + 0.5 : cur.getX();
		double   targetZ   = ladderClimbBlock != null ? ladderClimbBlock.getZ() + 0.5 : cur.getZ();
		double   targetY   = ladderClimbDescending ? cur.getY() - stepY : cur.getY() + stepY;

		Location dest = new Location(cur.getWorld(), targetX, targetY, targetZ, cur.getYaw(), cur.getPitch());
		entity.teleport(dest);
	}

	/**
	 * Ends ladder climbing — re-enables gravity, clears climb state, and optionally advances the NavPlan.
	 *
	 * @param entity the climbing entity
	 * @param timedOut true if the climb ended due to timeout (forces replan), false on normal completion
	 */
	private void endLadderClimb(LivingEntity entity, boolean timedOut) {
		// Before re-enabling gravity, move the NPC off the ladder column onto solid ground.
		// For ascending: onto the platform at the top. For descending: onto the ground at the base.
		// Without this the NPC falls back through the passable ladder blocks.
		if (!timedOut && ladderClimbBlock != null) {
			Location exitLoc = findStandableAtLadderExit(entity, ladderClimbBlock, ladderClimbDestY);
			if (exitLoc != null) {
				entity.teleport(exitLoc);
			}
		}

		ladderClimbActive = false;
		ladderClimbBlock  = null;
		entity.setGravity(true);

		// Force a full replan on the next navigateTo call. After a climb the NPC is at a
		// different Y level — doors and obstacles along the remaining path may differ from
		// what was scanned at the original elevation (e.g. descending a ladder then walking
		// through a ground-level door to reach spawn).
		navPlanTarget          = null;
		consecutiveStuckChecks = 0;
		stuckSampleTicks       = 0;
		lastProgressLocation   = entity.getLocation().clone();
		lastNavigationTarget   = null;
	}

	/**
	 * Finds a standable location at the exit of a ladder climb. For ascending, checks neighbours at the top Y. For
	 * descending, checks neighbours at the bottom Y. The NPC is teleported here so it steps off the ladder onto solid
	 * ground instead of falling back through the passable ladder blocks.
	 *
	 * @param entity the climbing entity (used for yaw/pitch)
	 * @param ladder the ladder block
	 * @param exitY the Y level to exit at (destY from the climb)
	 */
	private Location findStandableAtLadderExit(LivingEntity entity, Block ladder, int exitY) {
		World world = ladder.getWorld();
		int   lx    = ladder.getX();
		int   lz    = ladder.getZ();

		int standY = exitY;

		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : dirs) {
			int nx = lx + d[0];
			int nz = lz + d[1];

			Block below = world.getBlockAt(nx, standY - 1, nz);
			Block feet  = world.getBlockAt(nx, standY, nz);
			Block head  = world.getBlockAt(nx, standY + 1, nz);

			if (below.getType().isSolid() && feet.isPassable() && head.isPassable()) {
				return new Location(world, nx + 0.5, standY, nz + 0.5,
				                    entity.getLocation().getYaw(), entity.getLocation().getPitch());
			}
		}

		// Fallback: try the ladder column itself
		return normalizeToStandableLocation(
				new Location(world, lx + 0.5, standY, lz + 0.5));
	}

	// ── NavPlan — proactive path planning ────────────────────────────────────

	/**
	 * Scans the path from the NPC to {@code destination} and builds an ordered list of {@link NavStep} entries. The
	 * scan walks the horizontal XZ path at the NPC's floor Y (not the destination's Y) so that doors — which are always
	 * at ground level — are detected regardless of elevation difference. After the horizontal scan, if the destination
	 * is significantly elevated, a radial search for a climbable ladder column is performed.
	 *
	 * @param destination the final navigation target
	 *
	 * @return an ordered list of steps; always contains at least the destination step
	 */
	private List<NavStep> buildNavPlan(Location destination) {
		List<NavStep> steps = new ArrayList<>();

		LivingEntity entity = getEntity();
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
					org.bukkit.block.data.BlockData raw = block.getBlockData();
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
			// Dynamic radius — covers the full NPC↔destination corridor
			double xzDist = Math.sqrt(Math.pow(destination.getX() - from.getX(), 2) +
			                          Math.pow(destination.getZ() - from.getZ(), 2));
			int ladderSearchRadius = Math.min(30, Math.max(15, (int) Math.ceil(xzDist / 2) + 5));

			Location ladderBase = findNearestLadderBase(from, destination, ladderSearchRadius);

			if (ladderBase != null) {
				ladderBlock = findLadderBlockAtBase(ladderBase);

				if (ladderBlock != null) {
					// For ascending: approach waypoint 1 block toward NPC from ladder, at NPC ground level.
					// For descending: approach waypoint 1 block toward NPC from ladder, at ladder TOP level.
					// Citizens paths to this solid-ground waypoint; the climb activates on arrival.
					double ladderX = ladderBlock.getX() + 0.5;
					double ladderZ = ladderBlock.getZ() + 0.5;
					double dirX    = from.getX() - ladderX;
					double dirZ    = from.getZ() - ladderZ;
					double dirLen  = Math.sqrt(dirX * dirX + dirZ * dirZ);

					if (dirLen > 0.01) {
						// For descending, find the top of the ladder column so we can place the
						// approach waypoint at the correct Y level
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

						Location rawApproach = new Location(world,
						                                    ladderX + dirX / dirLen,
						                                    approachY,
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
			// OPEN_DOOR waypoint = ladder approach if a ladder follows, or destination
			Location doorWaypoint = ladderApproach != null ? ladderApproach : destStandable;
			steps.add(new NavStep(doorWaypoint, NavObstacle.OPEN_DOOR, foundDoor));
		}

		if (ladderApproach != null) {
			steps.add(new NavStep(ladderApproach, NavObstacle.CLIMB_LADDER, ladderBlock, ladderDescending));
		}

		steps.add(new NavStep(destStandable, NavObstacle.NONE, null));
		return steps;
	}

	/**
	 * Finds a {@link Material#LADDER} block at or immediately adjacent to {@code base}. Checks the base column first,
	 * then the 4 cardinal neighbours at foot level and one block above.
	 */
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

	/**
	 * Executes the obstacle pre-action for the given {@link NavStep}. Currently handles {@link NavObstacle#OPEN_DOOR}:
	 * opens the door block and schedules it to close after 2 seconds.
	 */
	private void executeObstacleAction(NavStep step) {
		if (step.obstacle != NavObstacle.OPEN_DOOR) return;
		if (plugin == null || step.obstacleBlock == null) return;

		Block                           door = step.obstacleBlock;
		org.bukkit.block.data.BlockData raw  = door.getBlockData();
		if (!(raw instanceof Openable openable) || openable.isOpen()) return;

		boolean isIron     = door.getType() == Material.IRON_DOOR;
		Sound   closeSound = isIron ? Sound.BLOCK_IRON_DOOR_CLOSE : Sound.BLOCK_WOODEN_DOOR_CLOSE;
		openable.setOpen(true);
		door.setBlockData(openable);
		door.getWorld()
		    .playSound(door.getLocation(), isIron ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 1f);

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			org.bukkit.block.data.BlockData current = door.getBlockData();
			if (current instanceof Openable toClose && toClose.isOpen()) {
				toClose.setOpen(false);
				door.setBlockData(toClose);
				door.getWorld().playSound(door.getLocation(), closeSound, 1f, 1f);
			}
		}, 40L);
	}

	/**
	 * Activates manual ladder-climb mode for the given ladder block. Scans the column to find the extent, then cancels
	 * Citizens navigation so the climb is driven entirely by {@link #tickLadderClimb()}.
	 *
	 * @param ladder the ladder block to climb
	 * @param descending true to climb down, false to climb up
	 */
	private void activateLadderClimb(Block ladder, boolean descending) {
		if (ladder == null || ladderClimbActive) return;

		World world = ladder.getWorld();
		if (world == null) return;

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
		npc.getNavigator().cancelNavigation();

		// Disable gravity so the teleport-based climb isn't undone by physics between
		// AI ticks (which fire every aiTickRate server ticks, not every tick).
		LivingEntity climbEntity = getEntity();
		if (climbEntity != null) {
			climbEntity.setGravity(false);
		}

	}

	// ── NavPlan inner types ──────────────────────────────────────────────────

	private enum NavObstacle {
		NONE,
		OPEN_DOOR,
		CLIMB_LADDER
	}

	private static final class NavStep {

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
}
