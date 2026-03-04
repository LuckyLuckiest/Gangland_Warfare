package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/**
 * Cop navigates back to the nearest spawn station. Once arrived, or after a timeout, the cop despawns — but only when
 * no other player is looking.
 */
public class ReturningBehavior implements CopBehavior {

	private static final int MAX_RETURN_TICKS = 600; // 30 seconds at 1 tick/tick, scaled by AI tick rate

	private final List<Location>  spawnStations;
	private final CopSpawnManager spawnManager;

	private Location selectedStation;

	public ReturningBehavior(List<Location> spawnStations, CopSpawnManager spawnManager) {
		this.spawnStations = spawnStations;
		this.spawnManager  = spawnManager;
	}

	@Override
	public void tick(CopNpc cop, Player target) {
		cop.setDespawnTicks(cop.getDespawnTicks() + 1);

		// If we haven't picked a station yet, find the nearest one
		if (selectedStation == null) {
			selectedStation = findNearestStation(cop);
		}

		// If there's a station to go to, navigate there
		if (selectedStation != null) {
			double distance = cop.getEntity().getLocation().distance(selectedStation);

			if (distance <= 3.0) {
				// Arrived at station — try to despawn
				tryDespawn(cop);
				return;
			}

			cop.navigateTo(selectedStation);
		}

		// Timeout — try to despawn regardless of reaching station
		if (cop.getDespawnTicks() >= MAX_RETURN_TICKS) {
			tryDespawn(cop);
		}
	}

	@Override
	public void onEnter(CopNpc cop) {
		cop.setDespawnTicks(0);
		selectedStation = null;
	}

	@Override
	public void onExit(CopNpc cop) {
		cop.stopNavigation();
		cop.setDespawnTicks(0);
		selectedStation = null;
	}

	/**
	 * Attempts to despawn the cop. Only despawns if no other player is directly looking. If visible, keeps waiting.
	 */
	private void tryDespawn(CopNpc cop) {
		if (!cop.isValid()) {
			cop.markForRemoval();
			return;
		}

		Location copLocation = cop.getEntity().getLocation();

		// Resolve the cop's target player to exclude from visibility check
		Player excludePlayer = cop.getTargetPlayerId() != null
							   ? Bukkit.getPlayer(cop.getTargetPlayerId())
							   : null;

		// Don't despawn if another player is watching
		if (spawnManager.isVisibleToOtherPlayers(copLocation, excludePlayer)) {
			// Still visible — wait a bit more, but force after extended timeout
			if (cop.getDespawnTicks() < MAX_RETURN_TICKS * 2) {
				return;
			}
		}

		cop.markForRemoval();
	}

	/**
	 * Finds the nearest configured spawn station to the cop's current location.
	 */
	private Location findNearestStation(CopNpc cop) {
		if (spawnStations == null || spawnStations.isEmpty()) {
			// Fall back to the cop's original spawn location
			return cop.getSpawnLocation();
		}

		Location copLoc = cop.getEntity().getLocation();

		return spawnStations.stream()
				.filter(loc -> loc.getWorld() != null && loc.getWorld().equals(copLoc.getWorld()))
				.min(Comparator.comparingDouble(loc -> loc.distanceSquared(copLoc)))
				.orElse(cop.getSpawnLocation());
	}
}