package me.luckyraven.feature.weapon;

public enum SelectiveFire {

	AUTO, BURST, SINGLE;

	public static SelectiveFire getType(String type) {
		return switch (type.toLowerCase()) {
			default -> AUTO;
			case "single" -> SINGLE;
			case "burst" -> BURST;
		};
	}

}
