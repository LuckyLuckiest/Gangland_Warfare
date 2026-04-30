package org.luckyraven.gangland.copsncrooks.npc.civilian.state.behavior;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.luckyraven.gangland.copsncrooks.npc.civilian.npc.CivilianNpc;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for ambient NPC look behaviour — scans for nearby entities and rotates the NPC to face them, or rotates to a
 * random direction when nothing is nearby.
 * <p>
 * All rotation is applied via {@code entity.teleport(rotated)} (the same pattern used by
 * {@code AbstractNpc#faceTarget}). Methods are stateless and safe to call from any behavior tick.
 */
public final class CivilianLookController {

	private CivilianLookController() {
	}

	/**
	 * Scans for the nearest living entity within {@code radius} blocks. Prefers players. Faces the NPC toward the
	 * nearest entity found, or rotates to a random direction if none is present.
	 */
	public static void lookAtNearbyEntityOrRandom(CivilianNpc npc, int radius) {
		LivingEntity entity = npc.getEntity();
		if (entity == null) return;

		Entity target = findNearestEntity(entity, radius);
		if (target != null) {
			faceLocation(entity, target.getLocation().add(0, 1, 0));
		} else {
			lookRandom(entity);
		}
	}

	/**
	 * Scans for the nearest living entity within {@code radius} blocks and faces the NPC toward it. Does nothing if
	 * none is found — suitable while walking so the head only turns when something is actually nearby.
	 */
	public static void lookAtNearbyEntityIfPresent(CivilianNpc npc, int radius) {
		LivingEntity entity = npc.getEntity();
		if (entity == null) return;

		Entity target = findNearestEntity(entity, radius);
		if (target != null) {
			faceLocation(entity, target.getLocation().add(0, 1, 0));
		}
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	private static Entity findNearestEntity(LivingEntity self, int radius) {
		Collection<Entity> nearby = self.getNearbyEntities(radius, radius, radius);

		Player   nearestPlayer    = null;
		Entity   nearestOther     = null;
		double   nearestPlayerDsq = Double.MAX_VALUE;
		double   nearestOtherDsq  = Double.MAX_VALUE;
		Location selfLocation     = self.getLocation();

		for (Entity candidate : nearby) {
			if (!(candidate instanceof LivingEntity)) continue;
			if (candidate.getEntityId() == self.getEntityId()) continue;

			double dsq = candidate.getLocation().distanceSquared(selfLocation);

			if (candidate instanceof Player player) {
				if (dsq < nearestPlayerDsq) {
					nearestPlayerDsq = dsq;
					nearestPlayer    = player;
				}
			} else {
				if (dsq < nearestOtherDsq) {
					nearestOtherDsq = dsq;
					nearestOther    = candidate;
				}
			}
		}

		return nearestPlayer != null ? nearestPlayer : nearestOther;
	}

	private static void faceLocation(LivingEntity entity, Location target) {
		Location origin    = entity.getLocation().add(0, 1.6, 0);
		Vector   direction = target.toVector().subtract(origin.toVector());
		if (direction.lengthSquared() < 0.0001) return;

		Location rotated = entity.getLocation().setDirection(direction.normalize());
		entity.teleport(rotated);
	}

	private static void lookRandom(LivingEntity entity) {
		float  randomYaw = ThreadLocalRandom.current().nextFloat() * 360f - 180f;
		double rad       = Math.toRadians(randomYaw);
		Vector direction = new Vector(-Math.sin(rad), 0, Math.cos(rad));

		Location rotated = entity.getLocation().setDirection(direction);
		entity.teleport(rotated);
	}
}
