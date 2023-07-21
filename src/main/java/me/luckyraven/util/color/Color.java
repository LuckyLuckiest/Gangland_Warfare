package me.luckyraven.util.color;

import lombok.Getter;

public enum Color {

	WHITE("&f"), LIGHT_GRAY("&7"), GRAY("&8"), BLACK("&0"),

	BROWN("&6"), RED("&4"), ORANGE("&6"), YELLOW("&e"),

	LIME("&a"), GREEN("&2"),

	CYAN("&3"), LIGHT_BLUE("&b"), BLUE("&1"),

	PURPLE("&5"), MAGENTA("&5"), PINK("&d");

	@Getter
	private final String colorCode;

	Color(String colorCode) {
		this.colorCode = colorCode;
	}

}
