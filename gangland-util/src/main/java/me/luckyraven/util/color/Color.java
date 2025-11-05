package me.luckyraven.util.color;

import lombok.Getter;

@Getter
public enum Color {

	WHITE("&f", org.bukkit.Color.WHITE),
	LIGHT_GRAY("&7", org.bukkit.Color.SILVER),
	GRAY("&8", org.bukkit.Color.GRAY),
	BLACK("&0", org.bukkit.Color.BLACK),
	BROWN("&6", org.bukkit.Color.OLIVE),
	RED("&4", org.bukkit.Color.RED),
	ORANGE("&6", org.bukkit.Color.ORANGE),
	YELLOW("&e", org.bukkit.Color.YELLOW),
	LIME("&a", org.bukkit.Color.LIME),
	GREEN("&2", org.bukkit.Color.GREEN),
	CYAN("&3", org.bukkit.Color.TEAL),
	LIGHT_BLUE("&b", org.bukkit.Color.AQUA),
	BLUE("&1", org.bukkit.Color.BLUE),
	PURPLE("&5", org.bukkit.Color.PURPLE),
	MAGENTA("&5", org.bukkit.Color.MAROON),
	PINK("&d", org.bukkit.Color.FUCHSIA);

	private final String           colorCode;
	private final org.bukkit.Color bukkitColor;

	Color(String colorCode, org.bukkit.Color bukkitColor) {
		this.colorCode   = colorCode;
		this.bukkitColor = bukkitColor;
	}

}
