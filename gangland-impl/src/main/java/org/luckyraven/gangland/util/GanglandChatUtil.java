package org.luckyraven.gangland.util;

import org.apache.logging.log4j.Logger;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;

public final class GanglandChatUtil extends ChatUtil {

	private GanglandChatUtil() {
		super();
	}

	public static String color(final String message) {
		return color(message, new Replacement("%money_symbol%", Settings.getMoneySymbol()));
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
		ChatUtil.sendToOperators(permission, commandMessage(message));
	}

	public static void sendToOperators(String permission, String message, Logger logger, boolean sendAsWarn) {
		ChatUtil.sendToOperators(permission, commandMessage(message), logger, sendAsWarn);
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