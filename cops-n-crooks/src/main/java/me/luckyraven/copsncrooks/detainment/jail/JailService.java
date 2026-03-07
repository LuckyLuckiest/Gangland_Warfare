package me.luckyraven.copsncrooks.detainment.jail;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JailService {

	private final List<Jail> cells;

	public JailService() {
		this.cells = new ArrayList<>();
	}

	public Jail getJailLocation(int id) {
		return cells.get(id);
	}

	@Nullable
	public Location getJailLocation(UUID playerId) {
		for (Jail jail : cells) {
			if (!jail.getJailedPlayersId().contains(playerId)) continue;

			return jail.getLocation();
		}

		return null;
	}

	public void addJail(Jail jail) {
		cells.add(jail);
	}

	public List<Jail> getCells() {
		return new ArrayList<>(cells);
	}
}
