package me.luckyraven.copsncrooks.police;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public final class PoliceConfig {

	// Spawn config
	public static final int    MAX_COPS_PER_PLAYER = 8;
	public static final double MIN_SPAWN_DISTANCE  = 30.0;
	public static final double MAX_SPAWN_DISTANCE  = 50.0;
	public static final double DESPAWN_DISTANCE    = 100.0;

	// AI tick rates (in ticks)
	public static final int AI_TICK_RATE       = 10;
	public static final int PATHFIND_TICK_RATE = 20;
	public static final int SPAWN_CHECK_RATE   = 40;

	// AI behavior
	public static final double ALERT_RANGE           = 40.0;
	public static final double CHASE_RANGE           = 30.0;
	public static final double COMBAT_RANGE          = 4.0;
	public static final double SEARCH_RADIUS         = 15.0;
	public static final int    LOS_TIMEOUT_TICKS     = 100;
	public static final int    SEARCH_DURATION_TICKS = 200;

	// Combat
	public static final double BASE_DAMAGE           = 2.0;
	public static final int    ATTACK_COOLDOWN_TICKS = 20;

	// Entity type for police
	public static final EntityType            POLICE_ENTITY_TYPE = EntityType.PILLAGER;
	// Cops to spawn per wanted level
	public static final Map<Integer, Integer> COPS_PER_LEVEL     = Map.of(1, 2, 2, 3, 3, 4, 4, 6, 5, 8);

	private PoliceConfig() { }

	// Equipment by wanted level
	public static ItemStack[] getEquipmentForLevel(int wantedLevel) {
		ItemStack helmet     = null;
		ItemStack chestplate = null;
		ItemStack leggings   = null;
		ItemStack boots      = null;
		ItemStack weapon;

		switch (wantedLevel) {
			case 1 -> weapon = new ItemStack(Material.WOODEN_SWORD);
			case 2 -> {
				weapon = new ItemStack(Material.STONE_SWORD);
				helmet = new ItemStack(Material.LEATHER_HELMET);
			}
			case 3 -> {
				weapon     = new ItemStack(Material.IRON_SWORD);
				helmet     = new ItemStack(Material.CHAINMAIL_HELMET);
				chestplate = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
			}
			case 4 -> {
				weapon     = new ItemStack(Material.IRON_SWORD);
				helmet     = new ItemStack(Material.IRON_HELMET);
				chestplate = new ItemStack(Material.IRON_CHESTPLATE);
				leggings   = new ItemStack(Material.IRON_LEGGINGS);
				boots      = new ItemStack(Material.IRON_BOOTS);
			}
			default -> {
				weapon     = new ItemStack(Material.DIAMOND_SWORD);
				helmet     = new ItemStack(Material.DIAMOND_HELMET);
				chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
				leggings   = new ItemStack(Material.DIAMOND_LEGGINGS);
				boots      = new ItemStack(Material.DIAMOND_BOOTS);
			}
		}

		return new ItemStack[]{helmet, chestplate, leggings, boots, weapon};
	}

	public static int getCopsForLevel(int level) {
		return COPS_PER_LEVEL.getOrDefault(level, Math.min(level + 1, MAX_COPS_PER_PLAYER));
	}

}
