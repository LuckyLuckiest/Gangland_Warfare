package me.luckyraven.copsncrooks.detainment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// TODO Needs to be implement to be stored in a database
public class InMemoryDetainmentRepository implements DetainmentRepository {

	private final Map<UUID, DetainmentState> states = new ConcurrentHashMap<>();

	@Override
	public DetainmentState loadState(UUID playerId) {
		return states.getOrDefault(playerId, DetainmentState.NORMAL);
	}

	@Override
	public void saveState(UUID playerId, DetainmentState state) {
		if (state == DetainmentState.NORMAL) {
			states.remove(playerId);
			return;
		}

		states.put(playerId, state);
	}
}
