package me.luckyraven.feature.weapon.reload;

import lombok.Setter;

public enum ReloadType {

	INSTANT,
	ONE,
	NUM;

	private @Setter int amount;

	public static ReloadType getType(String type) {
		return switch (type.toLowerCase()) {
			default -> INSTANT;
			case "one" -> ONE;
			case "num" -> NUM;
		};
	}

}
