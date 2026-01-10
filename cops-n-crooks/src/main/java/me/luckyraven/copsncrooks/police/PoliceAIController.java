package me.luckyraven.copsncrooks.police;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public class PoliceAIController {

	public void tick(PoliceUnit unit, int tickCount) {
		if (!unit.isValid()) {
			unit.setState(PoliceAIState.DESPAWNING);
			return;
		}

		Player target = Bukkit.getPlayer(unit.getTargetPlayerId());
		if (target == null || !target.isOnline()) {
			unit.setState(PoliceAIState.DESPAWNING);
			return;
		}

		unit.decrementAttackCooldown();

		switch (unit.getState()) {
			case IDLE -> handleIdle(unit, target);
			case ALERTED -> handleAlerted(unit, target);
			case CHASING -> handleChasing(unit, target, tickCount);
			case SEARCHING -> handleSearching(unit, target, tickCount);
			case COMBAT -> handleCombat(unit, target, tickCount);
			case DESPAWNING -> { } // Handled by manager
		}
	}

	private void handleIdle(PoliceUnit unit, Player target) {
		double distance = unit.distanceTo(target);

		if (distance <= PoliceConfig.ALERT_RANGE) {
			unit.setState(PoliceAIState.ALERTED);
			unit.setLastKnownPlayerLocation(target.getLocation());
		}
	}

	private void handleAlerted(PoliceUnit unit, Player target) {
		// Brief alert phase then chase
		if (unit.hasLineOfSight(target)) {
			unit.setLastKnownPlayerLocation(target.getLocation());
			unit.setState(PoliceAIState.CHASING);
		} else {
			unit.setState(PoliceAIState.SEARCHING);
		}
	}

	private void handleChasing(PoliceUnit unit, Player target, int tickCount) {
		double distance = unit.distanceTo(target);

		// Check combat range
		if (distance <= PoliceConfig.COMBAT_RANGE) {
			unit.setState(PoliceAIState.COMBAT);
			return;
		}

		// Check despawn distance
		if (distance > PoliceConfig.DESPAWN_DISTANCE) {
			unit.setState(PoliceAIState.DESPAWNING);
			return;
		}

		// Update LOS tracking
		if (unit.hasLineOfSight(target)) {
			unit.setLastKnownPlayerLocation(target.getLocation());
			unit.resetLosLostTicks();
		} else {
			unit.incrementLosLostTicks();

			if (unit.getLosLostTicks() >= PoliceConfig.LOS_TIMEOUT_TICKS) {
				unit.setState(PoliceAIState.SEARCHING);
				unit.resetLosLostTicks();
				return;
			}
		}

		// Pathfind (throttled)
		if (tickCount % PoliceConfig.PATHFIND_TICK_RATE == 0) {
			unit.pathfindTo(unit.getLastKnownPlayerLocation());
		}
	}

	private void handleSearching(PoliceUnit unit, Player target, int tickCount) {
		unit.incrementSearchTicks();

		// Check if player found
		if (unit.hasLineOfSight(target) && unit.distanceTo(target) <= PoliceConfig.CHASE_RANGE) {
			unit.setState(PoliceAIState.CHASING);
			unit.resetSearchTicks();
			return;
		}

		// Search timeout
		if (unit.getSearchTicks() >= PoliceConfig.SEARCH_DURATION_TICKS) {
			unit.setState(PoliceAIState.DESPAWNING);
			return;
		}

		// Random search movement (throttled)
		if (tickCount % (PoliceConfig.PATHFIND_TICK_RATE * 2) == 0) {
			Location searchLoc = getRandomSearchLocation(unit.getLastKnownPlayerLocation());
			unit.pathfindTo(searchLoc);
		}
	}

	private void handleCombat(PoliceUnit unit, Player target, int tickCount) {
		double distance = unit.distanceTo(target);

		// Exit combat if too far
		if (distance > PoliceConfig.COMBAT_RANGE * 2) {
			unit.setState(PoliceAIState.CHASING);
			return;
		}

		// Update position tracking
		if (unit.hasLineOfSight(target)) {
			unit.setLastKnownPlayerLocation(target.getLocation());
		}

		// Attack
		if (distance <= PoliceConfig.COMBAT_RANGE && unit.canAttack()) {
			unit.attack(target);
		}

		// Keep pathfinding toward player in combat
		if (tickCount % PoliceConfig.PATHFIND_TICK_RATE == 0) {
			unit.pathfindTo(target.getLocation());
		}
	}

	private Location getRandomSearchLocation(Location center) {
		ThreadLocalRandom random  = ThreadLocalRandom.current();
		double            offsetX = random.nextDouble(-PoliceConfig.SEARCH_RADIUS, PoliceConfig.SEARCH_RADIUS);
		double            offsetZ = random.nextDouble(-PoliceConfig.SEARCH_RADIUS, PoliceConfig.SEARCH_RADIUS);

		Location searchLoc = center.clone().add(offsetX, 0, offsetZ);

		// Find ground level
		searchLoc.setY(searchLoc.getWorld().getHighestBlockYAt(searchLoc) + 1);

		return searchLoc;
	}
}
