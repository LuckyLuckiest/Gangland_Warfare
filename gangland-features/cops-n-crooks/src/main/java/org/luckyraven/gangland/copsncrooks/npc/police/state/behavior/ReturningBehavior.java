package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.StuckSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;

import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Cop navigates back to the nearest registered spawn station. {@link #tryDespawn} marks the cop for removal - and it
 * despawns - on arrival, after {@code Return.Max_Ticks}, or once the way home has been unreachable for
 * {@code Stuck.Recycle_Seconds} (a stranded cop would otherwise stand frozen where its target died).
 * <p>
 * Only a cop sent back because its target was restrained or jailed re-engages: if that target is freed before the cop
 * reaches its station (e.g. via admin command), the cop returns to {@link CopState#COMBAT} when {@code combatForced}
 * is set, otherwise {@link CopState#PURSUING}. Every other return - rotated out of a pursuit, target gone or no longer
 * wanted - is final: re-engaging a free target from here bounced cops between PURSUING and RETURNING forever (D1).
 */
public class ReturningBehavior implements CopBehavior {

	private final CopSpawnManager   spawnManager;
	private final DetainmentService detainmentService;
	private final int               maxReturnTicks;
	private final double            stationArrivalDistance;
	private final StuckSettings     stuck;

	private Location selectedStation;
	/**
	 * Whether this return started because the target was restrained or jailed: only then does a released target pull the
	 * cop back. Set on entry; behaviours are per cop ({@code CopBehaviorFactory#createBehaviors} runs per spawn).
	 */
	private boolean  reengageOnRelease;
	private final LongSupplier  clock;
	/** Wall time the way home first read unreachable on this leg; 0 while it reads reachable. */
	private long     strandedSince;

	public ReturningBehavior(CopSpawnManager spawnManager, DetainmentService detainmentService, int maxReturnTicks,
	                         double stationArrivalDistance, StuckSettings stuck) {
		this(spawnManager, detainmentService, maxReturnTicks, stationArrivalDistance, stuck, System::currentTimeMillis);
	}

	ReturningBehavior(CopSpawnManager spawnManager, DetainmentService detainmentService, int maxReturnTicks,
	                  double stationArrivalDistance, StuckSettings stuck, LongSupplier clock) {
		this.clock                  = clock;
		this.stuck                  = stuck;
		this.spawnManager           = spawnManager;
		this.detainmentService      = detainmentService;
		this.maxReturnTicks         = maxReturnTicks;
		this.stationArrivalDistance = stationArrivalDistance;
	}

	@Override
	public void tick(CopNpc cop) {
		Player target = cop.getTargetPlayerId() != null ? Bukkit.getPlayer(cop.getTargetPlayerId()) : null;
		// Re-engage only a restrained or jailed target that has been freed (e.g. admin uncuff command)
		if (reengageOnRelease && target != null && target.isOnline() && !detainmentService.isRestrained(target)) {
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

			if (strandedOnReturn(cop)) {
				tryDespawn(cop);
				return;
			}
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
		cop.leaveSquad();
		strandedSince = 0;

		Player target = cop.getTargetPlayerId() != null ? Bukkit.getPlayer(cop.getTargetPlayerId()) : null;
		reengageOnRelease = target != null && detainmentService.isRestrained(target);
	}

	@Override
	public void onExit(CopNpc cop) {
		cop.stopNavigation();
		cop.setDespawnTicks(0);
		selectedStation = null;
	}

	/**
	 * Whether the way home has been unreachable for the stuck window, counted from the first return-leg tick that read
	 * it unreachable (the pursuit's own clock may predate entry, or be set aside, so its value is never trusted).
	 */
	private boolean strandedOnReturn(CopNpc cop) {
		if (!stuck.enabled()) return false;
		if (cop.millisUnreachable() <= 0) {
			strandedSince = 0;
			return false;
		}
		long now = clock.getAsLong();
		if (strandedSince == 0) strandedSince = now;
		return now - strandedSince >= stuck.recycleSeconds() * 1000L;
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