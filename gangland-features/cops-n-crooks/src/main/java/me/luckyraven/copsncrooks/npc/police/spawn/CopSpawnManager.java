package me.luckyraven.copsncrooks.npc.police.spawn;

import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.entity.EntityMarkManager;
import me.luckyraven.copsncrooks.entity.EntitySpawner;
import me.luckyraven.copsncrooks.npc.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.npc.police.config.CopLoader;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpcFactory;
import me.luckyraven.copsncrooks.npc.police.state.CopBehaviorFactory;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.weapon.WeaponService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public class CopSpawnManager extends EntitySpawner<CopSpawner> {

	private final JavaPlugin        plugin;
	private final CopLoader         copLoader;
	private final EntityMarkManager entityMarkManager;
	private final WeaponService     weaponService;
	private final DetainmentService detainmentService;

	private CopNpcFactory     copNpcFactory;
	private CopConfigProvider configProvider;

	public CopSpawnManager(JavaPlugin plugin, CopLoader copLoader, EntityMarkManager entityMarkManager,
	                       WeaponService weaponService, IRepository<CopSpawner> repository,
	                       DetainmentService detainmentService) {
		super(copLoader.getLoadedProvider(), repository);
		this.plugin            = plugin;
		this.copLoader         = copLoader;
		this.entityMarkManager = entityMarkManager;
		this.weaponService     = weaponService;
		this.detainmentService = detainmentService;

		rebuildFactories();
	}

	@Override
	public void onClear() {
		super.onClear();
		copNpcFactory  = null;
		configProvider = null;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		super.onInitialize(firstLoad);
		rebuildFactories();
	}

	/**
	 * Spawns a cop NPC near the given player with the specified tier.
	 *
	 * @param target the player to spawn near
	 * @param tier the cop tier
	 *
	 * @return the spawned CopNpc, or null if no valid location was found
	 */
	@Nullable
	public CopNpc spawnNearPlayer(Player target, int tier) {
		Location spawnLoc = findClosestSpawnerLocation(target);

		if (spawnLoc != null) {
			return copNpcFactory.createCop(spawnLoc, tier);
		}

		spawnLoc = findSpawnLocation(target);

		if (spawnLoc == null) return null;

		return copNpcFactory.createCop(spawnLoc, tier, true);
	}

	/**
	 * Spawns a cop NPC at a specific configured spawn location.
	 *
	 * @param location the spawn location
	 * @param tier the cop tier
	 *
	 * @return the spawned CopNpc, or null on failure
	 */
	@Nullable
	public CopNpc spawnAtLocation(Location location, int tier) {
		return copNpcFactory.createCop(location, tier);
	}

	/**
	 * Returns the number of cops that should be active for a given wanted level.
	 *
	 * @param wantedLevel the player's wanted level
	 *
	 * @return the target cop count
	 */
	public int getTargetCopCount(int wantedLevel) {
		return configProvider.getCopsPerWantedLevel()
		                     .getOrDefault(wantedLevel,
		                                   Math.min(wantedLevel + 1, configProvider.getMaxCopsPerPlayer()));
	}

	/**
	 * Determines the cop tier that should be spawned for a given wanted level.
	 *
	 * @param wantedLevel the player's wanted level
	 *
	 * @return the tier number
	 */
	public int getTierForWantedLevel(int wantedLevel) {
		return Math.min(wantedLevel, configProvider.getMaxTier());
	}

	@Override
	protected CopSpawner createSpawnerPoint(int id, Location location) {
		return new CopSpawner(id, location);
	}

	private void rebuildFactories() {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		this.configProvider = provider;

		CopBehaviorFactory behaviorFactory = new CopBehaviorFactory(provider, () -> this, detainmentService);
		this.copNpcFactory = new CopNpcFactory(plugin, provider, behaviorFactory, entityMarkManager, weaponService);
	}
}
