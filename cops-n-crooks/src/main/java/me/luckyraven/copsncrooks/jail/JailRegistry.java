package me.luckyraven.copsncrooks.jail;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JailRegistry {

	private final Map<Integer, Jail> cells;

	public JailRegistry() {
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

	public Jail setJailLocation(int id, Location location, int maxCapacity) {
		Jail jail = cells.get(id);

		if (jail == null) {
			jail = new Jail(id, location, maxCapacity);
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

	/**
	 * Returns the jail ID the player is currently assigned to, or null if not in any jail.
	 */
	@Nullable
	public Integer getJailIdForPlayer(UUID playerId) {
		for (Jail jail : cells.values()) {
			if (jail.getJailedPlayersId().contains(playerId)) {
				return jail.getId();
			}
		}
		return null;
	}

	/**
	 * Returns the first available jail ID, or null if no jails are registered.
	 */
	@Nullable
	public Integer findAvailableJailId() {
		for (Jail jail : cells.values()) {
			return jail.getId();
		}
		return null;
	}

	public List<Jail> getCells() {
		return new ArrayList<>(cells.values());
	}

	public Jail removeJail(int id) {
		return cells.remove(id);
	}

	public void clear() {
		cells.clear();
	}
}
