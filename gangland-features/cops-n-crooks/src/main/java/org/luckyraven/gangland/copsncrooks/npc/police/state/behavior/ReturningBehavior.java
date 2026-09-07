package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

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

	private final CopSpawnManager   spawnManager;
	private final DetainmentService detainmentService;
	private final int               maxReturnTicks;
	private final double            stationArrivalDistance;

	private Location selectedStation;

	public ReturningBehavior(CopSpawnManager spawnManager, DetainmentService detainmentService, int maxReturnTicks,
	                         double stationArrivalDistance) {
		this.spawnManager           = spawnManager;
		this.detainmentService      = detainmentService;
		this.maxReturnTicks         = maxReturnTicks;
		this.stationArrivalDistance = stationArrivalDistance;
	}

	@Override
	public void tick(CopNpc cop) {
		Player target = cop.getTargetPlayerId() != null ? Bukkit.getPlayer(cop.getTargetPlayerId()) : null;
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

			World world = entity.getWorld();

			if (!world.equals(selectedStation.getWorld())) {
				tryDespawn(cop);
				return;
			}

			double distance = Math.sqrt(horizontalDistanceSquared(entity.getLocation(), selectedStation));

			if (distance <= stationArrivalDistance) {
				tryDespawn(cop);
				return;
			}

			cop.navigateTo(selectedStation);
		}

		// Timeout - despawn regardless of whether the station was reached
		if (cop.getDespawnTicks() >= maxReturnTicks) {
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
	 * Returns the squared horizontal (XZ-plane) distance between two locations, ignoring the Y axis. Useful for arrival
	 * checks where minor vertical offsets (carpet, slabs) should not affect distance comparisons.
	 */
	private double horizontalDistanceSquared(Location a, Location b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/**
	 * Despawns the cop unconditionally. Pursuit has either reached the station, timed out, or the pursuit leash has
	 * given up — spawn-cap recovery takes priority over keeping the cop around for bystanders.
	 */
	private void tryDespawn(CopNpc cop) {
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
				.min(Comparator.comparingDouble(loc -> horizontalDistanceSquared(loc, copLoc)))
				.orElse(cop.getSpawnLocation());
	}
}