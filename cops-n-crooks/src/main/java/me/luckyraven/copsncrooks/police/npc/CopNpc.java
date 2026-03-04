package me.luckyraven.copsncrooks.police.npc;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.police.config.CopTierConfig;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopState;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a single cop NPC backed by Citizens. Manages state transitions and delegates behavior to CopBehavior
 * instances.
 */
public class CopNpc {

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

	public CopNpc(NPC npc, CopTierConfig tierConfig, Map<CopState, CopBehavior> behaviors, Location spawnLocation) {
		this.npc              = npc;
		this.tierConfig       = tierConfig;
		this.behaviors        = behaviors;
		this.spawnLocation    = spawnLocation;
		this.currentState     = CopState.IDLE;
		this.attackCooldown   = 0;
		this.markedForRemoval = false;
		this.despawnTicks     = 0;
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
		if (currentState == newState) return;

		CopBehavior oldBehavior = behaviors.get(currentState);
		if (oldBehavior != null) {
			oldBehavior.onExit(this);
		}

		currentState = newState;

		CopBehavior newBehavior = behaviors.get(currentState);
		if (newBehavior != null) {
			newBehavior.onEnter(this);
		}
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
		if (behavior != null) {
			behavior.tick(this, target);
		}
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
	 * Returns whether the attack cooldown has elapsed.
	 *
	 * @return true if the cop can attack
	 */
	public boolean canAttack() {
		return attackCooldown <= 0;
	}

	/**
	 * Attacks the target player with the cop's configured damage.
	 *
	 * @param player the target player
	 */
	public void attack(Player player) {
		if (!isValid() || !canAttack() || player == null) return;

		player.damage(tierConfig.damage(), getEntity());
		attackCooldown = 20;

		Vector knockback = player.getLocation()
								 .toVector()
								 .subtract(getEntity().getLocation().toVector())
								 .normalize()
								 .multiply(0.3)
								 .setY(0.1);
		player.setVelocity(player.getVelocity().add(knockback));
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
	 * Equips the cop with its tier's loadout including a random weapon from the pool.
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

		if (!tierConfig.weaponPool().isEmpty()) {
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
	 * Decrements the attack cooldown by one tick.
	 */
	private void decrementAttackCooldown() {
		if (attackCooldown > 0) attackCooldown--;
	}
}