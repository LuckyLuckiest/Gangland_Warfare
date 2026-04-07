package me.luckyraven.weapon.raytrace;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Shared muzzle-position helper. Computes the world-space "weapon position" — the point from which a weapon's
 * hit-detection ray (or visual projectile) originates — given a shooter and an aim direction.
 * <p>
 * The offset places the muzzle on the right side of the shooter's body, slightly below eye level, matching the visual
 * position of a held gun. Every weapon action (gun, incendiary, biological, melee, throwable) calls this helper so the
 * muzzle is identical across weapon types.
 */
public final class WeaponMuzzle {

	/**
	 * Lateral offset from the shooter's centerline along the right-hand cross product.
	 */
	private static final double RIGHT_OFFSET = 0.3;

	/**
	 * Vertical offset below the eye location to approximate hand height.
	 */
	private static final double DOWN_OFFSET = -0.2;

	private WeaponMuzzle() {
	}

	/**
	 * Computes the muzzle location for a shooter aiming in the given direction.
	 *
	 * @param shooter The entity firing the weapon
	 * @param direction The aim direction (does not need to be normalised)
	 *
	 * @return A new {@link Location} at the muzzle position, in the shooter's world
	 */
	public static Location compute(LivingEntity shooter, Vector direction) {
		Location eye   = shooter.getEyeLocation();
		Vector   right = direction.clone().crossProduct(new Vector(0, 1, 0));

		// Degenerate case: looking straight up or down. Pick an arbitrary stable right vector.
		if (right.lengthSquared() < 0.001) {
			right = new Vector(1, 0, 0);
		}

		right.normalize().multiply(RIGHT_OFFSET);

		return eye.clone().add(right).add(0, DOWN_OFFSET, 0);
	}

}
