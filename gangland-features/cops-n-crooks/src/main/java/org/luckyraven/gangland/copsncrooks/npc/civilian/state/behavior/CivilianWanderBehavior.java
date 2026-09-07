package org.luckyraven.gangland.copsncrooks.npc.civilian.state.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianGroup;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianState;
import org.luckyraven.gangland.copsncrooks.npc.civilian.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.npc.civilian.state.CivilianBehavior;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Wander behavior: navigates the civilian to random nearby locations.
 * <p>
 * If the NPC belongs to a group and has strayed beyond the group's {@code stayTogetherRange}, it navigates back toward
 * the group center instead. Reverts to {@link CivilianState#IDLE} once the destination is reached or navigation becomes
 * stuck. Periodically picks a fresh destination mid-path so the NPC appears to change its mind. While walking, the NPC
 * looks toward any nearby living entity it notices.
 */
public class CivilianWanderBehavior implements CivilianBehavior {

	private static final int ARRIVAL_CHECK_INTERVAL = 1;
	private static final int MAX_STUCK_REVERTS      = 3;
	private static final int REDIRECT_MIN_TICKS     = 60;
	private static final int REDIRECT_MAX_TICKS     = 120;
	private static final int MIN_LOOK_TICKS         = 10;
	private static final int MAX_LOOK_TICKS         = 20;
	private static final int LOOK_RADIUS            = 10;

	private final double softLeashRadiusSq;

	private int stuckCount;
	private int arrivalCheckTicks;
	private int redirectCountdown;
	private int lookCountdown;

	public CivilianWanderBehavior(double softLeashRadius) {
		this.softLeashRadiusSq = softLeashRadius * softLeashRadius;
	}

	@Override
	public void onEnter(CivilianNpc npc) {
		stuckCount        = 0;
		arrivalCheckTicks = 0;
		redirectCountdown = ThreadLocalRandom.current().nextInt(REDIRECT_MIN_TICKS, REDIRECT_MAX_TICKS + 1);
		lookCountdown     = ThreadLocalRandom.current().nextInt(MIN_LOOK_TICKS, MAX_LOOK_TICKS + 1);
		navigateToDestination(npc);
	}

	@Override
	public void tick(CivilianNpc npc) {
		// Look at nearby entities while walking
		lookCountdown--;
		if (lookCountdown <= 0) {
			lookCountdown = ThreadLocalRandom.current().nextInt(MIN_LOOK_TICKS, MAX_LOOK_TICKS + 1);
			CivilianLookController.lookAtNearbyEntityIfPresent(npc, LOOK_RADIUS);
		}

		arrivalCheckTicks++;
		if (arrivalCheckTicks < ARRIVAL_CHECK_INTERVAL) return;
		arrivalCheckTicks = 0;

		if (!npc.getNpc().getNavigator().isNavigating()) {
			navigateToDestination(npc);
			return;
		}

		// Periodically reconsider direction even while navigating
		redirectCountdown--;
		if (redirectCountdown <= 0) {
			redirectCountdown = ThreadLocalRandom.current().nextInt(REDIRECT_MIN_TICKS, REDIRECT_MAX_TICKS + 1);
			navigateToDestination(npc);
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

		if (group != null && group.isMemberStraying(npc)) {
			Location center = group.getGroupCenter();
			if (center != null) {
				npc.navigateTo(center);
				return;
			}
		}

		// Soft leash — bias wander back toward the spawner when the NPC has drifted too far.
		// Hard leash (CivilianService) will despawn anyone who still escapes.
		Location spawn = npc.getSpawnLocation();
		if (spawn != null) {
			World spawnWorld = spawn.getWorld();
			if (spawnWorld != null && npc.isValid()) {
				Location here  = npc.getEntity().getLocation();
				World    world = here.getWorld();

				if (Objects.requireNonNull(world).equals(spawnWorld) &&
				    here.distanceSquared(spawn) > softLeashRadiusSq) {
					npc.navigateTo(spawn);
					return;
				}
			}
		}

		Location destination = randomNearbyLocation(npc);
		if (destination != null) {
			npc.navigateTo(destination);
		} else {
			npc.transitionTo(CivilianState.IDLE);
		}
	}

	@Nullable
	private Location randomNearbyLocation(CivilianNpc npc) {
		if (!npc.isValid()) return null;

		int range   = npc.getTypeConfig().ai().wanderRange();
		int maxDist = Math.min(8, range);
		int minDist = Math.min(3, maxDist);

		return npc.findForwardWanderDestination(minDist, maxDist);
	}
}
