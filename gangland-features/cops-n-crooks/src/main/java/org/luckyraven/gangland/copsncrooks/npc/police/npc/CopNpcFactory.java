package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory for creating CopNpc instances backed by Citizens NPCs. {@link BartizanNpcWeapons}/{@link DownedTargetFilter}/
 * {@link NpcMarkManager} are civilians-module beans injected here through cops' {@code Depends: [civilians]} — not
 * duplicated (see {@code gangland-civilians}' {@code CiviliansModuleConfig}).
 */
public class CopNpcFactory {

	private final CopConfigProvider  configProvider;
	private final CopBehaviorFactory behaviorFactory;
	private final NpcMarkManager     markManager;
	private final JavaPlugin         plugin;
	private final BartizanNpcWeapons bartizanNpcWeapons;
	private final DownedTargetFilter downedTargetFilter;

	public CopNpcFactory(JavaPlugin plugin, CopConfigProvider configProvider, CopBehaviorFactory behaviorFactory,
	                     NpcMarkManager markManager, BartizanNpcWeapons bartizanNpcWeapons,
	                     DownedTargetFilter downedTargetFilter) {
		this.plugin             = plugin;
		this.configProvider     = configProvider;
		this.behaviorFactory    = behaviorFactory;
		this.markManager        = markManager;
		this.bartizanNpcWeapons = bartizanNpcWeapons;
		this.downedTargetFilter = downedTargetFilter;
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
		return createCop(spawnLocation, tier, false);
	}

	/**
	 * Creates a new cop NPC at the given location with the specified tier.
	 *
	 * @param spawnLocation the location to spawn the NPC
	 * @param tier the cop tier
	 * @param validateAfterSpawn when true, schedules a 1-tick safety check to destroy the NPC if it ended up clipped
	 * 		inside geometry; used for random indoor spawns where Citizens may nudge the entity unexpectedly
	 *
	 * @return the created CopNpc, or null if spawning failed
	 */
	public CopNpc createCop(Location spawnLocation, int tier, boolean validateAfterSpawn) {
		if (!NpcSupport.available()) return null;

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

		if (validateAfterSpawn) {
			scheduleDelayedSpawnValidation(npc);
		}

		if (npc.getEntity() != null) {
			markManager.setMark(npc.getEntity(), EntityMark.POLICE.name());
		}

		Map<CopState, CopBehavior> behaviors = behaviorFactory.createBehaviors();

		CopNpc copNpc = new CopNpc(plugin, npc, tierConfig, behaviors, spawnLocation, configProvider);
		copNpc.setTargetFilter(downedTargetFilter);

		// equip() runs first so the ranged-attack block below can override its vanilla weaponPool main-hand item
		// with the Bartizan-built weapon item, reproducing 0.8.4's heldWeapon != null ? heldWeapon.buildItem() :
		// weaponPool precedence (matches CivilianNpcFactory, T-HR2).
		copNpc.equip();

		// Bartizan-backed ranged weapon: a random name from the tier's pool, resolved through the factory hook.
		// NpcRangedAttack.NONE (no weapon name configured, unresolvable name, or Bartizan absent) leaves the cop on
		// the vanilla weaponPool fallback CopNpc#equip() already applied above. Bartizan owns the NPC magazine —
		// no off-hand ammo item is stocked here (0.8.4's giveStartingAmmo is gone).
		if (tierConfig.canUseWeapons()) {
			String          weaponName   = pickWeaponName(tierConfig);
			NpcRangedAttack rangedAttack = bartizanNpcWeapons.create(copNpc.getEntity(), weaponName,
			                                                        copNpc.getDifficulty());
			copNpc.setRangedAttack(rangedAttack);

			ItemStack weaponItem = bartizanNpcWeapons.buildItem(weaponName);
			if (weaponItem != null) {
				setMainHand(copNpc.getEntity(), weaponItem);
			}
		}

		npc.getNavigator().getLocalParameters().speedModifier((float) tierConfig.speed());

		return copNpc;
	}

	private void setMainHand(@Nullable LivingEntity entity, ItemStack item) {
		if (entity == null) return;
		EntityEquipment equipment = entity.getEquipment();
		if (equipment == null) return;
		equipment.setItemInMainHand(item);
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

			Entity entity = npc.getEntity();
			// Citizens PLAYER NPCs may have a null entity for a tick while initializing; skip rather than destroy.
			if (entity == null) return;
			if (isSafeSpawnPosition(entity)) return;

			npc.destroy();
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

		// Physics timing between spawn and this 1-tick callback can leave the entity fractionally inside
		// the ground block before collision resolution runs. Step up one block so we evaluate the position
		// the entity will actually occupy once standing.
		if (!world.getBlockAt(x, y, z).isPassable()) {
			y++;
		}

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

		return openSides >= configProvider.getMinOpenHorizontalSides();
	}

	/**
	 * Picks a random weapon name from the tier's weapon name pool, or {@code null} if the pool is empty.
	 * {@link BartizanNpcWeapons#create} handles an unresolvable name the same way as an empty pool.
	 */
	@Nullable
	private String pickWeaponName(CopTierConfig tierConfig) {
		List<String> pool = tierConfig.weaponNamePool();
		if (pool.isEmpty()) return null;

		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}
}