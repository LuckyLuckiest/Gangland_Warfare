package me.luckyraven.util;

import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public final class ChatUtil {

	public static String replaceColorCodes(String message, String replaceWith) {
		return message.replace("§", "&").replaceAll("&[0-9a-fk-o]|&r", replaceWith);
	}

	public static String color(String message) {
		Objects.requireNonNull(message);
		message = message.replace("%n%", "\n").replace("%money_symbol%", SettingAddon.getMoneySymbol());
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	public static String[] color(String... messages) {
		return Arrays.stream(messages).map(ChatUtil::color).toArray(String[]::new);
	}

	public static void consoleColor(String message) {
		Bukkit.getConsoleSender().sendMessage(color(message));
	}

	public static String plural(int amount) {
		return amount > 1 ? "s" : "";
	}

	public static String capitalize(@NotNull String text) {
		return text.substring(0, 1).toUpperCase() + text.substring(1);
	}

	public static String unicodeCharacters(String position) {
		return String.valueOf(Character.toChars(Integer.parseInt(position, 16)));
	}

	public static void startUpMessage(JavaPlugin plugin) {
		PluginDescriptionFile pdf = plugin.getDescription();
		consoleColor(getPrefix(pdf));
		consoleColor("\t&8Author&7: " + pdf.getAuthors());
		consoleColor("\t&8Version&7: &5(&6" + pdf.getVersion() + "&5)");
		consoleColor(getPrefix(pdf));
	}

	public static String getPrefix(PluginDescriptionFile pdf) {
		return color("&8-[\t&6" + pdf.getName() + "\t&8]-");
	}

	public static String getFilePrefix(PluginDescriptionFile pdf) {
		return color("&8-[\t&6" + pdf.getName() + " &cFiles\t&8]-");
	}

	public static String removeSymbol(String message) {
		return message.replaceAll("[^a-zA-Z\\d\\s]*", "");
	}

	public static String confirmCommand(String[] args) {
		return color(
				"&cYou need to confirm using &e/glw " + String.join(" ", args) + " confirm &cto execute the command.");
	}

	public static String prefixMessage(String message) {
		return color(MessageAddon.PREFIX + message);
	}

	public static String commandMessage(String message) {
		return color(MessageAddon.COMMAND_PREFIX + message);
	}

	public static String errorMessage(String message) {
		return color(MessageAddon.ERROR_PREFIX + message);
	}

	public static String informationMessage(String message) {
		return color(MessageAddon.INFORMATION_PREFIX + message);
	}

}