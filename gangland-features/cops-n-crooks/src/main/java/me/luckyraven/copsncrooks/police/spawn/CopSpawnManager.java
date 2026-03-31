package me.luckyraven.copsncrooks.police.spawn;

import me.luckyraven.copsncrooks.entity.EntitySpawner;
import me.luckyraven.copsncrooks.police.config.CopConfigProvider;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.copsncrooks.police.npc.CopNpcFactory;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class CopSpawnManager extends EntitySpawner<CopSpawner> {

	private final CopNpcFactory     copNpcFactory;
	private final CopConfigProvider configProvider;

	public CopSpawnManager(CopNpcFactory copNpcFactory, CopConfigProvider configProvider,
	                       IRepository<CopSpawner> repository) {
		super(configProvider, repository);
		this.copNpcFactory  = copNpcFactory;
		this.configProvider = configProvider;
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
}
