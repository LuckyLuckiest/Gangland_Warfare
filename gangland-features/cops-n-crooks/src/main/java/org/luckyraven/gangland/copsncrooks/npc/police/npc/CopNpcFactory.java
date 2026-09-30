package org.luckyraven.gangland.copsncrooks.npc.police.npc;

import com.cryptomorin.xseries.XAttribute;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.HologramTrait;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
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
import org.luckyraven.gangland.civilians.npc.npc.CitizensNpcs;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopNames;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.npc.NpcFireRate;
import org.luckyraven.keystone.npc.NpcMeleeProfile;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.npc.spi.NpcRangedAttack;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

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
		return createCop(spawnLocation, tier, validateAfterSpawn, null);
	}

	/**
	 * Creates a new cop NPC with {@code role} laid over its tier ({@link CopRole#overlay}): the role's health,
	 * difficulty, fire rate and strafe reach the cop through the tier config, its placement, leader priority and band
	 * through {@link #applyRole}, its off-hand item through the loadout.
	 *
	 * @param role the cop's squad role; {@code null} spawns the plain tier
	 */
	public CopNpc createCop(Location spawnLocation, int tier, boolean validateAfterSpawn, @Nullable CopRole role) {
		if (!NpcSupport.available()) return null;

		CopTierConfig baseTier   = configProvider.getTierConfig(tier);
		CopTierConfig tierConfig = role != null ? role.overlay(baseTier) : baseTier;

		CopNames names = configProvider.getNames() != null ? configProvider.getNames() : CopNames.DEFAULT;

		// Created under a placeholder: the badge is the Citizens id, only known once the NPC exists.
		NPC    npc      = CitizensNpcs.create(EntityType.PLAYER, "Officer");
		String callsign = CitizensBridge.nameplate(npc, names, tierConfig.displayName(),
		                                           role != null ? role.displayName() : "", ThreadLocalRandom.current());
		npc.setProtected(false);
		npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
		// The callsign hologram line stands in for the nameplate (CitizensBridge#nameplate).
		npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
		npc.spawn(spawnLocation);

		if (!npc.isSpawned()) {
			npc.destroy();
			return null;
		}

		if (validateAfterSpawn) {
			CitizensBridge.scheduleDelayedSpawnValidation(plugin, npc, this::isSafeSpawnPosition);
		}

		Map<CopState, CopBehavior> behaviors = behaviorFactory.createBehaviors();

		CopNpc copNpc = new CopNpc(plugin, npc, tierConfig, behaviors, spawnLocation, configProvider);
		copNpc.setCallsign(callsign);
		copNpc.setTargetFilter(downedTargetFilter);
		copNpc.setRole(role);

		// Bartizan-backed ranged weapon: a random name from the tier's pool, resolved through the factory hook.
		// NpcRangedAttack.NONE (no weapon name configured, unresolvable name, or Bartizan absent) leaves the cop on
		// the vanilla weaponPool fallback CopNpc#equip() applies. Bartizan owns the NPC magazine — no off-hand ammo
		// item is stocked here (0.8.4's giveStartingAmmo is gone). The supplier keeps Bartizan on the live entity.
		NpcRangedAttack rangedAttack = NpcRangedAttack.NONE;
		ItemStack       weaponItem   = null;
		String          weaponName   = null;
		if (tierConfig.canUseWeapons()) {
			weaponName = pickWeaponName(tierConfig);
			rangedAttack = bartizanNpcWeapons.create(copNpc::getEntity, weaponName, copNpc.getDifficulty());
			copNpc.setRangedAttack(rangedAttack);
			weaponItem = bartizanNpcWeapons.buildItem(weaponName);
		}

		// Everything on the entity itself goes through the loadout, which CopNpc re-applies to a replacement entity
		// (any Citizens respawn, e.g. a chunk reload; the skin-fetch respawn is stopped at source in CitizensNpcs). equip() runs before the weapon item so the Bartizan-built item overrides
		// its vanilla weaponPool main hand, reproducing 0.8.4's heldWeapon precedence (matches CivilianNpcFactory).
		copNpc.setLoadout(loadout(copNpc, tierConfig, markManager, weaponItem));

		applyTuning(copNpc, tierConfig, configProvider, rangedAttack);
		applyRole(copNpc, role, bartizanNpcWeapons.reach(weaponName));

		copNpc.applySpeed(1.0);

		return copNpc;
	}

	/**
	 * The entity-level loadout: POLICE mark, tier health, {@link CopNpc#equip()} armour, the held weapon item, then the
	 * role's off-hand item (the Defender's shield). No drop chance is set for it: cops are PLAYER entities, whose
	 * drop-chance setters throw, and a dead cop's drops are cleared anyway.
	 */
	static Consumer<LivingEntity> loadout(CopNpc copNpc, CopTierConfig tierConfig, NpcMarkManager markManager,
	                                      @Nullable ItemStack heldWeapon) {
		return entity -> {
			markManager.setMark(entity, EntityMark.POLICE.name());
			applyHealthBonus(entity, tierConfig.health());
			copNpc.equip();
			if (heldWeapon != null) setMainHand(entity, heldWeapon.clone());
			CopRole role = copNpc.getRole();
			if (role != null && role.offHand() != null && entity.getEquipment() != null)
				entity.getEquipment().setItemInOffHand(role.offHand().clone());
		};
	}

	/**
	 * The role's formation hooks: where on the fan the cop stands, its claim to lead the squad, and its own firing band
	 * clamped under {@code reach} (the held gun's range, {@code null} when unknown). Nothing for no role.
	 */
	static void applyRole(CopNpc copNpc, @Nullable CopRole role, @Nullable Double reach) {
		if (role == null) return;
		copNpc.setFanPlacement(role.placement());
		copNpc.setLeaderPriority(role.leaderPriority());
		double[] band = role.rangedBand(reach);
		if (band != null) copNpc.setRangedBand(band[0], band[1]);
	}

	/** Squad engagement, melee band and gun cadence from the tier's config ({@link NpcFireRate#scale}). */
	static void applyTuning(CopNpc copNpc, CopTierConfig tierConfig, CopConfigProvider configProvider,
	                        NpcRangedAttack rangedAttack) {
		copNpc.setEngagement(tierConfig.tactics().engagement());
		copNpc.setMeleeProfile(meleeFor(configProvider.getMeleeProfile(), tierConfig));
		copNpc.setFireRateScale(NpcFireRate.scale(tierConfig.fireRateMultiplier(), rangedAttack));
	}

	/**
	 * The tier's melee profile, with {@code approach} clamped below the tier's own {@code Cuff_Radius} — a melee cop
	 * that surrounds and cuffs first must not settle further out than it can reach to cuff.
	 */
	static NpcMeleeProfile meleeFor(NpcMeleeProfile profile, CopTierConfig tier) {
		double approach = Math.max(0.5, Math.min(profile.approach(), tier.cuffRadius() - 0.5));
		return new NpcMeleeProfile(profile.reach(), approach, profile.cooldownTicks(), profile.damageSpread(),
		                           profile.edgeDamage());
	}

	/** Applies the tier's configured {@code Health} as both the max-health attribute base and current health. */
	static void applyHealthBonus(@Nullable Entity entity, double health) {
		if (!(entity instanceof LivingEntity living)) return;

		AttributeInstance maxHealth = living.getAttribute(XAttribute.MAX_HEALTH.get());
		if (maxHealth != null) {
			maxHealth.setBaseValue(health);
		}
		living.setHealth(health);
	}

	private static void setMainHand(@Nullable LivingEntity entity, ItemStack item) {
		if (entity == null) return;
		EntityEquipment equipment = entity.getEquipment();
		if (equipment == null) return;
		equipment.setItemInMainHand(item);
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

	/**
	 * Citizens-typed helpers live on a nested class, not this factory directly (D2/D-fix-1, defense-in-depth):
	 * {@code CopNpcFactory} is not itself Keystone-scanned today (it is plain-constructed by
	 * {@code CopSpawnManager#rebuildFactories}, never returned from a {@code @Bean} method), but keeping every
	 * Citizens type off this class's own declared-method signatures matches the pattern used for the confirmed-bean
	 * {@code CivilianNpcFactory}/{@code NpcDamageUnprotectListener} and removes any risk if this factory is ever
	 * exposed as a bean later.
	 */
	static final class CitizensBridge {

		/**
		 * Names the cop before it spawns and returns its coloured callsign ({@code Cops.Names}). The Citizens name is
		 * the short plain {@code "Bob #1592"}: at 16 characters or fewer with no colour Citizens keeps it as the
		 * entity's profile name, so {@code Player#getName()} (death and kill messages, tab) never shows the
		 * {@code CIT-...} team name. The caller hides the nameplate ({@code NAMEPLATE_VISIBLE}; not set here because
		 * {@code NPC.Metadata} cannot be class-loaded in unit tests) and the full coloured callsign is hologram
		 * line 0 in its place ({@code HologramTrait} stacks lines upward from the hidden plate, so a line inserted
		 * at 0, e.g. the healthbars module's bar, sits directly under the callsign).
		 */
		static String nameplate(NPC npc, CopNames names, String rank, String role, Random random) {
			String firstName = names.pickName(random);
			int    badge     = CopNames.badge(npc.getId());
			String callsign  = names.callsign(rank, role, firstName, badge);

			npc.setName(CopNames.shortName(firstName, badge));
			npc.getOrAddTrait(HologramTrait.class).addLine(ChatUtil.color(callsign));
			return callsign;
		}

		/**
		 * Schedules a 1-tick delayed validation of the NPC's actual spawned position. This acts as a fail-safe for
		 * cases where the entity is nudged into an unsafe location immediately after spawning.
		 *
		 * @param plugin the owning plugin, used to schedule the delayed task
		 * @param npc the spawned npc
		 * @param isSafeSpawnPosition callback validating the entity's actual spawned position
		 */
		private static void scheduleDelayedSpawnValidation(JavaPlugin plugin, NPC npc,
		                                                    java.util.function.Predicate<Entity> isSafeSpawnPosition) {
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
				if (npc == null || !npc.isSpawned()) return;

				Entity entity = npc.getEntity();
				// Citizens PLAYER NPCs may have a null entity for a tick while initializing; skip rather than destroy.
				if (entity == null) return;
				if (isSafeSpawnPosition.test(entity)) return;

				npc.destroy();
			}, 1L);
		}
	}
}