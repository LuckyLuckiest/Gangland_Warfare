package me.luckyraven.copsncrooks.police;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.copsncrooks.entity.EntityMark;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;

public class PoliceUnit {

	@Getter
	private final UUID               targetPlayerId;
	@Getter
	private final Mob                entity;
	private final NamespacedKey      policeKey;
	private final NamespacedKey      targetKey;
	private final PathfindingHandler pathfindingHandler;

	@Getter
	@Setter
	private PoliceAIState state;
	@Getter
	private Location      lastKnownPlayerLocation;
	@Getter
	private int           losLostTicks;
	@Getter
	private int           searchTicks;
	private int           attackCooldown;
	@Getter
	private int           wantedLevelOnSpawn;

	public PoliceUnit(JavaPlugin plugin, Mob entity, Player target, int wantedLevel,
					  EntityMarkManager entityMarkManager, PathfindingHandler pathfindingHandler) {
		this.entity                  = entity;
		this.targetPlayerId          = target.getUniqueId();
		this.policeKey               = new NamespacedKey(plugin, "police_unit");
		this.targetKey               = new NamespacedKey(plugin, "police_target");
		this.pathfindingHandler      = pathfindingHandler;
		this.state                   = PoliceAIState.IDLE;
		this.lastKnownPlayerLocation = target.getLocation().clone();
		this.losLostTicks            = 0;
		this.searchTicks             = 0;
		this.attackCooldown          = 0;
		this.wantedLevelOnSpawn      = wantedLevel;

		// Mark as police entity
		entityMarkManager.setEntityMark(entity, EntityMark.POLICE);

		// Store metadata
		PersistentDataContainer pdc = entity.getPersistentDataContainer();
		pdc.set(policeKey, PersistentDataType.BYTE, (byte) 1);
		pdc.set(targetKey, PersistentDataType.STRING, target.getUniqueId().toString());

		// Configure entity
		configureEntity(wantedLevel);
	}

	public static boolean isPoliceUnit(Entity entity, JavaPlugin plugin) {
		if (!(entity instanceof LivingEntity)) return false;

		NamespacedKey           key = new NamespacedKey(plugin, "police_unit");
		PersistentDataContainer pdc = entity.getPersistentDataContainer();

		return pdc.has(key, PersistentDataType.BYTE);
	}

	public UUID getEntityId() {
		return entity.getUniqueId();
	}

	public void setLastKnownPlayerLocation(Location location) {
		this.lastKnownPlayerLocation = location.clone();
	}

	public void incrementLosLostTicks() {
		this.losLostTicks++;
	}

	public void resetLosLostTicks() {
		this.losLostTicks = 0;
	}

	public void incrementSearchTicks() {
		this.searchTicks++;
	}

	public void resetSearchTicks() {
		this.searchTicks = 0;
	}

	public boolean canAttack() {
		return attackCooldown <= 0;
	}

	public void resetAttackCooldown() {
		this.attackCooldown = PoliceConfig.ATTACK_COOLDOWN_TICKS;
	}

	public void decrementAttackCooldown() {
		if (attackCooldown > 0) attackCooldown--;
	}

	public boolean isValid() {
		return entity != null && !entity.isDead() && entity.isValid();
	}

	public boolean hasLineOfSight(Player player) {
		if (!isValid() || player == null) return false;
		return entity.hasLineOfSight(player);
	}

	public double distanceTo(Player player) {
		if (!isValid() || player == null) return Double.MAX_VALUE;
		return entity.getLocation().distance(player.getLocation());
	}

	public void pathfindTo(Location location) {
		if (!isValid() || location == null) return;

		double speed = 1.0 + (wantedLevelOnSpawn * 0.1);
		pathfindingHandler.navigateTo(entity, location, speed);
	}

	public void stopPathfinding() {
		if (!isValid()) return;
		pathfindingHandler.stopNavigation(entity);
	}

	public boolean isNavigating() {
		if (!isValid()) return false;
		return pathfindingHandler.isNavigating(entity);
	}

	public void attack(Player player) {
		if (!isValid() || !canAttack() || player == null) return;

		double damage = PoliceConfig.BASE_DAMAGE + (wantedLevelOnSpawn * 0.5);
		player.damage(damage, entity);
		resetAttackCooldown();

		// Knockback
		Vector knockback = player.getLocation()
								 .toVector()
								 .subtract(entity.getLocation().toVector())
								 .normalize()
								 .multiply(0.3)
								 .setY(0.1);
		player.setVelocity(player.getVelocity().add(knockback));
	}

	public void despawn(EntityMarkManager entityMarkManager) {
		if (entity != null && !entity.isDead()) {
			pathfindingHandler.stopNavigation(entity);
			pathfindingHandler.cleanup(entity.getUniqueId());
			entityMarkManager.removeEntityMark(entity);
			entity.remove();
		}
	}

	private void configureEntity(int wantedLevel) {
		// Clear vanilla AI goals for full custom control
		pathfindingHandler.clearAIGoals(entity);
		pathfindingHandler.setAIEnabled(entity, true); // Keep AI enabled for pathfinding

		entity.setRemoveWhenFarAway(false);
		entity.setPersistent(false);
		entity.setCustomName("§9Police");
		entity.setCustomNameVisible(true);

		// Set health based on wanted level
		double maxHealth  = 20.0 + (wantedLevel * 5);
		var    healthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
		if (healthAttr != null) {
			healthAttr.setBaseValue(maxHealth);
			entity.setHealth(maxHealth);
		}

		// Equip based on wanted level
		equipForLevel(wantedLevel);
	}

	private void equipForLevel(int wantedLevel) {
		EntityEquipment equipment = entity.getEquipment();
		if (equipment == null) return;

		ItemStack[] gear = PoliceConfig.getEquipmentForLevel(wantedLevel);
		equipment.setHelmet(gear[0]);
		equipment.setChestplate(gear[1]);
		equipment.setLeggings(gear[2]);
		equipment.setBoots(gear[3]);
		equipment.setItemInMainHand(gear[4]);

		// Prevent drops
		equipment.setHelmetDropChance(0f);
		equipment.setChestplateDropChance(0f);
		equipment.setLeggingsDropChance(0f);
		equipment.setBootsDropChance(0f);
		equipment.setItemInMainHandDropChance(0f);
	}
}
