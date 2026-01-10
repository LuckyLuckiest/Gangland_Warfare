package me.luckyraven.compatibility.pathfinding;

import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefaultPathfindingHandler implements PathfindingHandler {

	// Track navigation targets manually
	private final Map<UUID, Location> navigationTargets = new HashMap<>();

	@Override
	public boolean navigateTo(Mob mob, Location target, double speed) {
		if (mob == null || target == null || mob.isDead()) return false;
		if (target.getWorld() == null || !target.getWorld().equals(mob.getWorld())) return false;

		UUID entityId = mob.getUniqueId();
		navigationTargets.put(entityId, target.clone());

		// Make sure AI is enabled
		mob.setAI(true);
		mob.setAware(true);

		// Use LivingEntity lookAt to face target
		Location mobLoc    = mob.getLocation();
		Vector   direction = target.toVector().subtract(mobLoc.toVector()).normalize();

		if (direction.lengthSquared() > 0) {
			mobLoc.setDirection(direction);
			mob.setRotation(mobLoc.getYaw(), mobLoc.getPitch());
		}

		// For simple navigation: set velocity toward target
		// This is a basic fallback - actual pathfinding requires NMS
		double distance = mobLoc.distance(target);

		if (distance > 1.0) {
			Vector velocity = direction.multiply(Math.min(speed * 0.2, 0.4));
			velocity.setY(mob.getVelocity().getY()); // Preserve Y velocity

			// Only set if on ground to avoid flying
			if (mob.isOnGround()) {
				mob.setVelocity(velocity);
			}
		}

		return true;
	}

	@Override
	public void stopNavigation(Mob mob) {
		if (mob == null || mob.isDead()) return;

		navigationTargets.remove(mob.getUniqueId());

		// Stop movement
		if (mob.isOnGround()) {
			Vector vel = mob.getVelocity();
			mob.setVelocity(new Vector(0, vel.getY(), 0));
		}
	}

	@Override
	public boolean isNavigating(Mob mob) {
		if (mob == null || mob.isDead()) return false;
		return navigationTargets.containsKey(mob.getUniqueId());
	}

	@Override
	public Location getTargetLocation(Mob mob) {
		if (mob == null || mob.isDead()) return null;
		Location target = navigationTargets.get(mob.getUniqueId());
		return target != null ? target.clone() : null;
	}

	@Override
	public void clearAIGoals(Mob mob) {
		if (mob == null || mob.isDead()) return;
		// Cannot clear goals without NMS - do nothing
		// Awareness control is the best we can do
	}

	@Override
	public void setAIEnabled(Mob mob, boolean enabled) {
		if (mob == null || mob.isDead()) return;
		mob.setAware(enabled);
		mob.setAI(enabled);
	}

	@Override
	public void cleanup(UUID entityId) {
		navigationTargets.remove(entityId);
	}
}
