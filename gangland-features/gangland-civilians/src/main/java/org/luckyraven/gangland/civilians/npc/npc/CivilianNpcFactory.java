package org.luckyraven.gangland.civilians.npc.npc;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.civilians.npc.config.CivilianGroupConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianNavigationConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.state.CivilianBehavior;
import org.luckyraven.gangland.civilians.npc.state.CivilianBehaviorFactory;
import org.luckyraven.gangland.civilians.npc.entity.EntityMark;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.item.ItemParser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory for creating {@link CivilianNpc} instances backed by Citizens NPCs.
 */
public class CivilianNpcFactory implements BeanLifecycle {

	private final           JavaPlugin          plugin;
	private final           NpcMarkManager      markManager;
	private final @Nullable ItemParser          itemParser;
	private final           BartizanNpcWeapons  bartizanNpcWeapons;
	private final           DownedTargetFilter  downedTargetFilter;
	private final           CivilianSettings    civilianSettings;

	private CivilianBehaviorFactory  behaviorFactory;
	private CivilianNavigationConfig navConfig;

	public CivilianNpcFactory(JavaPlugin plugin, NpcMarkManager markManager, @Nullable ItemParser itemParser,
	                          BartizanNpcWeapons bartizanNpcWeapons, DownedTargetFilter downedTargetFilter,
	                          CivilianSettings civilianSettings) {
		this.plugin             = plugin;
		this.markManager        = markManager;
		this.itemParser         = itemParser;
		this.bartizanNpcWeapons = bartizanNpcWeapons;
		this.downedTargetFilter = downedTargetFilter;
		this.civilianSettings   = civilianSettings;
		this.behaviorFactory    = new CivilianBehaviorFactory(civilianSettings.getCivilianSpawnerSoftLeashRadius());
		this.navConfig          = CivilianNavigationConfig.from(civilianSettings);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (firstLoad) return;
		this.navConfig       = CivilianNavigationConfig.from(civilianSettings);
		this.behaviorFactory = new CivilianBehaviorFactory(civilianSettings.getCivilianSpawnerSoftLeashRadius());
	}

	/**
	 * Spawns and configures a single civilian NPC of the given type.
	 *
	 * @param spawnLocation the target spawn location
	 * @param typeConfig type configuration resolved from civilians.yml
	 * @param groupId the group this civilian belongs to, or {@code null}
	 * @param groupConfig group trait overrides (health/speed bonus), or {@code null}
	 *
	 * @return the created NPC, or {@code null} if Citizens failed to spawn it
	 */
	@Nullable
	public CivilianNpc createCivilian(Location spawnLocation, CivilianTypeConfig typeConfig,
	                                  @Nullable String groupId,
	                                  @Nullable CivilianGroupConfig groupConfig) {
		if (!NpcSupport.available()) return null;

		String plainName = ChatUtil.replaceColorCodes(ChatUtil.color(typeConfig.displayName()), "");

		EntityType entityType = typeConfig.entityType();
		NPC        npc        = CitizensAPI.getNPCRegistry().createNPC(entityType, plainName);
		npc.setProtected(false);
		npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
		npc.data().setPersistent(NPC.Metadata.USE_MINECRAFT_AI, false);
		npc.spawn(spawnLocation);

		if (!npc.isSpawned()) {
			npc.destroy();
			return null;
		}

		if (npc.getEntity() != null) {
			markManager.setMark(npc.getEntity(), EntityMark.CIVILIAN.name());
		}

		Map<CivilianState, CivilianBehavior> behaviors = behaviorFactory.createBehaviors();

		CivilianNpc civilian = new CivilianNpc(plugin, npc, typeConfig, groupId, behaviors,
		                                       spawnLocation, navConfig, itemParser);
		civilian.setTargetFilter(downedTargetFilter);

		// Apply group trait bonuses before equipping
		double healthBonus = groupConfig != null ? groupConfig.healthBonus() : 0.0;
		double speedBonus  = groupConfig != null ? groupConfig.speedBonus() : 0.0;

		applyHealthBonus(npc.getEntity(), typeConfig.health(), healthBonus);

		// equip() runs first so the ranged-attack block below can override its vanilla weaponPool main-hand item
		// with the Bartizan-built weapon item, reproducing 0.8.4's heldWeapon != null ? heldWeapon.buildItem() :
		// weaponPool precedence.
		civilian.equip();

		// Bartizan-backed ranged weapon: a random name from the type's pool, resolved through the factory hook.
		// NpcRangedAttack.NONE (no weapon name configured, unresolvable name, or Bartizan absent) leaves the
		// civilian on the vanilla weaponPool fallback CivilianNpc#equip() already applied above.
		if (civilian.canUseRangedAttack()) {
			String          weaponName   = pickWeaponName(typeConfig);
			NpcRangedAttack rangedAttack = bartizanNpcWeapons.create(civilian.getEntity(), weaponName,
			                                                        civilian.getDifficulty());
			civilian.setRangedAttack(rangedAttack);

			ItemStack weaponItem = bartizanNpcWeapons.buildItem(weaponName);
			if (weaponItem != null) {
				setMainHand(civilian.getEntity(), weaponItem);
			}
		}

		float speedModifier = 1.0f + (float) speedBonus;
		npc.getNavigator().getLocalParameters().speedModifier(speedModifier);

		return civilian;
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void setMainHand(@Nullable LivingEntity entity, ItemStack item) {
		if (entity == null) return;
		EntityEquipment equipment = entity.getEquipment();
		if (equipment == null) return;
		equipment.setItemInMainHand(item);
	}

	// Takes Entity (not Citizens' NPC) so this class's own getDeclaredMethods() never resolves a Citizens type when
	// scanned as a bean on a Citizens-less server (D2/D-fix-1) — the only thing this ever needed was npc.getEntity().
	private void applyHealthBonus(@Nullable Entity entity, double baseHealth, double bonus) {
		if (!(entity instanceof LivingEntity living)) return;

		double total = baseHealth + bonus;

		AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(total);
		}
		living.setHealth(total);
	}

	/**
	 * Picks a random weapon name from the type's weapon name pool, or {@code null} if the pool is empty.
	 * {@link BartizanNpcWeapons#create} handles an unresolvable name the same way as an empty pool.
	 */
	@Nullable
	private String pickWeaponName(CivilianTypeConfig typeConfig) {
		List<String> pool = typeConfig.weaponNamePool();
		if (pool.isEmpty()) return null;

		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

}
