package me.luckyraven.copsncrooks.npc.civilian.spawn;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.entity.EntitySpawner;
import me.luckyraven.copsncrooks.entity.SpawnConfigProvider;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianTypeConfig;
import me.luckyraven.copsncrooks.npc.civilian.config.CiviliansConfig;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
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

	private final CivilianService civilianService;
	private final CiviliansConfig civiliansConfig;

	/**
	 * @param config spawn location config (min/max distances, clearance, etc.)
	 * @param repository persistent spawner storage; may be {@code null} to skip persistence
	 * @param civilianService manages active civilian NPCs and provides the factory
	 * @param civiliansConfig loaded civilians.yml config (used to resolve type definitions)
	 */
	public CivilianSpawnManager(SpawnConfigProvider config,
	                            IRepository<CivilianSpawner> repository,
	                            CivilianService civilianService,
	                            CiviliansConfig civiliansConfig) {
		super(config, repository);
		this.civilianService = civilianService;
		this.civiliansConfig = civiliansConfig;
	}

	// ── EntitySpawner contract ────────────────────────────────────────────────

	/**
	 * Spawns a civilian of the given type at exactly the given location and registers it with {@link CivilianService}.
	 *
	 * @return the created NPC, or {@code null} if spawning failed
	 */
	@Nullable
	public CivilianNpc spawnCivilian(Location location, String typeId) {
		CivilianTypeConfig typeConfig = civiliansConfig.types().get(typeId);
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

	/**
	 * Creates a type spawner at the given location. When a player enters the activation radius this spawner will spawn
	 * civilians of the given {@code typeId} defined in civilians.yml.
	 */
	public void setTypeSpawnerLocation(Location location, String typeId) {
		ID++;
		CivilianSpawner spawner = new CivilianSpawner(ID, location, typeId, null);
		spawners.put(ID, spawner);
		repository.save(spawner);
	}

	/**
	 * Creates a group spawner at the given location. When a player enters the activation radius this spawner will spawn
	 * a full civilian group defined by {@code groupId} in civilians.yml.
	 */
	public void setGroupSpawnerLocation(Location location, String groupId) {
		ID++;
		CivilianSpawner spawner = new CivilianSpawner(ID, location, null, groupId);
		spawners.put(ID, spawner);
		repository.save(spawner);
	}

	@Override
	protected CivilianSpawner createSpawnerPoint(int id, Location location) {
		return new CivilianSpawner(id, location, null, null);
	}
}
