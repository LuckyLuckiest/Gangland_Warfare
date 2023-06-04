package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.luckyraven.util.ChatUtil.color;

public final class CommandManager implements CommandExecutor {

	private @Getter
	static final Map<String, CommandHandler> commands = new HashMap<>();

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
	                         @NotNull String[] args) {
		try {
			if (!sender.hasPermission("gangland.command.main")) {
//				sender.sendMessage(MessagesAddons.NOPERM_CMD);
				return true;
			}

			if (args.length == 0) {
				show(sender);
				return true;
			}

			boolean match = false;

			for (Map.Entry<String, CommandHandler> entry : commands.entrySet())
				if (entry.getKey().equalsIgnoreCase(args[0]) || entry.getValue().getAlias().contains(
						args[0].toLowerCase())) {
					if (Arrays.stream(args).anyMatch("help"::equalsIgnoreCase)) onHelp(entry, sender, args);
					else entry.getValue().runExecute(sender, command, args);
					match = true;
				}

			if (!match) {
				sender.sendMessage();
//				sender.sendMessage(
//						setArguments(MessagesAddons.COMMAND_NOTEXISTS, "/" + label + " " + Arrays.asList(args)));
				return false;
			}
		} catch (Exception exception) {
			Gangland.getInstance().getLogger().warning("Unhandled error (commands): " + exception.getMessage());
			exception.printStackTrace();
		}
		return true;
	}

	private void onHelp(Map.Entry<String, CommandHandler> entry, CommandSender sender, String[] args) {
		if (entry.getValue().getHelpInfo().size() == 0) return;
		try {
			int page = 1;
			if (args.length > 2) try {
				for (int i = 0; i < args.length; i++)
					if (args[i].equalsIgnoreCase("help")) {
						page = Integer.parseInt(args[i + 1]);
						break;
					}
			} catch (NumberFormatException ignored) {
			}
			entry.getValue().help(sender, page);
		} catch (IllegalArgumentException exception) {
			Gangland.getInstance().getLogger().warning("Unhandled error (help): " + exception.getMessage());
			exception.printStackTrace();
		}
	}

	public static void addCommand(CommandHandler sub) {
		commands.put(sub.getLabel(), sub);
	}

	public void show(CommandSender cs) {
		PluginDescriptionFile pdf = Gangland.getInstance().getDescription();
		cs.sendMessage("");
		cs.sendMessage(color("&8--&6=&7&oGangland Warfare&6=&8--"));

		List<String>  authors   = pdf.getAuthors();
		StringBuilder authorStr = new StringBuilder();
		for (int i = 0; i < authors.size(); i++) {
			authorStr.append(authors.get(i));
			if (i < authors.size() - 1) authorStr.append(", ");
		}

		cs.sendMessage(color("&7Author" + ChatUtil.plural(authors.size()) + "&8: &b" + authorStr));
		cs.sendMessage(color("&7Version&8: &b" + pdf.getVersion()));
		cs.sendMessage(color("&7Type &6/glw help &7to start."));
		cs.sendMessage("");
	}

	public static String commandDesign(String command) {
		return color(command.replace("/glw", "&6/glw&7")
		                    .replace("<", "&5<&7")
		                    .replace(">", "&5>&7")
		                    .replace(" - ", " &c-&7 ")
		                    .replaceAll("[\\[\\],]", ""));
	}

//		public static String setArguments(String arguments, CommandInformation commandInformationType) {
//		return cc(arguments + design(commandInformationType.getUsage()));
//	}

	public static String setArguments(String arguments, String command) {
		return color(arguments + commandDesign(command));
	}

}
