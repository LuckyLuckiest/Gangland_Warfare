package me.luckyraven.copsncrooks.civilian;

import me.luckyraven.copsncrooks.civilian.config.CivilianGroupConfig;
import me.luckyraven.copsncrooks.civilian.config.CivilianNavigationConfig;
import me.luckyraven.copsncrooks.civilian.config.CivilianSettings;
import me.luckyraven.copsncrooks.civilian.config.CivilianTypeConfig;
import me.luckyraven.copsncrooks.civilian.state.CivilianBehavior;
import me.luckyraven.copsncrooks.civilian.state.CivilianBehaviorFactory;
import me.luckyraven.copsncrooks.entity.EntityMark;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.item.ItemParser;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.dto.AmmunitionData;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory for creating {@link CivilianNpc} instances backed by Citizens NPCs.
 */
public class CivilianNpcFactory {

	private static final int STARTING_AMMO_MAGAZINES = 3;

	private final           JavaPlugin               plugin;
	private final           EntityMarkManager        entityMarkManager;
	private final @Nullable ItemParser               itemParser;
	private final @Nullable WeaponService            weaponService;
	private final           CivilianBehaviorFactory  behaviorFactory;
	private final           CivilianNavigationConfig navConfig;

	public CivilianNpcFactory(JavaPlugin plugin, EntityMarkManager entityMarkManager,
	                          @Nullable ItemParser itemParser, @Nullable WeaponService weaponService,
	                          CivilianSettings civilianSettings) {
		this.plugin            = plugin;
		this.entityMarkManager = entityMarkManager;
		this.itemParser        = itemParser;
		this.weaponService     = weaponService;
		this.behaviorFactory   = new CivilianBehaviorFactory();
		this.navConfig         = CivilianNavigationConfig.from(civilianSettings);
	}

	/**
	 * Spawns and configures a single civilian NPC of the given type.
	 *
	 * @param spawnLocation the target spawn location
	 * @param typeConfig type configuration resolved from entity_marker.yml
	 * @param groupId the group this civilian belongs to, or {@code null}
	 * @param groupConfig group trait overrides (health/speed bonus), or {@code null}
	 *
	 * @return the created NPC, or {@code null} if Citizens failed to spawn it
	 */
	@Nullable
	public CivilianNpc createCivilian(Location spawnLocation, CivilianTypeConfig typeConfig,
	                                  @Nullable String groupId,
	                                  @Nullable CivilianGroupConfig groupConfig) {
		String plainName = ChatUtil.replaceColorCodes(ChatUtil.color(typeConfig.displayName()), "");

		EntityType entityType = typeConfig.entityType();
		NPC        npc        = CitizensAPI.getNPCRegistry().createNPC(entityType, plainName);
		npc.setProtected(false);
		npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
		npc.spawn(spawnLocation);

		if (!npc.isSpawned()) {
			npc.destroy();
			return null;
		}

		if (npc.getEntity() != null) {
			entityMarkManager.setEntityMark(npc.getEntity(), EntityMark.CIVILIAN);
		}

		Map<CivilianState, CivilianBehavior> behaviors = behaviorFactory.createBehaviors();

		CivilianNpc civilian = new CivilianNpc(npc, typeConfig, groupId, behaviors,
		                                       spawnLocation, navConfig, itemParser);

		// Apply group trait bonuses before equipping
		double healthBonus = groupConfig != null ? groupConfig.healthBonus() : 0.0;
		double speedBonus  = groupConfig != null ? groupConfig.speedBonus() : 0.0;

		applyHealthBonus(npc, typeConfig.health(), healthBonus);

		// Gangland weapon
		if (civilian.canUseWeapons() && weaponService != null) {
			Weapon weapon = resolveGanglandWeapon(typeConfig);
			if (weapon != null) {
				civilian.setHeldWeapon(weapon, plugin);
				giveStartingAmmo(npc, weapon);
			}
		}

		civilian.equip();

		float speedModifier = 1.0f + (float) speedBonus;
		npc.getNavigator().getLocalParameters().speedModifier(speedModifier);

		return civilian;
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void applyHealthBonus(NPC npc, double baseHealth, double bonus) {
		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity living)) return;

		double total = baseHealth + bonus;
		living.setMaxHealth(total);
		living.setHealth(total);
	}

	/**
	 * Picks a random gangland weapon from the type's weapon name pool.
	 */
	@Nullable
	private Weapon resolveGanglandWeapon(CivilianTypeConfig typeConfig) {
		List<String> pool = typeConfig.weaponNamePool();
		if (pool.isEmpty()) return null;

		int start = ThreadLocalRandom.current().nextInt(pool.size());
		for (int i = 0; i < pool.size(); i++) {
			String name   = pool.get((start + i) % pool.size());
			Weapon weapon = weaponService.getWeapon(name);
			if (weapon != null) return weapon;
		}
		return null;
	}

	/**
	 * Gives the NPC enough ammo in its off-hand to cover several full magazines.
	 */
	private void giveStartingAmmo(NPC npc, Weapon weapon) {
		Entity entity = npc.getEntity();
		if (!(entity instanceof LivingEntity livingEntity)) return;

		EntityEquipment equipment = livingEntity.getEquipment();
		if (equipment == null) return;

		AmmunitionData ammunitionData = weapon.getAmmunitionData();
		if (ammunitionData == null) return;

		Ammunition ammoType = ammunitionData.getAmmoType();
		if (ammoType == null) return;

		int       ammoCount = ammunitionData.getMaxMagCapacity() * STARTING_AMMO_MAGAZINES;
		ItemStack ammoItem  = ammoType.buildItem(Math.min(ammoCount, ammoType.buildItem().getMaxStackSize()));

		equipment.setItemInOffHand(ammoItem);

		if (!(livingEntity instanceof Player)) {
			equipment.setItemInOffHandDropChance(0f);
		}
	}
}
