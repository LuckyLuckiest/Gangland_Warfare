package me.luckyraven.copsncrooks.police.npc;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.police.config.CopTierConfig;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopState;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.events.WeaponShootEvent;
import me.luckyraven.weapon.projectile.WeaponProjectile;
import net.citizensnpcs.api.npc.NPC;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a single cop NPC backed by Citizens. Manages state transitions and delegates behavior to CopBehavior
 * instances.
 */
public class CopNpc {

	private static final Logger logger = LogManager.getLogger(CopNpc.class.getSimpleName());

	@Getter
	private final NPC                        npc;
	@Getter
	private final CopTierConfig              tierConfig;
	private final Map<CopState, CopBehavior> behaviors;
	@Getter
	private final Location                   spawnLocation;

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
	private int      despawnTicks;

	@Getter
	private Weapon     heldWeapon;
	private boolean    reloading;
	private JavaPlugin plugin;

	public CopNpc(NPC npc, CopTierConfig tierConfig, Map<CopState, CopBehavior> behaviors, Location spawnLocation) {
		this.npc              = npc;
		this.tierConfig       = tierConfig;
		this.behaviors        = behaviors;
		this.spawnLocation    = spawnLocation;
		this.currentState     = CopState.IDLE;
		this.attackCooldown   = 0;
		this.markedForRemoval = false;
		this.despawnTicks     = 0;
		this.reloading        = false;
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
		logger.info("Transitioning cop {} to state: {} from: {}", npc.getName(), newState, currentState);
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

		CopBehavior behavior = behaviors.get(currentState);

		if (behavior == null) return;

		behavior.tick(this, target);
	}

	/**
	 * Navigates the NPC to the given location using Citizens pathfinding.
	 *
	 * @param location the target location
	 */
	public void navigateTo(Location location) {
		if (!isValid() || location == null) return;
		npc.getNavigator().setTarget(location);
	}

	/**
	 * Stops any current navigation.
	 */
	public void stopNavigation() {
		if (!isValid()) return;
		npc.getNavigator().cancelNavigation();
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
		return getEntity().getLocation().distance(player.getLocation());
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
		return getEntity().hasLineOfSight(player);
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
	 *   <li>Gangland weapon — fires the real projectile through the weapon event pipeline.</li>
	 *   <li>Vanilla ranged weapon (bow / crossbow) — hitscan with particle trail.</li>
	 *   <li>Melee fallback.</li>
	 * </ol>
	 *
	 * @param player the target player
	 */
	public void attack(Player player) {
		if (!isValid() || !canAttack() || player == null) return;

		if (tierConfig.canUseWeapons() && heldWeapon != null && hasLineOfSight(player)) {
			performGanglandWeaponAttack(player);
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
	 * Fires the gangland weapon at the player. Consumes ammo, launches the real projectile through the
	 * {@link WeaponShootEvent} pipeline, and triggers a reload when the magazine empties.
	 */
	private void performGanglandWeaponAttack(Player player) {
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

		player.damage(tierConfig.damage(), getEntity());
		attackCooldown = 5;

		Vector knockback = player.getLocation()
								 .toVector()
								 .subtract(getEntity().getLocation().toVector())
								 .normalize()
								 .multiply(0.3)
								 .setY(0.1);
		player.setVelocity(player.getVelocity().add(knockback));
	}

	/**
	 * Hitscan attack for vanilla ranged weapons (bow / crossbow). Does not consume arrows.
	 */
	private void performVanillaRangedAttack(Player player) {
		LivingEntity shooter   = getEntity();
		World        world     = shooter.getWorld();
		Location     eye       = shooter.getEyeLocation();
		Vector       direction = eye.getDirection().normalize();

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
	 * Decrements the attack cooldown by one tick.
	 */
	private void decrementAttackCooldown() {
		if (attackCooldown > 0) attackCooldown--;
	}
}