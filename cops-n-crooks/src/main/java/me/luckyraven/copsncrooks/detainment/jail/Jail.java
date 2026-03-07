package me.luckyraven.copsncrooks.detainment.jail;

import lombok.Getter;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class Jail {

	private final int        id;
	private final Location   location;
	private final List<UUID> jailedPlayersId;

	public Jail(int id, Location location) {
		this.id              = id;
		this.location        = location;
		this.jailedPlayersId = new ArrayList<>();
	}

	public void addPlayer(UUID playerId) {
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
