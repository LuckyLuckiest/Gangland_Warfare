package org.luckyraven.gangland.weapon.projectile;

/**
 * Catalogue of weapon projectile types parsed from configuration. The unified {@code WeaponRaytracer} dispatches based
 * on this enum: {@link #BULLET} and {@link #SPREAD} run synchronously through {@code fireInstant}, while
 * {@link #ROCKET} and {@link #FLARE} spawn a cosmetic visual entity driven by a {@code SteppedProjectileTask}.
 */
public enum ProjectileType {

	BULLET,
	SPREAD,
	FLARE,
	ROCKET;

	public static ProjectileType getType(String type) {
		return switch (type.toLowerCase()) {
			case "flare" -> FLARE;
			case "spread" -> SPREAD;
			case "rocket" -> ROCKET;
			default -> BULLET;
		};
	}

}
