package me.luckyraven.copsncrooks.police.npc;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.config.CopTierConfig;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopState;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@CustomLog
public class CopNpc {

	private final int    navigationRecalculationTicks;
	private final int    stuckCheckIntervalTicks;
	private final int    maxStuckChecks;
	private final int    maxHopelessStuckChecks;
	private final double hopelessCloseThreshold;
	private final double minProgressDistanceSquared;
	private final double rangedMinDistance;
	private final double rangedMaxDistance;

	@Getter
	private final NPC                        npc;
	@Getter
	private final CopTierConfig              tierConfig;
	private final Map<CopState, CopBehavior> behaviors;
	@Getter
	private final Location                   spawnLocation;
	private final int                        aiTickRate;

	@Getter
	private CopState currentState;
	@Setter
	@Getter
	private UUID     targetPlayerId;
	private int      attackCooldown;
	@Getter
	private boolean  markedForRemoval;
	@Getter
	@Setter
	private boolean  combatForced;
	@Getter
	@Setter
	private int      despawnTicks;

	@Getter
	private Weapon     heldWeapon;
	private boolean    reloading;
	private JavaPlugin plugin;

	private Location lastNavigationTarget;
	private Location lastProgressLocation;
	private int      navigationThrottleTicks;
	private int      stuckSampleTicks;
	private int      consecutiveStuckChecks;
	private boolean  navigationHopeless;

	public CopNpc(NPC npc, CopTierConfig tierConfig, Map<CopState, CopBehavior> behaviors, Location spawnLocation,
				  CopConfigProvider configProvider) {
		this.npc                    = npc;
		this.tierConfig             = tierConfig;
		this.behaviors              = behaviors;
		this.spawnLocation          = spawnLocation;
		this.aiTickRate             = configProvider.getAiTickRate();
		this.currentState           = CopState.IDLE;
		this.attackCooldown         = 0;
		this.markedForRemoval       = false;
		this.despawnTicks           = 0;
		this.reloading              = false;
		this.stuckSampleTicks       = 0;
		this.consecutiveStuckChecks = 0;

		this.navigationRecalculationTicks = configProvider.getNavigationRecalculationTicks();
		this.stuckCheckIntervalTicks      = configProvider.getStuckCheckIntervalTicks();
		this.maxStuckChecks               = configProvider.getMaxStuckChecks();
		this.maxHopelessStuckChecks       = configProvider.getMaxHopelessStuckChecks();
		this.hopelessCloseThreshold       = configProvider.getHopelessCloseThreshold();
		double minProg = configProvider.getMinProgressDistance();
		this.minProgressDistanceSquared = minProg * minProg;
		this.rangedMinDistance          = configProvider.getRangedMinDistance();
		this.rangedMaxDistance          = configProvider.getRangedMaxDistance();

		this.navigationThrottleTicks = this.navigationRecalculationTicks;
	}

	/**
	 * Assigns the gangland Weapon this cop will fire and the plugin needed for reload scheduling. Call this before
	 * {@link #equip()} so the weapon item ends up in the main hand.
	 *
	 * @param weapon the weapon instance, or null to clear
	 * @param plugin the owning plugin
	 */
	public void setHeldWeapon(Weapon weapon, JavaPlugin plugin) {
		this.heldWeapon = weapon;
		this.plugin     = plugin;
	}

	/**
	 * Marks this cop for removal during the next cleanup cycle.
	 */
	public void markForRemoval() {
		this.markedForRemoval = true;
	}

	/**
	 * Returns the living entity associated with this NPC.
	 *
	 * @return the living entity
	 */
	public LivingEntity getEntity() {
		return (LivingEntity) npc.getEntity();
	}

	/**
	 * Returns whether the NPC is currently spawned and alive.
	 *
	 * @return true if valid
	 */
	public boolean isValid() {
		return npc.isSpawned() && npc.getEntity() != null && !npc.getEntity().isDead();
	}

	/**
	 * Transitions the cop to a new AI state, invoking exit/enter callbacks.
	 *
	 * @param newState the target state
	 */
	public void transitionTo(CopState newState) {
		log.debug("Transitioning cop {}-{} from {} state to {} state.", npc.getName(), npc.getId(), currentState,
				  newState);
		if (currentState == newState) return;

		CopBehavior oldBehavior = behaviors.get(currentState);
		if (oldBehavior != null) {
			oldBehavior.onExit(this);
		}

		currentState = newState;

		CopBehavior newBehavior = behaviors.get(currentState);

		if (newBehavior == null) return;

		newBehavior.onEnter(this);
	}

	/**
	 * Runs a single AI tick using the current state's behavior.
	 *
	 * @param target the current target player, or null
	 */
	public void tick(Player target) {
		if (!isValid()) {
			markForRemoval();
			return;
		}

		decrementAttackCooldown();
		updateNavigationProgress();

		CopBehavior behavior = behaviors.get(currentState);

		if (behavior == null) return;

		behavior.tick(this, target);
	}

	/**
	 * Returns whether this cop is currently using a ranged weapon.
	 */
	public boolean isUsingRangedWeapon() {
		if (!tierConfig.canUseWeapons()) {
			return false;
		}

		return heldWeapon != null || isHoldingVanillaRangedWeapon();
	}

	/**
	 * Returns whether the cop should hold position instead of closing distance further. Intended for ranged cops that
	 * already have a good firing angle.
	 */
	public boolean shouldHoldPursuitPosition(Player player) {
		if (!isUsingRangedWeapon() || !hasLineOfSight(player)) {
			return false;
		}

		double distance = distanceTo(player);
		return distance >= rangedMinDistance && distance <= rangedMaxDistance;
	}

	/**
	 * Returns whether the current navigation appears stuck (NPC has made no progress for several samples).
	 */
	public boolean isNavigationStuck() {
		return consecutiveStuckChecks >= maxStuckChecks;
	}

	/**
	 * Returns whether the navigation target appears permanently unreachable. Latches to {@code true} once the threshold
	 * is first crossed and stays {@code true} until {@link #stopNavigation()} is called, so that a small fallback
	 * movement does not reset the counter and restart the full wait.
	 */
	public boolean isNavigationHopeless() {
		if (!navigationHopeless && consecutiveStuckChecks >= maxHopelessStuckChecks) {
			navigationHopeless = true;
		}
		return navigationHopeless;
	}

	/**
	 * Resolves the navigation target while pursuing a player. Returns a standable location at the player's XZ so that
	 * Citizens' own A* pathfinder handles all obstacle avoidance and terrain navigation.
	 *
	 * @param player the target player
	 *
	 * @return a standable target near the player, or the player's raw location as a fallback
	 */
	public Location resolvePursuitLocation(Player player) {
		if (!isValid() || player == null) return null;

		LivingEntity entity = getEntity();

		if (entity == null) return null;

		Location copLocation    = entity.getLocation().clone();
		Location playerLocation = player.getLocation().clone();

		if (copLocation.getWorld() == null || !copLocation.getWorld().equals(playerLocation.getWorld())) {
			return playerLocation;
		}

		// Normalize Y so the cop targets solid ground at the player's XZ; Citizens navigates around obstacles itself
		Location safePlayerSpot = normalizeToStandableLocation(playerLocation);
		return safePlayerSpot != null ? safePlayerSpot : playerLocation;
	}

	/**
	 * Resolves the best reachable position when normal pathfinding has been declared hopeless. Attempts three
	 * strategies in order:
	 * <ol>
	 *   <li>Gap-walk - advances from the cop toward the player in 0.5-block steps and returns the last safe position
	 *       before any walkable connection breaks (gap, cliff, void).</li>
	 *   <li>Line approach - walks from the player outward toward the cop, returning the first safe column.</li>
	 *   <li>Ring approach - samples positions on a ring around the player and returns the best-scored safe spot.</li>
	 * </ol>
	 * If the player is within {@value HOPELESS_CLOSE_THRESHOLD} blocks Citizens navigates the remainder directly.
	 *
	 * @param player the target player
	 *
	 * @return the closest safely reachable location toward the player, or {@code null} if resolution fails
	 */
	public Location resolveHopelessFallbackLocation(Player player) {
		if (!isValid() || player == null) return null;

		LivingEntity entity = getEntity();
		if (entity == null) return null;

		Location from = entity.getLocation();
		Location to   = player.getLocation();

		if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return null;

		// Close enough that Citizens can navigate the remaining distance on its own
		if (from.distanceSquared(to) <= hopelessCloseThreshold * hopelessCloseThreshold) {
			Location safe = normalizeToStandableLocation(to);
			return safe != null ? safe : to;
		}

		// 1. Walk from the cop toward the player and stop at the last safe block before any gap
		Location gapWalk = findLastReachableGroundBeforeGap(from, to, 32.0);
		if (gapWalk != null) {
			return gapWalk;
		}

		// 2. Try a safe point on the direct line from the player outward toward the cop
		Location lineApproach = findLineApproachLocation(from, to, 32.0);
		if (lineApproach != null) {
			return lineApproach;
		}

		// 3. Last resort: sample a ring around the player for the best reachable spot
		double distance = from.distance(to);
		return findBestRingApproachLocation(from, to, 1.5, Math.min(distance, 16.0), Math.min(distance * 0.5, 8.0));
	}

	/**
	 * Navigates the NPC to the given location using Citizens pathfinding.
	 *
	 * @param location the target location
	 */
	public void navigateTo(Location location) {
		if (!isValid() || location == null) return;

		if (!shouldRecalculateNavigation(location)) {
			return;
		}

		npc.getNavigator().setTarget(location);
		lastNavigationTarget    = location.clone();
		navigationThrottleTicks = 0;
		stuckSampleTicks        = 0;
		lastProgressLocation    = getEntity() != null ? getEntity().getLocation().clone() : null;
	}

	/**
	 * Stops any current navigation.
	 */
	public void stopNavigation() {
		if (!isValid()) return;
		npc.getNavigator().cancelNavigation();
		resetNavigationTracking();
	}

	/**
	 * Returns the distance between this cop and the given player.
	 *
	 * @param player the target player
	 *
	 * @return the distance in blocks
	 */
	public double distanceTo(Player player) {
		if (!isValid() || player == null) return Double.MAX_VALUE;

		LivingEntity entity = getEntity();

		if (entity == null) return Double.MAX_VALUE;

		return entity.getLocation().distance(player.getLocation());
	}

	/**
	 * Checks if the cop has direct line of sight to the player.
	 *
	 * @param player the target player
	 *
	 * @return true if line of sight exists
	 */
	public boolean hasLineOfSight(Player player) {
		if (!isValid() || player == null) return false;

		LivingEntity entity = getEntity();

		if (entity == null) return false;

		return entity.hasLineOfSight(player);
	}

	/**
	 * Returns whether the attack cooldown has elapsed and the cop is not reloading.
	 *
	 * @return true if the cop can attack
	 */
	public boolean canAttack() {
		return attackCooldown <= 0 && !reloading;
	}

	/**
	 * Attacks the target player using the highest-priority available attack:
	 * <ol>
	 *   <li>Gangland weapon - fires the real projectile through the weapon event pipeline.</li>
	 *   <li>Vanilla ranged weapon (bow / crossbow) - hitscan with particle trail.</li>
	 *   <li>Melee fallback.</li>
	 * </ol>
	 *
	 * @param player the target player
	 */
	public void attack(Player player) {
		if (!isValid() || !canAttack() || player == null) return;

		faceTarget(player);

		if (tierConfig.canUseWeapons() && heldWeapon != null && hasLineOfSight(player)) {
			performGanglandWeaponAttack();
			return;
		}

		if (tierConfig.canUseWeapons() && isHoldingVanillaRangedWeapon() && hasLineOfSight(player)) {
			performVanillaRangedAttack(player);
			return;
		}

		performMeleeAttack(player);
	}

	/**
	 * Attempts to cuff the target player. Returns success based on line of sight and range.
	 *
	 * @param player the target player
	 *
	 * @return true if cuff succeeded
	 */
	public boolean attemptCuff(Player player) {
		if (!isValid() || player == null) return false;
		if (!hasLineOfSight(player)) return false;
		if (distanceTo(player) > tierConfig.cuffRadius()) return false;

		return ThreadLocalRandom.current().nextDouble() < 0.6;
	}

	/**
	 * Equips the cop with its tier's loadout. If a gangland weapon was assigned via {@link #setHeldWeapon}, its built
	 * item is placed in the main hand. Otherwise a random item from the vanilla weapon pool is used.
	 */
	public void equip() {
		if (!isValid()) return;
		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return;

		equipment.setHelmet(tierConfig.helmet());
		equipment.setChestplate(tierConfig.chestplate());
		equipment.setLeggings(tierConfig.leggings());
		equipment.setBoots(tierConfig.boots());

		if (heldWeapon != null) {
			equipment.setItemInMainHand(heldWeapon.buildItem());
		} else if (!tierConfig.weaponPool().isEmpty()) {
			int index = ThreadLocalRandom.current().nextInt(tierConfig.weaponPool().size());
			equipment.setItemInMainHand(tierConfig.weaponPool().get(index));
		}

		// PlayerInventory does not support drop chances
		if (!(livingEntity instanceof Player)) {
			equipment.setHelmetDropChance(0f);
			equipment.setChestplateDropChance(0f);
			equipment.setLeggingsDropChance(0f);
			equipment.setBootsDropChance(0f);
			equipment.setItemInMainHandDropChance(0f);
		}
	}

	/**
	 * Despawns and destroys the NPC, cleaning up entity marks.
	 *
	 * @param entityMarkManager the entity mark manager for cleanup, may be null
	 */
	public void destroy(EntityMarkManager entityMarkManager) {
		if (heldWeapon != null) {
			heldWeapon.stopReloading();
		}
		if (npc.isSpawned()) {
			if (entityMarkManager != null && npc.getEntity() != null) {
				entityMarkManager.removeEntityMark(npc.getEntity());
			}
			npc.despawn();
		}
		npc.destroy();
	}

	/**
	 * Despawns and destroys the NPC without entity mark cleanup.
	 */
	public void destroy() {
		destroy(null);
	}

	/**
	 * Rotates the NPC entity in place to face the target player's eye level. Decouples head rotation from Citizens
	 * navigation so the cop aims at the player even when holding a firing position.
	 */
	private void faceTarget(Player player) {
		Entity entity = npc.getEntity();
		if (entity == null) return;

		Location shooterEye = entity.getLocation().add(0, 1.6, 0);
		Vector   direction  = player.getEyeLocation().toVector().subtract(shooterEye.toVector()).normalize();
		Location rotated    = entity.getLocation().setDirection(direction);
		entity.teleport(rotated);
	}

	/**
	 * Finds the last safe walkable location on the horizontal path from the cop toward the player. If a gap, cliff, or
	 * blocked step is encountered the previous safe location is returned.
	 */
	private Location findLastReachableGroundBeforeGap(Location from, Location to, double maxDistance) {
		if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
			return null;
		}

		Vector horizontal = to.toVector().subtract(from.toVector());
		horizontal.setY(0.0);

		if (horizontal.lengthSquared() <= 0.0001) {
			return normalizeToStandableLocation(from);
		}

		double   totalDistance = Math.min(horizontal.length(), maxDistance);
		Vector   direction     = horizontal.normalize().multiply(0.5);
		Location lastSafe      = normalizeToStandableLocation(from);
		Location cursor        = from.clone();

		for (double travelled = 0.5; travelled <= totalDistance; travelled += 0.5) {
			cursor = cursor.clone().add(direction);

			Location standable = normalizeToStandableLocation(cursor);

			if (standable == null) {
				return lastSafe;
			}

			if (!isWalkableStep(lastSafe, standable)) {
				return lastSafe;
			}

			lastSafe = standable;
		}

		return lastSafe;
	}

	/**
	 * Finds a safe point on the line between the player and the cop by walking from the player outward toward the cop.
	 */
	private Location findLineApproachLocation(Location copLocation, Location playerLocation, double maxDistance) {
		Vector direction = copLocation.toVector().subtract(playerLocation.toVector());

		if (direction.lengthSquared() <= 0.0001) {
			return normalizeToStandableLocation(playerLocation);
		}

		direction.normalize();

		double searchDistance = Math.min(copLocation.distance(playerLocation), maxDistance);

		for (double offset = 0.0; offset <= searchDistance; offset += 1.0) {
			Location candidate     = playerLocation.clone().add(direction.clone().multiply(offset));
			Location safeCandidate = normalizeToStandableLocation(candidate);

			if (safeCandidate != null) {
				return safeCandidate;
			}
		}

		return null;
	}

	/**
	 * Finds the best safe point on a ring around the player. Positions are scored by distance to the cop, deviation
	 * from the ideal radius, and - for ranged cops - line-of-sight quality.
	 */
	private Location findBestRingApproachLocation(Location copLocation, Location playerLocation, double minRadius,
												  double maxRadius, double idealRadius) {
		Location bestLocation = null;
		double   bestScore    = Double.MAX_VALUE;

		for (double radius = minRadius; radius <= maxRadius; radius += 1.5) {
			for (int angle = 0; angle < 360; angle += 22) {
				double radians = Math.toRadians(angle);

				Location candidate = playerLocation.clone()
												   .add(Math.cos(radians) * radius, 0.0, Math.sin(radians) * radius);

				Location safeCandidate = normalizeToStandableLocation(candidate);

				if (safeCandidate == null || !isSafeStandLocation(safeCandidate)) {
					continue;
				}

				double score = safeCandidate.distanceSquared(copLocation);
				score += Math.abs(radius - idealRadius) * 3.0;

				if (isUsingRangedWeapon()) {
					if (!hasClearShot(safeCandidate, playerLocation)) {
						score += 20.0;
					}
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

	/**
	 * Returns whether there is a clear block line from {@code from} to {@code to} (eye-level ray, ignoring fluids).
	 */
	private boolean hasClearShot(Location from, Location to) {
		if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
			return false;
		}

		Location start = from.clone().add(0.0, 1.2, 0.0);
		Location end   = to.clone().add(0.0, 1.0, 0.0);
		Vector   delta = end.toVector().subtract(start.toVector());

		double length = delta.length();
		if (length <= 0.0001) {
			return true;
		}

		var hit = from.getWorld().rayTraceBlocks(start, delta.normalize(), length, FluidCollisionMode.NEVER, true);
		return hit == null;
	}

	/**
	 * Returns whether the location has immediate walkable ground and is not sitting on the edge of a gap that would
	 * force the next movement step into open air.
	 */
	private boolean isSafeStandLocation(Location location) {
		return isBasicSafeStandLocation(location) && !isFrontedByImmediateGap(location);
	}

	/**
	 * Returns whether there is an immediate horizontal gap directly ahead of the candidate location. Checks a 1-block
	 * step in the direction from the location toward the entity over a ±4 block vertical scan.
	 */
	private boolean isFrontedByImmediateGap(Location location) {
		LivingEntity entity = getEntity();

		if (entity == null) {
			return false;
		}

		Vector facing = entity.getLocation().toVector().subtract(location.toVector());
		facing.setY(0.0);

		if (facing.lengthSquared() <= 0.0001) {
			return false;
		}

		Vector   step  = facing.normalize();
		Location front = location.clone().add(step.getX(), 0.0, step.getZ());

		World world = front.getWorld();
		int   baseX = front.getBlockX();
		int   baseZ = front.getBlockZ();

		for (int yOffset = 2; yOffset >= -4; yOffset--) {
			Location candidate = new Location(world, baseX + 0.5, front.getY() + yOffset, baseZ + 0.5);
			if (isBasicSafeStandLocation(candidate)) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Returns whether two normalized standable locations are connected by a single walkable step: at most one block of
	 * Y change in either direction, within horizontal reach, and the destination passes basic stand checks.
	 */
	private boolean isWalkableStep(Location from, Location to) {
		if (from == null || to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
			return false;
		}

		int deltaY = to.getBlockY() - from.getBlockY();

		if (deltaY > 1 || deltaY < -1) {
			return false;
		}

		double dx = from.getX() - to.getX();
		double dz = from.getZ() - to.getZ();

		if (dx * dx + dz * dz > 1.25 * 1.25) {
			return false;
		}

		return isBasicSafeStandLocation(to);
	}

	/**
	 * Attempts to convert a raw location into a safe standable destination for an NPC.
	 */
	private Location normalizeToStandableLocation(Location location) {
		if (location == null || location.getWorld() == null) return null;

		World world = location.getWorld();
		int   baseX = location.getBlockX();
		int   baseZ = location.getBlockZ();

		for (int yOffset = 2; yOffset >= -4; yOffset--) {
			Location candidate = new Location(world, baseX + 0.5, location.getY() + yOffset, baseZ + 0.5,
											  location.getYaw(), location.getPitch());

			if (isBasicSafeStandLocation(candidate)) {
				return candidate;
			}
		}

		return null;
	}

	/**
	 * Returns whether the location is safe for the NPC to stand at: feet and head blocks passable, block below solid
	 * and not a hazard.
	 */
	private boolean isBasicSafeStandLocation(Location location) {
		if (location == null || location.getWorld() == null) return false;

		Block feet  = location.getBlock();
		Block head  = feet.getRelative(0, 1, 0);
		Block below = feet.getRelative(0, -1, 0);

		if (!feet.isPassable() || !head.isPassable()) {
			return false;
		}

		if (below.isPassable() || !below.getType().isSolid()) {
			return false;
		}

		Material supportType = below.getType();

		return supportType != Material.LAVA && supportType != Material.WATER && supportType != Material.CACTUS &&
			   supportType != Material.MAGMA_BLOCK;
	}

	/**
	 * Updates navigation progress tracking for throttling and stuck detection.
	 * <p>
	 * Progress is tracked whenever a navigation target is active, regardless of whether Citizens reports
	 * {@code isNavigating()} at this instant. Citizens can return {@code false} briefly between path requests (on
	 * completion or silent failure), which would otherwise reset the stuck counter every tick.
	 * {@code consecutiveStuckChecks} is only cleared on actual movement progress or an explicit
	 * {@link #stopNavigation()} call.
	 */
	private void updateNavigationProgress() {
		navigationThrottleTicks++;

		LivingEntity entity = getEntity();

		if (entity == null) {
			resetNavigationTracking();
			return;
		}

		// No target active yet - nothing to track; preserve the stuck counter
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

		if (stuckSampleTicks < stuckCheckIntervalTicks) {
			return;
		}

		Location currentLocation = entity.getLocation();

		if (!Objects.equals(currentLocation.getWorld(), lastProgressLocation.getWorld())) {
			lastProgressLocation   = currentLocation.clone();
			stuckSampleTicks       = 0;
			consecutiveStuckChecks = 0;
			return;
		}

		double progress = currentLocation.distanceSquared(lastProgressLocation);

		if (progress < minProgressDistanceSquared) {
			consecutiveStuckChecks++;
		} else {
			// Real movement detected - the obstacle is gone, so clear the hopeless latch too
			consecutiveStuckChecks = 0;
			navigationHopeless     = false;
			lastProgressLocation   = currentLocation.clone();
		}

		stuckSampleTicks = 0;
	}

	/**
	 * Returns whether navigation should be recalculated right now.
	 */
	private boolean shouldRecalculateNavigation(Location target) {
		if (target == null) {
			return false;
		}

		// Always respect the throttle - even when stuck - to avoid hammering Citizens with path requests every tick
		if (navigationThrottleTicks < navigationRecalculationTicks) {
			return false;
		}

		if (lastNavigationTarget == null || lastNavigationTarget.getWorld() == null || target.getWorld() == null) {
			return true;
		}

		if (!lastNavigationTarget.getWorld().equals(target.getWorld())) {
			return true;
		}

		// Force a fresh path request when stuck (but still throttled above), or when the target moved enough
		return isNavigationStuck() || lastNavigationTarget.distanceSquared(target) >= 2.25;
	}

	/**
	 * Clears transient navigation tracking state. The {@code navigationHopeless} latch is intentionally preserved so
	 * that state transitions (e.g. pursuing → combat) do not restart the stuck-detection wait when the underlying
	 * terrain obstacle still exists. The latch is cleared only when real movement progress is detected.
	 */
	private void resetNavigationTracking() {
		lastNavigationTarget    = null;
		lastProgressLocation    = null;
		navigationThrottleTicks = navigationRecalculationTicks;
		stuckSampleTicks        = 0;
		consecutiveStuckChecks  = 0;
	}

	/**
	 * Fires the gangland weapon at the player. Consumes ammo, launches the real projectile through the
	 * {@link WeaponShootEvent} pipeline, and triggers a reload when the magazine empties.
	 */
	private void performGanglandWeaponAttack() {
		if (heldWeapon.isBroken() || heldWeapon.isMagazineEmpty()) {
			triggerReload();
			return;
		}

		boolean consumed = heldWeapon.consumeShot();
		if (!consumed) {
			triggerReload();
			return;
		}

		LivingEntity shooter = getEntity();

		if (shooter == null) return;

		WeaponProjectile<?> projectile = heldWeapon.getProjectileData()
												   .getType()
												   .createInstance(plugin, shooter, heldWeapon);

		WeaponShootEvent event = new WeaponShootEvent(heldWeapon, projectile);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			// Refund the consumed shot
			heldWeapon.addAmmunition(1);
		} else {
			projectile.launchProjectile();
			SoundConfiguration.playSoundsAtLocation(shooter.getEyeLocation(), heldWeapon.getSoundData().getShotCustom(),
													heldWeapon.getSoundData().getShotDefault());
			refreshHeldItem();
		}

		// Use the weapon's own cooldown; fall back to a sensible minimum
		int cooldown = heldWeapon.getProjectileData().getCooldown();
		attackCooldown = Math.max(cooldown, 5);

		if (heldWeapon.isMagazineEmpty()) {
			triggerReload();
		}
	}

	/**
	 * Schedules a reload: the cop is blocked from attacking until the weapon's reload cooldown expires, after which the
	 * magazine is topped up. No physical ammo items are consumed because the cop's ammo is managed internally.
	 */
	private void triggerReload() {
		if (reloading || plugin == null) return;

		reloading = true;

		int reloadTicks = heldWeapon.getReloadData().getCooldown();

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (!isValid()) {
				reloading = false;
				return;
			}
			heldWeapon.addAmmunition(heldWeapon.getReloadData().getMaxMagCapacity());
			refreshHeldItem();
			reloading = false;
		}, reloadTicks);
	}

	/**
	 * Updates the NPC's main-hand item so the ammo count display stays current.
	 */
	private void refreshHeldItem() {
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

	private void performMeleeAttack(Player player) {
		if (!isValid() || player == null) return;

		LivingEntity entity = getEntity();

		if (entity == null) return;

		player.damage(tierConfig.damage(), entity);
		attackCooldown = 5;

		Vector knockback = player.getLocation()
								 .toVector()
								 .subtract(entity.getLocation().toVector())
								 .normalize()
								 .multiply(0.3)
								 .setY(0.1);
		player.setVelocity(player.getVelocity().add(knockback));
	}

	/**
	 * Hitscan attack for vanilla ranged weapons (bow / crossbow). Does not consume arrows.
	 */
	private void performVanillaRangedAttack(Player player) {
		LivingEntity shooter = getEntity();

		if (shooter == null) return;

		World    world     = shooter.getWorld();
		Location eye       = shooter.getEyeLocation();
		Vector   direction = eye.getDirection().normalize();

		var result = world.rayTrace(eye, direction, 35.0, FluidCollisionMode.NEVER, true, 0.25, entity -> {
			return entity instanceof Player p && p.getUniqueId().equals(player.getUniqueId());
		});

		world.spawnParticle(Particle.CRIT, eye.clone().add(direction.clone().multiply(0.5)), 6, 0.05, 0.05, 0.05, 0.0);
		world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.6f);

		if (result != null && result.getHitEntity() != null) {
			player.damage(tierConfig.damage(), shooter);
		}

		attackCooldown = 15;
	}

	private boolean isHoldingVanillaRangedWeapon() {
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
	 * Decrements the attack cooldown by the AI tick rate so that weapon cooldowns expressed in server ticks are
	 * consumed correctly regardless of how often the AI task runs.
	 */
	private void decrementAttackCooldown() {
		if (attackCooldown > 0) attackCooldown = Math.max(0, attackCooldown - aiTickRate);
	}
}