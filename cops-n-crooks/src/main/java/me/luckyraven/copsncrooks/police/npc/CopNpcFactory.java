package me.luckyraven.copsncrooks.police.npc;

import me.luckyraven.copsncrooks.entity.EntityMark;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.config.CopTierConfig;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import me.luckyraven.copsncrooks.police.state.CopBehavior;
import me.luckyraven.copsncrooks.police.state.CopBehaviorFactory;
import me.luckyraven.copsncrooks.police.state.CopState;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.Ammunition;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory for creating CopNpc instances backed by Citizens NPCs.
 */
public class CopNpcFactory {

	private static final int MIN_OPEN_HORIZONTAL_SIDES = 2;

	private final CopConfigProvider  configProvider;
	private final CopBehaviorFactory behaviorFactory;
	private final EntityMarkManager  entityMarkManager;
	private final JavaPlugin         plugin;
	private final WeaponService      weaponService;

	public CopNpcFactory(CopConfigProvider configProvider, EntityMarkManager entityMarkManager,
						 CopSpawnManager spawnManager, JavaPlugin plugin, WeaponService weaponService) {
		this.configProvider    = configProvider;
		this.behaviorFactory   = new CopBehaviorFactory(configProvider, spawnManager);
		this.entityMarkManager = entityMarkManager;
		this.plugin            = plugin;
		this.weaponService     = weaponService;
	}

	/**
	 * Creates a new cop NPC at the given location with the specified tier.
	 *
	 * @param spawnLocation the location to spawn the NPC
	 * @param tier the cop tier
	 *
	 * @return the created CopNpc, or null if spawning failed
	 */
	public CopNpc createCop(Location spawnLocation, int tier) {
		CopTierConfig tierConfig = configProvider.getTierConfig(tier);

		String plainName = ChatUtil.replaceColorCodes(ChatUtil.color(tierConfig.displayName()), "");

		NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, plainName);
		npc.setProtected(false);
		npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
		npc.spawn(spawnLocation);

		if (!npc.isSpawned()) {
			npc.destroy();
			return null;
		}

		scheduleDelayedSpawnValidation(npc);

		if (npc.getEntity() != null) {
			entityMarkManager.setEntityMark(npc.getEntity(), EntityMark.POLICE);
		}

		Map<CopState, CopBehavior> behaviors = behaviorFactory.createBehaviors();
		CopNpc                     copNpc    = new CopNpc(npc, tierConfig, behaviors, spawnLocation);

		// Resolve and assign a gangland weapon when the tier supports it
		if (tierConfig.canUseWeapons()) {
			Weapon weapon = resolveGanglandWeapon(tierConfig);
			if (weapon != null) {
				copNpc.setHeldWeapon(weapon, plugin);
				giveStartingAmmo(npc, weapon);
			}
		}

		copNpc.equip();
		npc.getNavigator().getLocalParameters().speedModifier((float) tierConfig.speed());

		return copNpc;
	}

	/**
	 * Schedules a 1-tick delayed validation of the NPC's actual spawned position. This acts as a fail-safe for cases
	 * where the entity is nudged into an unsafe location immediately after spawning.
	 *
	 * @param npc the spawned npc
	 */
	private void scheduleDelayedSpawnValidation(NPC npc) {
		plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			if (npc == null || !npc.isSpawned()) return;
			if (npc.getEntity() == null || !isSafeSpawnPosition(npc.getEntity())) {
				npc.destroy();
			}
		}, 1L);
	}

	/**
	 * Validates the actual spawned entity position using the entity's real block coordinates.
	 *
	 * @param entity the spawned entity
	 *
	 * @return true if the entity has safe breathing room and enough horizontal clearance
	 */
	private boolean isSafeSpawnPosition(Entity entity) {
		if (entity == null) return false;

		Location location = entity.getLocation();
		World    world    = location.getWorld();
		if (world == null) return false;

		int x = location.getBlockX();
		int y = location.getBlockY();
		int z = location.getBlockZ();

		Block feet      = world.getBlockAt(x, y, z);
		Block head      = world.getBlockAt(x, y + 1, z);
		Block aboveHead = world.getBlockAt(x, y + 2, z);

		if (!feet.isEmpty() || !head.isEmpty() || !aboveHead.isEmpty()) return false;

		return hasEnoughHorizontalClearance(world, x, y, z) && hasEnoughHorizontalClearance(world, x, y + 1, z);
	}

	/**
	 * Counts the open horizontal sides around the given block position.
	 *
	 * @param world the world
	 * @param x center x
	 * @param y center y
	 * @param z center z
	 *
	 * @return true if at least the minimum number of sides are open
	 */
	private boolean hasEnoughHorizontalClearance(World world, int x, int y, int z) {
		int openSides = 0;

		if (world.getBlockAt(x + 1, y, z).isEmpty()) openSides++;
		if (world.getBlockAt(x - 1, y, z).isEmpty()) openSides++;
		if (world.getBlockAt(x, y, z + 1).isEmpty()) openSides++;
		if (world.getBlockAt(x, y, z - 1).isEmpty()) openSides++;

		return openSides >= MIN_OPEN_HORIZONTAL_SIDES;
	}

	/**
	 * Picks a random name from the tier's weapon pool and resolves it as a gangland Weapon. Names that are not
	 * registered as gangland weapons return null (they are vanilla materials).
	 */
	private Weapon resolveGanglandWeapon(CopTierConfig tierConfig) {
		List<String> pool = tierConfig.weaponNamePool();
		if (pool.isEmpty()) return null;

		// Start at a random index so each cop gets a varied pick
		int start = ThreadLocalRandom.current().nextInt(pool.size());
		for (int i = 0; i < pool.size(); i++) {
			String name   = pool.get((start + i) % pool.size());
			Weapon weapon = weaponService.getWeapon(name);
			if (weapon != null) return weapon;
		}
		return null;
	}

	/**
	 * Gives the NPC enough ammo items (in its off-hand) to cover several full reloads. This ensures the reload system,
	 * which reads from entity inventory, finds bullets available.
	 */
	private void giveStartingAmmo(NPC npc, Weapon weapon) {
		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return;

		Ammunition ammoType = weapon.getReloadData().getAmmoType();
		if (ammoType == null) return;

		// Give enough for 3 full magazines worth of reloads
		int       ammoCount = weapon.getReloadData().getMaxMagCapacity() * 3;
		ItemStack ammoItem  = ammoType.buildItem(Math.min(ammoCount, ammoType.buildItem().getMaxStackSize()));

		equipment.setItemInOffHand(ammoItem);

		if (!(livingEntity instanceof Player)) {
			equipment.setItemInOffHandDropChance(0f);
		}
	}
}