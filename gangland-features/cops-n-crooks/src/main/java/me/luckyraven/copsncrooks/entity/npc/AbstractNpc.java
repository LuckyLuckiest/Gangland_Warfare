package me.luckyraven.copsncrooks.entity.npc;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.dto.ReloadData;
import me.luckyraven.weapon.events.projectile.WeaponShootEvent;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import me.luckyraven.weapon.types.gun.GunWeapon;
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

import java.util.Objects;

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

	// ── Core ─────────────────────────────────────────────────────────────────
	@Getter
	protected final NPC        npc;
	@Getter
	protected final Location   spawnLocation;
	protected final int        aiTickRate;
	// ── Navigation config (read once from NpcNavigationConfig) ──────────────
	private final   int        navigationRecalculationTicks;
	private final   int        stuckCheckIntervalTicks;
	private final   int        maxStuckChecks;
	private final   int        maxHopelessStuckChecks;
	private final   double     hopelessCloseThreshold;
	private final   double     minProgressDistanceSquared;
	private final   double     rangedMinDistance;
	private final   double     rangedMaxDistance;
	private final   double     minRepathAfterLossTicks;
	// ── Weapon ───────────────────────────────────────────────────────────────
	@Getter
	protected       Weapon     heldWeapon;
	protected       boolean    reloading;
	protected       JavaPlugin plugin;

	// ── State ────────────────────────────────────────────────────────────────
	@Getter
	protected boolean markedForRemoval;
	@Getter
	@Setter
	protected int     despawnTicks;
	protected int     attackCooldown;

	// ── Navigation state ─────────────────────────────────────────────────────
	private Location lastNavigationTarget;
	private Location lastProgressLocation;
	private int      navigationThrottleTicks;
	private int      stuckSampleTicks;
	private int      consecutiveStuckChecks;
	private boolean  navigationHopeless;

	protected AbstractNpc(NPC npc, Location spawnLocation, NpcNavigationConfig navConfig) {
		this.npc           = npc;
		this.spawnLocation = spawnLocation;
		this.aiTickRate    = navConfig.getAiTickRate();

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
		this.minRepathAfterLossTicks = navConfig.getMinRepathAfterLossTicks();

		this.attackCooldown         = 0;
		this.markedForRemoval       = false;
		this.despawnTicks           = 0;
		this.reloading              = false;
		this.stuckSampleTicks       = 0;
		this.consecutiveStuckChecks = 0;
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
		return npc.isSpawned() && npc.getEntity() != null && !npc.getEntity().isDead();
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

	// ── Combat ───────────────────────────────────────────────────────────────

	/**
	 * Returns whether this NPC is currently using a ranged weapon.
	 */
	public boolean isUsingRangedWeapon() {
		if (!canUseWeapons()) return false;
		return heldWeapon != null || isHoldingVanillaRangedWeapon();
	}

	/**
	 * Returns whether the NPC should hold position instead of closing distance (ranged hold check).
	 */
	public boolean shouldHoldPursuitPosition(Player player) {
		if (!isUsingRangedWeapon() || !hasLineOfSight(player)) return false;
		double distance = distanceTo(player);
		return distance >= rangedMinDistance && distance <= rangedMaxDistance;
	}

	/**
	 * Returns whether the attack cooldown has elapsed and the NPC is not reloading.
	 */
	public boolean canAttack() {
		return attackCooldown <= 0 && !reloading;
	}

	/**
	 * Attacks the target player using the highest-priority available attack: gangland weapon → vanilla ranged → melee
	 * fallback.
	 */
	public void attack(Player player) {
		if (!isValid() || !canAttack() || player == null) return;

		faceTarget(player);

		if (canUseWeapons() && heldWeapon != null && hasLineOfSight(player)) {
			performGanglandWeaponAttack();
			return;
		}

		if (canUseWeapons() && isHoldingVanillaRangedWeapon() && hasLineOfSight(player)) {
			performVanillaRangedAttack(player);
			return;
		}

		performMeleeAttack(player);
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

	// ── Navigation ───────────────────────────────────────────────────────────

	/**
	 * Navigates the NPC to the given location using Citizens pathfinding.
	 */
	public void navigateTo(Location location) {
		if (!isValid() || location == null) return;
		if (!shouldRecalculateNavigation(location)) return;

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
	 * Returns whether the current navigation appears stuck.
	 */
	public boolean isNavigationStuck() {
		return consecutiveStuckChecks >= maxStuckChecks;
	}

	/**
	 * Returns whether the navigation target appears permanently unreachable.
	 */
	public boolean isNavigationHopeless() {
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

		if (from.distanceSquared(to) <= hopelessCloseThreshold * hopelessCloseThreshold) {
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

	// ── Tick helpers (called by subclass tick methods) ───────────────────────

	/**
	 * Decrements the attack cooldown by the AI tick rate.
	 */
	protected void decrementAttackCooldown() {
		if (attackCooldown > 0) attackCooldown = Math.max(0, attackCooldown - aiTickRate);
	}

	/**
	 * Updates navigation progress tracking for throttling and stuck detection.
	 */
	protected void updateNavigationProgress() {
		navigationThrottleTicks++;

		LivingEntity entity = getEntity();

		if (entity == null) {
			resetNavigationTracking();
			return;
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
			lastProgressLocation   = currentLocation.clone();
		}

		stuckSampleTicks = 0;
	}

	// ── Private attack helpers ────────────────────────────────────────────────

	protected void faceTarget(Player player) {
		Entity entity = npc.getEntity();
		if (entity == null) return;

		Location shooterEye = entity.getLocation().add(0, 1.6, 0);
		Vector   direction  = player.getEyeLocation().toVector().subtract(shooterEye.toVector()).normalize();
		Location rotated    = entity.getLocation().setDirection(direction);
		entity.teleport(rotated);
	}

	protected void performGanglandWeaponAttack() {
		if (!(heldWeapon instanceof GunWeapon gun)) return;

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

		WeaponProjectile<?> projectile = gun.getProjectileData().getType().createInstance(plugin, shooter, gun);

		WeaponShootEvent event = new WeaponShootEvent(heldWeapon, projectile);
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			heldWeapon.addAmmunition(1);
		} else {
			projectile.launchProjectile();
			SoundConfiguration.playSoundsAtLocation(shooter.getEyeLocation(),
			                                        heldWeapon.getSoundData().getShotCustom(),
			                                        heldWeapon.getSoundData().getShotDefault());
			refreshHeldItem();
		}

		int cooldown = gun.getProjectileData().getCooldown();
		attackCooldown = Math.max(cooldown, 5);

		if (heldWeapon.isMagazineEmpty()) {
			triggerReload();
		}
	}

	protected void triggerReload() {
		if (reloading || plugin == null) return;

		reloading = true;

		ReloadData reloadData = heldWeapon.getReloadData();
		if (reloadData == null) return;

		int reloadTicks = reloadData.getCooldown();

		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			AmmunitionData ammunitionData = heldWeapon.getAmmunitionData();
			if (ammunitionData == null) return;

			if (!isValid()) {
				reloading = false;
				return;
			}

			heldWeapon.addAmmunition(ammunitionData.getMaxMagCapacity());
			refreshHeldItem();
			reloading = false;
		}, reloadTicks);
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

		attackCooldown = 15;
	}

	protected void performMeleeAttack(Player player) {
		if (!isValid() || player == null) return;

		LivingEntity entity = getEntity();
		if (entity == null) return;

		player.damage(getAttackDamage(), entity);
		attackCooldown = 5;

		Vector knockback = player.getLocation()
		                         .toVector()
		                         .subtract(entity.getLocation().toVector())
		                         .normalize()
		                         .multiply(0.3)
		                         .setY(0.1);
		player.setVelocity(player.getVelocity().add(knockback));
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

	// ── Navigation helpers ────────────────────────────────────────────────────

	private boolean shouldRecalculateNavigation(Location target) {
		if (target == null) return false;

		if (navigationThrottleTicks < navigationRecalculationTicks) {
			return lastNavigationTarget != null && !npc.getNavigator().isNavigating() &&
			       navigationThrottleTicks >= minRepathAfterLossTicks;
		}

		if (lastNavigationTarget == null || lastNavigationTarget.getWorld() == null ||
		    target.getWorld() == null) {
			return true;
		}

		if (!lastNavigationTarget.getWorld().equals(target.getWorld())) return true;

		return isNavigationStuck() || lastNavigationTarget.distanceSquared(target) >= 2.25;
	}

	private void resetNavigationTracking() {
		lastNavigationTarget    = null;
		lastProgressLocation    = null;
		navigationThrottleTicks = navigationRecalculationTicks;
		stuckSampleTicks        = 0;
		consecutiveStuckChecks  = 0;
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

	private Location findBestRingApproachLocation(Location npcLocation, Location playerLocation,
	                                              double minRadius, double maxRadius, double idealRadius) {
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
		if (below.isPassable() || !below.getType().isSolid()) return false;

		Material supportType = below.getType();
		return supportType != Material.LAVA && supportType != Material.WATER &&
		       supportType != Material.CACTUS && supportType != Material.MAGMA_BLOCK;
	}
}
