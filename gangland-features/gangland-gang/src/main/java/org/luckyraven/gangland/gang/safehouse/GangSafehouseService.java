package org.luckyraven.gangland.gang.safehouse;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** A gang's safehouses. Seam: the body belongs to lane W5-H; until then there are none. */
public class GangSafehouseService {

	public int count(int gangId) {
		return 0;
	}

	public Optional<Safehouse> find(int gangId, String name) {
		return Optional.empty();
	}

	public List<Safehouse> list(int gangId) {
		return List.of();
	}

	public void remove(int gangId, String name) {
		// W5-H
	}

	/** @return {@code true} when the warm-up started; refusal lines are sent inside. */
	public boolean teleport(Player player, Safehouse safehouse) {
		return false;
	}
}
