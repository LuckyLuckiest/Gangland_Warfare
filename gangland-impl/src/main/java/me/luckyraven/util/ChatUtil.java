package me.luckyraven.util;

import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Bukkit;

public final class ChatUtil extends me.luckyraven.util.utilities.ChatUtil {

	private ChatUtil() {
		super();
	}

	public static String replaceColorCodes(String message, String replaceWith) {
		return message.replace('§', '&').replaceAll("&[0-9a-fA-Fk-oK-OrR]", replaceWith);
	}

	public static String color(final String message) {
		return color(message, new Replacement("%money_symbol%", SettingAddon.getMoneySymbol()));
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

	public static void sendToOperators(String permission, String message) {
		Bukkit.getServer()
			  .getOnlinePlayers()
				.stream()
				.filter(player -> permission == null || permission.isEmpty() || player.hasPermission(permission))
				.forEach(player -> player.sendMessage(ChatUtil.commandMessage(message)));

		Bukkit.getServer().getConsoleSender().sendMessage(ChatUtil.commandMessage(message));
	}

	public static String commandDesign(String command) {
		return color(command.replace("/glw", "&6/glw&7")
							.replace("<", "&5<&7")
							.replace(">", "&5>&7")
							.replace(" - ", " &c-&r ")
							.replaceAll("[\\[\\],]", ""));
	}

	public static String setArguments(String arguments, String command) {
		return color(arguments + commandDesign(command));
	}

}