package me.luckyraven.copsncrooks.npc.civilian.spawn;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.entity.EntitySpawner;
import me.luckyraven.copsncrooks.entity.SpawnConfigProvider;
import me.luckyraven.copsncrooks.npc.civilian.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianTypeConfig;
import me.luckyraven.copsncrooks.npc.civilian.config.EntityMarkerConfig;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Manages civilian spawn points and delegates NPC creation to {@link CivilianService}.
 * <p>
 * Inherits all location-finding logic (surface search, clearance checks, player-proximity filtering) from
 * {@link EntitySpawner}.
 */
@CustomLog
public class CivilianSpawnManager extends EntitySpawner<CivilianSpawner> {

	private final CivilianService    civilianService;
	private final EntityMarkerConfig markerConfig;

	/**
	 * @param config spawn location config (min/max distances, clearance, etc.)
	 * @param repository persistent spawner storage; may be {@code null} to skip persistence
	 * @param civilianService manages active civilian NPCs and provides the factory
	 * @param markerConfig loaded entity_marker.yml config (used to resolve type definitions)
	 */
	// TODO Implement the repository before building
	public CivilianSpawnManager(SpawnConfigProvider config,
	                            IRepository<CivilianSpawner> repository,
	                            CivilianService civilianService,
	                            EntityMarkerConfig markerConfig) {
		super(config, repository);
		this.civilianService = civilianService;
		this.markerConfig    = markerConfig;
	}

	// ── EntitySpawner contract ────────────────────────────────────────────────

	/**
	 * Spawns a civilian of the given type at exactly the given location and registers it with {@link CivilianService}.
	 *
	 * @return the created NPC, or {@code null} if spawning failed
	 */
	@Nullable
	public CivilianNpc spawnCivilian(Location location, String typeId) {
		CivilianTypeConfig typeConfig = markerConfig.types().get(typeId);
		if (typeConfig == null) {
			log.warn("Cannot spawn civilian: unknown type '{}'.", typeId);
			return null;
		}

		CivilianNpc npc = civilianService.getNpcFactory().createCivilian(location, typeConfig, null, null);
		if (npc == null) return null;

		civilianService.register(npc);
		return npc;
	}

	// ── Spawn API ─────────────────────────────────────────────────────────────

	/**
	 * Finds a valid spawn location near {@code origin} using the inherited surface search, then spawns a civilian of
	 * the given type and registers it with {@link CivilianService}.
	 *
	 * @return the created NPC, or {@code null} if no suitable location was found or spawning failed
	 */
	@Nullable
	public CivilianNpc spawnNearLocation(Player originPlayer, String typeId) {
		Location spawnLoc = findSpawnLocation(originPlayer);
		if (spawnLoc == null) return null;
		return spawnCivilian(spawnLoc, typeId);
	}

	@Override
	protected CivilianSpawner createSpawnerPoint(int id, Location location) {
		return new CivilianSpawner(id, location, null);
	}
}
