package me.luckyraven.copsncrooks.detainment;

import lombok.Getter;
import me.luckyraven.persistence.repository.IRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DetainmentManager {

	private final IRepository<DetainedPlayer> detainmentRepository;

	@Getter
	private final Map<UUID, DetainedPlayer> detainedPlayers;

	public DetainmentManager(IRepository<DetainedPlayer> detainmentRepository) {
		this.detainmentRepository = detainmentRepository;
		this.detainedPlayers      = new ConcurrentHashMap<>();

		initialize();

		// Set data supplier so repository can save current state
		detainmentRepository.setDataSupplier(detainedPlayers::values);
	}

	public DetainmentState getState(UUID playerId) {
		DetainedPlayer detainedPlayer = detainedPlayers.get(playerId);
		return detainedPlayer == null ? DetainmentState.NORMAL : detainedPlayer.getState();
	}

	public Map<UUID, DetainmentState> getStates() {
		Map<UUID, DetainmentState> states = new HashMap<>();

		for (DetainedPlayer detainedPlayer : detainedPlayers.values()) {
			states.put(detainedPlayer.getPlayerId(), detainedPlayer.getState());
		}

		return states;
	}

	public void setState(UUID playerId, DetainmentState state) {
		if (state == DetainmentState.NORMAL) {
			DetainedPlayer removed = detainedPlayers.remove(playerId);
			if (removed != null) {
				detainmentRepository.delete(removed);
			}
			return;
		}

		DetainedPlayer detainedPlayer = detainedPlayers.get(playerId);

		if (detainedPlayer == null) {
			detainedPlayer = new DetainedPlayer(playerId, 0/* get an empty jail */, state);
			detainedPlayers.put(playerId, detainedPlayer);
			detainmentRepository.save(detainedPlayer);
		} else {
			detainedPlayer.setState(state);
			detainmentRepository.save(detainedPlayer);
		}
	}

	public boolean isDetained(UUID playerId) {
		return getState(playerId) != DetainmentState.NORMAL;
	}

	/**
	 * Save all detainment data to database
	 */
	public void saveAll() {
		detainmentRepository.saveAllFromMemory();
	}

	/**
	 * Reloads detainment data from the database, clearing the current in-memory state first.
	 */
	public void reload() {
		detainedPlayers.clear();
		initialize();
	}

	private void initialize() {
		for (DetainedPlayer detainedPlayer : detainmentRepository.loadAll()) {
			detainedPlayers.put(detainedPlayer.getPlayerId(), detainedPlayer);
		}
	}
}