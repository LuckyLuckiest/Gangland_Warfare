package me.luckyraven.util;

import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.SettingAddon;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;

public final class ChatUtil extends me.luckyraven.util.utilities.ChatUtil {

	private ChatUtil() {
		super();
	}

	public static String color(final String message) {
		return color(message, new Replacement("%money_symbol%", SettingAddon.getMoneySymbol()));
	}

	public static String prefixMessage(String message) {
		return color(Messages.PREFIX + message);
	}

	public static String commandMessage(String message) {
		return color(Messages.COMMAND_PREFIX + message);
	}

	public static String errorMessage(String message) {
		return color(Messages.ERROR_PREFIX + message);
	}

	public static String informationMessage(String message) {
		return color(Messages.INFORMATION_PREFIX + message);
	}

	public static void sendToOperators(String permission, String message) {
		sendToOperators(permission, message, null, false);
	}

	public static void sendToOperators(String permission, String message, Logger logger, boolean sendAsWarn) {
		Bukkit.getServer()
			  .getOnlinePlayers()
				.stream()
				.filter(player -> permission == null || permission.isEmpty() || player.hasPermission(permission))
				.forEach(player -> player.sendMessage(commandMessage(message)));

		if (sendAsWarn && logger != null) logger.warn(message);
		else Bukkit.getServer().getConsoleSender().sendMessage(commandMessage(message));
	}

	public static String commandDesign(String command) {
		return color(command.replace("/" + Gangland.SHORT_PREFIX, "&6/" + Gangland.SHORT_PREFIX + "&7")
							.replace("<", "&5<&7")
							.replace(">", "&5>&7")
							.replace(" - ", " &c-&r ")
							.replaceAll("[\\[\\],]", ""));
	}

	public static String confirmCommand(String[] args) {
		return color("&cYou need to confirm using &e/" + Gangland.SHORT_PREFIX + " " + String.join(" ", args) +
					 " confirm &cto execute the command.");
	}

	public static String setArguments(String arguments, String command) {
		return color(arguments + commandDesign(command));
	}

}