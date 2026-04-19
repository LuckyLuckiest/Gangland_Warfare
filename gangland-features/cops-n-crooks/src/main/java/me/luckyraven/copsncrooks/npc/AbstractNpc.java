package me.luckyraven.copsncrooks.npc;

import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.npc.entity.EntityMarkManager;
import me.luckyraven.weapon.Weapon;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Base class for all gangland Citizens NPC types (cops, civilians, etc.).
 * <p>
 * Provides the public API for combat, navigation, and lifecycle management. Navigation logic is delegated to
 * {@link NpcNavigationDelegate}; combat logic is delegated to {@link NpcCombatDelegate}.
 * <p>
 * Subclasses provide type-specific configuration by implementing the abstract methods.
 */
@CustomLog
public abstract class AbstractNpc {

	// ── Core ─────────────────────────────────────────────────────────────────
	@Getter
	protected final NPC                   npc;
	@Getter
	protected final Location              spawnLocation;
	protected final int                   aiTickRate;
	// ── Delegates ────────────────────────────────────────────────────────────
	private final   NpcNavigationDelegate navigation;
	private final   NpcCombatDelegate     combat;
	// ── Weapon ───────────────────────────────────────────────────────────────
	@Getter
	protected       Weapon                heldWeapon;
	protected       boolean               reloading;
	protected       JavaPlugin            plugin;
	// ── Difficulty ───────────────────────────────────────────────────────────
	@Getter
	protected       NpcDifficulty         difficulty;
	// ── State ────────────────────────────────────────────────────────────────
	@Getter
	protected       boolean               markedForRemoval;
	@Getter
	@Setter
	protected       int                   despawnTicks;
	@Getter
	@Setter
	protected       int                   pursuitTicks;
	protected       int                   attackCooldown;

	protected AbstractNpc(NPC npc, Location spawnLocation, NpcNavigationConfig navConfig, NpcDifficulty difficulty) {
		this.npc           = npc;
		this.spawnLocation = spawnLocation;
		this.aiTickRate    = navConfig.getAiTickRate();
		this.difficulty    = difficulty != null ? difficulty : NpcDifficulty.NORMAL;

		this.navigation = new NpcNavigationDelegate(this, navConfig);
		this.combat     = new NpcCombatDelegate(this);

		this.attackCooldown   = 0;
		this.markedForRemoval = false;
		this.despawnTicks     = 0;
		this.pursuitTicks     = 0;
		this.reloading        = false;
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

	// ── Combat (delegated to NpcCombatDelegate) ──────────────────────────────

	/**
	 * Returns whether the NPC should hold position instead of closing distance (ranged hold check).
	 */
	public boolean shouldHoldPursuitPosition(Player player) {
		if (!isUsingRangedWeapon() || !hasLineOfSight(player)) return false;
		return navigation.isInRangedHoldRange(distanceTo(player));
	}

	/**
	 * Returns whether the attack cooldown has elapsed, the NPC is not reloading, and the held weapon (if any) is not
	 * mid-reload.
	 */
	public boolean canAttack() {
		return combat.canAttack();
	}

	/**
	 * Attacks the target player using the highest-priority available attack: gangland weapon → vanilla ranged → melee
	 * fallback. Does nothing if the target is dead or in a downed state.
	 */
	public void attack(Player player) {
		if (!isValid() || player == null) return;
		combat.attack(player, canUseWeapons(), getAttackDamage());
	}

	/**
	 * Attacks the target entity using the gangland weapon (if available and line of sight) or melee fallback. Used for
	 * NPC-to-NPC combat where the target is not a player.
	 */
	public void attackEntity(LivingEntity target) {
		if (!isValid() || target == null) return;
		combat.attackEntity(target, canUseWeapons(), getAttackDamage());
	}

	// ── Perception ───────────────────────────────────────────────────────────

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
		return navigation.isInRangedHoldRange(distanceTo(target));
	}

	// ── Navigation (delegated to NpcNavigationDelegate) ──────────────────────

	/**
	 * Navigates the NPC toward the given destination using a proactive {@link NavStep} plan.
	 */
	public void navigateTo(Location destination) {
		if (!isValid() || destination == null) return;
		navigation.navigateTo(destination);
	}

	/**
	 * Stops any current navigation and clears tracking. Use when transitioning out of a behavior state.
	 */
	public void stopNavigation() {
		if (!isValid()) return;
		navigation.stopNavigation();
	}

	/**
	 * Cancels the Citizens navigator's current path without resetting tracking state.
	 */
	public void pauseNavigation() {
		if (!isValid()) return;
		navigation.pauseNavigation();
	}

	/**
	 * Returns whether the current navigation appears stuck.
	 */
	public boolean isNavigationStuck() {
		return navigation.isNavigationStuck();
	}

	/**
	 * Returns whether the navigation target appears permanently unreachable.
	 */
	public boolean isNavigationHopeless() {
		return navigation.isNavigationHopeless();
	}

	/**
	 * Resolves the navigation target while pursuing a player.
	 */
	public Location resolvePursuitLocation(Player player) {
		if (!isValid() || player == null) return null;
		return navigation.resolvePursuitLocation(player);
	}

	/**
	 * Resolves the best reachable position when normal pathfinding has been declared hopeless.
	 */
	public Location resolveHopelessFallbackLocation(Player player) {
		if (!isValid() || player == null) return null;
		return navigation.resolveHopelessFallbackLocation(player);
	}

	/**
	 * Resolves the navigation target while pursuing a non-player entity.
	 */
	public Location resolvePursuitLocation(LivingEntity target) {
		if (!isValid() || target == null) return null;
		return navigation.resolvePursuitLocation(target);
	}

	/**
	 * Resolves the best reachable position when pathfinding has been declared hopeless for a non-player entity target.
	 */
	public Location resolveHopelessFallbackLocation(LivingEntity target) {
		if (!isValid() || target == null) return null;
		return navigation.resolveHopelessFallbackLocation(target);
	}

	/**
	 * Scans a forward-biased cone of positions and returns the best walkable destination for wandering.
	 *
	 * @param minDist minimum probe distance in blocks
	 * @param maxDist maximum probe distance in blocks
	 *
	 * @return a validated standable location, or {@code null} if none found
	 */
	public Location findForwardWanderDestination(int minDist, int maxDist) {
		return navigation.findForwardWanderDestination(minDist, maxDist);
	}

	// ── Tick helpers (called by subclass tick methods) ───────────────────────

	/**
	 * Decrements the attack cooldown by one server tick.
	 */
	protected void decrementAttackCooldown() {
		combat.decrementAttackCooldown();
	}

	/**
	 * Updates navigation progress tracking for throttling and stuck detection.
	 */
	protected void updateNavigationProgress() {
		navigation.updateNavigationProgress();
	}

	/**
	 * Rotates the NPC to face the given player, applying aim error.
	 */
	protected void faceTarget(Player player) {
		combat.faceTarget(player);
	}

	/**
	 * Rotates the NPC to face the given entity, applying aim error.
	 */
	protected void faceTargetEntity(LivingEntity target) {
		combat.faceTargetEntity(target);
	}

	/**
	 * Fires the equipped gangland weapon using the current selective fire mode.
	 */
	protected void performGanglandWeaponAttack() {
		combat.performGanglandWeaponAttack();
	}

	/**
	 * Triggers a weapon reload cycle.
	 */
	protected void triggerReload() {
		combat.triggerReload();
	}

	/**
	 * Fires a vanilla ranged attack (bow/crossbow ray trace) against the target player.
	 */
	protected void performVanillaRangedAttack(Player player) {
		combat.performVanillaRangedAttack(player, getAttackDamage());
	}

	/**
	 * Performs a melee attack against the target player with knockback.
	 */
	protected void performMeleeAttack(Player player) {
		if (!isValid() || player == null) return;
		combat.performMeleeAttack(player, getAttackDamage());
	}

	/**
	 * Performs a melee attack against the target entity with knockback.
	 */
	protected void performMeleeAttackOnEntity(LivingEntity target) {
		if (!isValid() || target == null) return;
		combat.performMeleeAttackOnEntity(target, getAttackDamage());
	}

	/**
	 * Updates the held weapon's visual data on the NPC's equipment.
	 */
	protected void refreshHeldItem() {
		if (!isValid()) return;
		combat.refreshHeldItem();
	}

	/**
	 * Returns whether the NPC is holding a vanilla ranged weapon (bow or crossbow).
	 */
	protected boolean isHoldingVanillaRangedWeapon() {
		if (!isValid()) return false;
		return combat.isHoldingVanillaRangedWeapon();
	}

	// ── Private helpers ──────────────────────────────────────────────────────

	/**
	 * Strips any protection that Citizens or Minecraft may have (re-)applied to this NPC's entity.
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
}
