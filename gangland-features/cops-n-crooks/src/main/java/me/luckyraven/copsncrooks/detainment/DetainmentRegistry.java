package me.luckyraven.copsncrooks.detainment;

import lombok.Getter;
import me.luckyraven.copsncrooks.jail.Jail;
import me.luckyraven.copsncrooks.jail.JailRegistry;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DetainmentRegistry implements BeanLifecycle {

	private final IRepository<DetainedPlayer> detainmentRepository;
	private final JailRegistry                jailRegistry;

	@Getter
	private final Map<UUID, DetainedPlayer> detainedPlayers;

	public DetainmentRegistry(IRepository<DetainedPlayer> detainmentRepository, JailRegistry jailRegistry) {
		this.detainmentRepository = detainmentRepository;
		this.jailRegistry         = jailRegistry;
		this.detainedPlayers      = new ConcurrentHashMap<>();

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

		Integer resolvedJailId = resolveJailId(playerId);
		if (detainedPlayer == null) {
			Integer jailId = state == DetainmentState.JAILED ? resolvedJailId : null;

			detainedPlayer = new DetainedPlayer(playerId, jailId, state);

			detainedPlayers.put(playerId, detainedPlayer);
			detainmentRepository.save(detainedPlayer);
			return;
		}

		if (state == DetainmentState.JAILED && detainedPlayer.getJailId() == null) {
			detainedPlayer.setJailId(resolvedJailId);
		}

		detainedPlayer.setState(state);
		detainmentRepository.save(detainedPlayer);
	}

	@Nullable
	public Jail findEmptyJail() {
		for (Jail jail : jailRegistry.getCells()) {
			if (jail.getJailedPlayersId().size() >= jail.getMaxCapacity()) continue;

			return jail;
		}
		return null;
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
		loadDetainedPlayers();
	}

	@Override
	public void onClear() {
		detainedPlayers.clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		loadDetainedPlayers();
	}

	/**
	 * Resolves the jail ID for a player transitioning to the JAILED state. First checks if {@link JailRegistry} has
	 * already assigned them to a specific jail (via {@link DetainmentService#jail}), then falls back to the first
	 * available jail.
	 */
	@Nullable
	private Integer resolveJailId(UUID playerId) {
		Integer assigned = jailRegistry.getJailIdForPlayer(playerId);
		if (assigned != null) return assigned;

		Jail emptyJail = findEmptyJail();
		return emptyJail == null ? null : emptyJail.getId();
	}

	private void loadDetainedPlayers() {
		for (DetainedPlayer detainedPlayer : detainmentRepository.loadAll()) {
			detainedPlayers.put(detainedPlayer.getPlayerId(), detainedPlayer);
		}
	}
}