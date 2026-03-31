package me.luckyraven.copsncrooks.civilian.state.behavior;

import me.luckyraven.copsncrooks.civilian.CivilianGroup;
import me.luckyraven.copsncrooks.civilian.CivilianNpc;
import me.luckyraven.copsncrooks.civilian.CivilianState;
import me.luckyraven.copsncrooks.civilian.state.CivilianBehavior;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Wander behavior: navigates the civilian to random nearby locations.
 * <p>
 * If the NPC belongs to a group and has strayed beyond the group's {@code stayTogetherRange}, it navigates back toward
 * the group center instead. Reverts to {@link CivilianState#IDLE} once the destination is reached or navigation becomes
 * stuck.
 */
public class CivilianWanderBehavior implements CivilianBehavior {

	private static final int ARRIVAL_CHECK_INTERVAL = 20;
	private static final int MAX_STUCK_REVERTS      = 3;

	private int stuckCount;
	private int arrivalCheckTicks;

	@Override
	public void onEnter(CivilianNpc npc) {
		stuckCount        = 0;
		arrivalCheckTicks = 0;
		navigateToDestination(npc);
	}

	@Override
	public void tick(CivilianNpc npc) {
		arrivalCheckTicks++;

		if (arrivalCheckTicks < ARRIVAL_CHECK_INTERVAL) return;
		arrivalCheckTicks = 0;

		if (!npc.getNpc().getNavigator().isNavigating()) {
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		if (npc.isNavigationStuck()) {
			stuckCount++;
			if (stuckCount >= MAX_STUCK_REVERTS) {
				npc.stopNavigation();
				npc.transitionTo(CivilianState.IDLE);
				return;
			}
			navigateToDestination(npc);
		}
	}

	@Override
	public void onExit(CivilianNpc npc) {
		npc.stopNavigation();
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void navigateToDestination(CivilianNpc npc) {
		CivilianGroup group = npc.getGroup();

		// If straying from group, head back to group center
		if (group != null && group.isMemberStraying(npc)) {
			Location center = group.getGroupCenter();
			if (center != null) {
				npc.navigateTo(center);
				return;
			}
		}

		Location destination = randomNearbyLocation(npc);
		if (destination != null) {
			npc.navigateTo(destination);
		}
	}

	@Nullable
	private Location randomNearbyLocation(CivilianNpc npc) {
		if (!npc.isValid()) return null;

		Location base  = npc.getEntity().getLocation();
		World    world = base.getWorld();
		if (world == null) return null;

		int range = npc.getTypeConfig().ai().wanderRange();

		ThreadLocalRandom rng     = ThreadLocalRandom.current();
		double            offsetX = rng.nextDouble(-range, range + 1);
		double            offsetZ = rng.nextDouble(-range, range + 1);

		Location candidate = base.clone().add(offsetX, 0, offsetZ);

		// Snap to the highest non-air block at that X/Z
		int highestY = world.getHighestBlockYAt(candidate);
		candidate.setY(highestY);

		return candidate;
	}
}
