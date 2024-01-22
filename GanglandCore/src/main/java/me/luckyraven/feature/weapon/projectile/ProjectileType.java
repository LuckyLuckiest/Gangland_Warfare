package me.luckyraven.feature.weapon.projectile;

public enum ProjectileType {

	BULLET,
	SPREAD,
	FLARE,
	ROCKET;

	public static ProjectileType getType(String type) {
		return switch (type.toLowerCase()) {
			default -> BULLET;
			case "flare" -> FLARE;
			case "spread" -> SPREAD;
			case "rocket" -> ROCKET;
		};
	}

}
