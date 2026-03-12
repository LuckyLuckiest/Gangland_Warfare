package me.luckyraven.copsncrooks.jail;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JailService {

	private final Map<Integer, Jail> cells;

	public JailService() {
		this.cells = new LinkedHashMap<>();
	}

	@Nullable
	public Jail getJail(int id) {
		return cells.get(id);
	}

	@Nullable
	public Location getJailLocation(int id) {
		Jail jail = cells.get(id);
		return jail == null ? null : jail.getLocation();
	}

	@Nullable
	public Location getJailLocation(UUID playerId) {
		for (Jail jail : cells.values()) {
			if (!jail.getJailedPlayersId().contains(playerId)) continue;

			return jail.getLocation();
		}

		return null;
	}

	public Jail setJailLocation(int id, Location location) {
		Jail jail = cells.get(id);

		if (jail == null) {
			jail = new Jail(id, location);
			cells.put(id, jail);
			return jail;
		}

		jail.setLocation(location);
		return jail;
	}

	public void addJail(Jail jail) {
		cells.put(jail.getId(), jail);
	}

	public void detainPlayer(int jailId, UUID playerId) {
		releasePlayer(playerId);

		Jail jail = cells.get(jailId);
		if (jail == null) {
			throw new IllegalArgumentException("Unknown jail id: " + jailId);
		}

		jail.addPlayer(playerId);
	}

	public void releasePlayer(UUID playerId) {
		for (Jail jail : cells.values()) {
			jail.removePlayer(playerId);
		}
	}

	public List<Jail> getCells() {
		return new ArrayList<>(cells.values());
	}

	public void clear() {
		cells.clear();
	}
}
