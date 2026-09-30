package org.luckyraven.gangland.copsncrooks.npc.police.spawn;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.combat.BartizanNpcWeapons;
import org.luckyraven.gangland.civilians.npc.combat.DownedTargetFilter;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpcFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehaviorFactory;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.keystone.npc.entity.EntitySpawner;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.util.function.Predicate;

public class CopSpawnManager extends EntitySpawner<CopSpawner> {

	private final JavaPlugin         plugin;
	private final CopLoader          copLoader;
	private final NpcMarkManager     markManager;
	private final BartizanNpcWeapons bartizanNpcWeapons;
	private final DownedTargetFilter downedTargetFilter;
	private final DetainmentService  detainmentService;
	private final CuffLockRegistry   cuffLockRegistry;

	CopNpcFactory             copNpcFactory; // package-private: tests swap in a mock
	private CopConfigProvider configProvider;
	/** While set, every spot counts as outdoor: the ring takes indoor and outdoor spots alike. */
	private boolean           anyRoof;

	public CopSpawnManager(JavaPlugin plugin, CopLoader copLoader, NpcMarkManager markManager,
	                       BartizanNpcWeapons bartizanNpcWeapons, DownedTargetFilter downedTargetFilter,
	                       IRepository<CopSpawner> repository, DetainmentService detainmentService,
	                       CuffLockRegistry cuffLockRegistry) {
		super(copLoader.getLoadedProvider(), repository);
		this.plugin             = plugin;
		this.copLoader          = copLoader;
		this.markManager        = markManager;
		this.bartizanNpcWeapons = bartizanNpcWeapons;
		this.downedTargetFilter = downedTargetFilter;
		this.detainmentService  = detainmentService;
		this.cuffLockRegistry   = cuffLockRegistry;

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
	 * Spawns a cop NPC near the given player with the specified tier: at the closest registered spawner whose location
	 * {@code allowed} accepts, else somewhere on the ring around him.
	 *
	 * @param target the player to spawn near
	 * @param tier the cop tier
	 * @param allowed which spawner locations may be used (a recycled cop's spawner is skipped for a while)
	 *
	 * @return the spawned CopNpc, or null if no valid location was found
	 */
	@Nullable
	public CopNpc spawnNearPlayer(Player target, int tier, Predicate<Location> allowed) {
		Location spawnLoc = findClosestSpawnerLocation(target, allowed);

		if (spawnLoc != null) {
			return copNpcFactory.createCop(spawnLoc, tier);
		}

		spawnLoc = findRingLocation(target);

		if (spawnLoc == null) return null;

		return copNpcFactory.createCop(spawnLoc, tier, true);
	}

	/**
	 * A spot on the ring around {@code target}. The ring only takes spots as indoor or outdoor as the suspect, so a
	 * suspect under a roof with nothing but open street around him got none, and no cop ever came; for him the ring
	 * is searched again taking either kind (still at his level, within {@code Spawn.Max_Y_Diff}).
	 */
	@Nullable
	Location findRingLocation(Player target) {
		Location spot = findSpawnLocation(target);
		if (spot != null || isOutdoor(target.getLocation())) return spot;

		anyRoof = true;
		try {
			return findSpawnLocation(target);
		} finally {
			anyRoof = false;
		}
	}

	// ponytail: leans on EntitySpawner judging both the suspect and each ring spot through isOutdoor; a Keystone ring
	// option (match indoor/outdoor or not) replaces this if that ever changes.
	@Override
	protected boolean isOutdoor(Location location) {
		return anyRoof || super.isOutdoor(location);
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

		CopBehaviorFactory behaviorFactory = new CopBehaviorFactory(provider, () -> this, detainmentService,
		                                                            cuffLockRegistry);
		this.copNpcFactory = new CopNpcFactory(plugin, provider, behaviorFactory, markManager, bartizanNpcWeapons,
		                                       downedTargetFilter);
	}
}
