package me.luckyraven.copsncrooks.detainment;

import lombok.Getter;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.persistence.repository.IRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DetainmentManager {

	private final IRepository<DetainedPlayer> detainmentRepository;
	private final JailService                 jailService;

	@Getter
	private final Map<UUID, DetainedPlayer> detainedPlayers;

	public DetainmentManager(IRepository<DetainedPlayer> detainmentRepository, JailService jailService) {
		this.detainmentRepository = detainmentRepository;
		this.jailService          = jailService;
		this.detainedPlayers      = new ConcurrentHashMap<>();

		initialize();

		// Set the data supplier so the repository can save the current state
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
			int jailId = state == DetainmentState.JAILED ? resolveJailId(playerId) : -1;
			detainedPlayer = new DetainedPlayer(playerId, jailId, state);
			detainedPlayers.put(playerId, detainedPlayer);
			detainmentRepository.save(detainedPlayer);
		} else {
			if (state == DetainmentState.JAILED && detainedPlayer.getJailId() == -1) {
				detainedPlayer.setJailId(resolveJailId(playerId));
			}
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

	/**
	 * Resolves the jail ID for a player transitioning to the JAILED state. First checks if {@link JailService} has
	 * already assigned them to a specific jail (via
	 * {@link me.luckyraven.copsncrooks.detainment.DetainmentService#jail}), then falls back to the first available
	 * jail.
	 */
	private Integer resolveJailId(UUID playerId) {
		Integer assigned = jailService.getJailIdForPlayer(playerId);
		if (assigned != null) return assigned;

		return jailService.findAvailableJailId();
	}

	private void initialize() {
		for (DetainedPlayer detainedPlayer : detainmentRepository.loadAll()) {
			detainedPlayers.put(detainedPlayer.getPlayerId(), detainedPlayer);
		}
	}
}