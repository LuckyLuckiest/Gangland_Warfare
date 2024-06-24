package me.luckyraven.feature.weapon.reload;

public enum ReloadType {

	INSTANT,
	ONE,
	NUM;

	public static ReloadType getType(String type) {
		return switch (type.toLowerCase()) {
			default -> INSTANT;
			case "one" -> ONE;
			case "num" -> NUM;
		};
	}

}
