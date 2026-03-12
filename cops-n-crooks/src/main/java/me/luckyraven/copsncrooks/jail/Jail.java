package me.luckyraven.copsncrooks.jail;

import lombok.Getter;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Jail {

	private final int        id;
	private final List<UUID> jailedPlayersId;

	private Location location;

	public Jail(int id, Location location) {
		this.id              = id;
		this.location        = location.clone();
		this.jailedPlayersId = new ArrayList<>();
	}

	public Location getLocation() {
		return location.clone();
	}

	public void setLocation(Location location) {
		this.location = location.clone();
	}

	public void addPlayer(UUID playerId) {
		if (jailedPlayersId.contains(playerId)) return;
		jailedPlayersId.add(playerId);
	}

	public void removePlayer(UUID playerId) {
		jailedPlayersId.remove(playerId);
	}

	public List<UUID> getJailedPlayersId() {
		return new ArrayList<>(jailedPlayersId);
	}

	public boolean isJailOccupied() {
		return !jailedPlayersId.isEmpty();
	}
}
