package me.luckyraven.weapon.types;

public enum WeaponType {

	GUN,
	MELEE,
	THROWABLE,
	INCENDIARY,
	BIOLOGICAL,
	OTHER;

	public static WeaponType getType(String type) {
		return switch (type.toLowerCase()) {
			case "gun" -> GUN;
			case "melee" -> MELEE;
			case "throwable", "throw", "grenade", "projectile", "proj" -> THROWABLE;
			case "incendiary", "fire" -> INCENDIARY;
			case "biological", "biology", "bio" -> BIOLOGICAL;
			default -> OTHER;
		};
	}

}
