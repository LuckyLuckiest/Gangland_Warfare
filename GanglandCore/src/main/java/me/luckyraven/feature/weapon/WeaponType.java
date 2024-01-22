package me.luckyraven.feature.weapon;

public enum WeaponType {

	GUN,
	MELEE,
	PROJECTILE,
	INCENDIARY,
	BIOLOGICAL,
	OTHER;

	public static WeaponType getType(String type) {
		return switch (type.toLowerCase()) {
			case "gun" -> GUN;
			case "melee" -> MELEE;
			case "projectile", "proj" -> PROJECTILE;
			case "incendiary", "fire" -> INCENDIARY;
			case "biological", "biology", "bio" -> BIOLOGICAL;
			default -> OTHER;
		};
	}

}
