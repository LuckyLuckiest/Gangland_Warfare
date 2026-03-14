package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.copsncrooks.police.spawn.CopSpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/**
 * Cop navigates back to the nearest registered spawn station. Once arrived, or after a timeout, the cop despawns - but
 * only when no other player is looking.
 * <p>
 * If the target player is freed before the cop reaches its station (e.g. via admin command), the cop immediately
 * re-engages - returning to {@link CopState#COMBAT} when {@code combatForced} is set, otherwise
 * {@link CopState#PURSUING}.
 */
public class ReturningBehavior implements CopBehavior {

	private static final int MAX_RETURN_TICKS = 600; // 30 seconds at 1 tick/tick, scaled by AI tick rate

	private final CopSpawnManager   spawnManager;
	private final DetainmentService detainmentService;

	private Location selectedStation;

	public ReturningBehavior(CopSpawnManager spawnManager, DetainmentService detainmentService) {
		this.spawnManager      = spawnManager;
		this.detainmentService = detainmentService;
	}

	@Override
	public void tick(CopNpc cop, Player target) {
		// Re-engage if the target has been freed (e.g. admin uncuff command)
		if (target != null && target.isOnline() && !detainmentService.isRestrained(target)) {
			cop.transitionTo(cop.isCombatForced() ? CopState.COMBAT : CopState.PURSUING);
			return;
		}

		cop.setDespawnTicks(cop.getDespawnTicks() + 1);

		// Resolve the nearest station once on entry
		if (selectedStation == null) {
			selectedStation = findNearestStation(cop);
		}

		if (selectedStation != null) {
			LivingEntity entity = cop.getEntity();

			if (entity == null) return;

			double distance = entity.getLocation().distance(selectedStation);

			if (distance <= 3.0) {
				tryDespawn(cop);
				return;
			}

			cop.navigateTo(selectedStation);
		}

		// Timeout - despawn regardless of whether the station was reached
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
	 * Attempts to despawn the cop. Waits if another player is watching, but forces despawn after an extended timeout.
	 */
	private void tryDespawn(CopNpc cop) {
		if (!cop.isValid()) {
			cop.markForRemoval();
			return;
		}

		LivingEntity entity = cop.getEntity();

		if (entity == null) return;

		Location copLocation = entity.getLocation();

		Player excludePlayer = cop.getTargetPlayerId() != null ? Bukkit.getPlayer(cop.getTargetPlayerId()) : null;

		if (spawnManager.isVisibleToOtherPlayers(copLocation, excludePlayer)) {
			if (cop.getDespawnTicks() < MAX_RETURN_TICKS * 2) {
				return;
			}
		}

		cop.markForRemoval();
	}

	/**
	 * Finds the nearest registered spawner location to the cop. Falls back to the cop's original spawn location when no
	 * spawners are registered in the same world.
	 */
	private Location findNearestStation(CopNpc cop) {
		List<Location> stations = spawnManager.getSpawnerLocations();

		if (stations.isEmpty()) {
			return cop.getSpawnLocation();
		}

		LivingEntity entity = cop.getEntity();

		if (entity == null) return cop.getSpawnLocation();

		Location copLoc = entity.getLocation();

		return stations.stream()
				.filter(loc -> loc.getWorld() != null && loc.getWorld().equals(copLoc.getWorld()))
				.min(Comparator.comparingDouble(loc -> loc.distanceSquared(copLoc)))
				.orElse(cop.getSpawnLocation());
	}
}