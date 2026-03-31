package me.luckyraven.copsncrooks.civilian.state.behavior;

import me.luckyraven.copsncrooks.civilian.CivilianNpc;
import me.luckyraven.copsncrooks.civilian.CivilianState;
import me.luckyraven.copsncrooks.civilian.state.CivilianBehavior;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

/**
 * Flee behavior: the civilian runs away from its last attacker's location. Reverts to {@link CivilianState#IDLE} once
 * it has moved beyond the configured flee range or navigation becomes hopeless.
 */
public class CivilianFleeBehavior implements CivilianBehavior {

	private static final int ARRIVAL_CHECK_INTERVAL = 20;

	private           int      arrivalCheckTicks;
	private @Nullable Location fleeOrigin;

	@Override
	public void onEnter(CivilianNpc npc) {
		arrivalCheckTicks = 0;
		fleeOrigin        = npc.getLastAttackerLocation();
		navigateAwayFrom(npc, fleeOrigin);
	}

	@Override
	public void tick(CivilianNpc npc) {
		arrivalCheckTicks++;

		if (arrivalCheckTicks < ARRIVAL_CHECK_INTERVAL) return;
		arrivalCheckTicks = 0;

		// Revert if no longer navigating (arrived) or navigation is permanently stuck
		if (!npc.getNpc().getNavigator().isNavigating() || npc.isNavigationHopeless()) {
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		// Check if we have fled far enough
		if (fleeOrigin != null && npc.isValid()) {
			Location npcLoc = npc.getEntity().getLocation();
			if (npcLoc.getWorld() != null && npcLoc.getWorld().equals(fleeOrigin.getWorld())) {
				double fleeRange = npc.getTypeConfig().ai().fleeRange();
				if (npcLoc.distance(fleeOrigin) >= fleeRange) {
					npc.stopNavigation();
					npc.transitionTo(CivilianState.IDLE);
				}
			}
		}
	}

	@Override
	public void onExit(CivilianNpc npc) {
		npc.stopNavigation();
		npc.setLastAttackerLocation(null);
		fleeOrigin = null;
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void navigateAwayFrom(CivilianNpc npc, @Nullable Location threat) {
		if (!npc.isValid() || threat == null) return;

		Location npcLoc = npc.getEntity().getLocation();
		World    world  = npcLoc.getWorld();
		if (world == null) return;

		// Direction pointing away from the threat
		double dx = npcLoc.getX() - threat.getX();
		double dz = npcLoc.getZ() - threat.getZ();

		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 0.001) {
			// Attacker is on top of us; flee in an arbitrary direction
			dx     = 1.0;
			dz     = 0.0;
			length = 1.0;
		}

		double range   = npc.getTypeConfig().ai().fleeRange();
		double targetX = npcLoc.getX() + (dx / length) * range;
		double targetZ = npcLoc.getZ() + (dz / length) * range;

		Location destination = new Location(world, targetX, world.getHighestBlockYAt((int) targetX, (int) targetZ),
		                                    targetZ);
		npc.navigateTo(destination);
	}
}
