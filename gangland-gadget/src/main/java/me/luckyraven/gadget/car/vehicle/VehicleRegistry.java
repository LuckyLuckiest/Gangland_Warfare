package me.luckyraven.gadget.car.vehicle;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all active {@link VehicleSession}s in the server, indexed by both entity UUID and player UUID.
 */
public class VehicleRegistry {

	private final Map<UUID, VehicleSession> byEntity       = new ConcurrentHashMap<>();
	private final Map<UUID, UUID>           playerToEntity = new ConcurrentHashMap<>();

	public void register(VehicleSession session) {
		UUID entityUUID = session.getEntity().getEntityUUID();
		UUID playerUUID = session.getDriverUUID();

		byEntity.put(entityUUID, session);
		playerToEntity.put(playerUUID, entityUUID);
	}

	public void unregister(UUID entityUUID) {
		VehicleSession session = byEntity.remove(entityUUID);
		if (session != null) {
			playerToEntity.remove(session.getDriverUUID());
		}
	}

	@Nullable
	public VehicleSession getByEntity(UUID entityUUID) {
		return byEntity.get(entityUUID);
	}

	@Nullable
	public VehicleSession getByPlayer(UUID playerUUID) {
		UUID entityUUID = playerToEntity.get(playerUUID);
		if (entityUUID == null) {
			return null;
		}
		return byEntity.get(entityUUID);
	}

	public boolean isPlayerDriving(UUID playerUUID) {
		return playerToEntity.containsKey(playerUUID);
	}

	public Collection<VehicleSession> getAllSessions() {
		return byEntity.values();
	}

	public void clear() {
		byEntity.clear();
		playerToEntity.clear();
	}
}
