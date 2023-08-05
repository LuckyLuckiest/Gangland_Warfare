package me.luckyraven.command;

import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.UnhandledError;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static me.luckyraven.util.ChatUtil.color;

public final class CommandManager implements CommandExecutor {

	private final Map<String, CommandHandler> commands;
	private final Gangland                    gangland;

	public CommandManager(Gangland gangland) {
		this.gangland = gangland;
		this.commands = new HashMap<>();
	}

	public static String commandDesign(String command) {
		return color(command.replace("/glw", "&6/glw&7")
		                    .replace("<", "&5<&7")
		                    .replace(">", "&5>&7")
		                    .replace(" - ", " &c-&7 ")
		                    .replaceAll("[\\[\\],]", ""));
	}

	public static String setArguments(String arguments, String command) {
		return color(arguments + commandDesign(command));
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
	                         @NotNull String[] args) {
		try {
			if (!sender.hasPermission("gangland.command.main")) {
				sender.sendMessage(MessageAddon.COMMAND_NO_PERM.toString());
				return false;
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
					else entry.getValue().runExecute(sender, args);
					match = true;
					break;
				}

			if (!match) {
				sender.sendMessage(setArguments(MessageAddon.ARGUMENTS_DONT_EXIST.toString(),
				                                String.format("/%s %s", label, Arrays.asList(args))));
				return false;
			}
		} catch (Exception exception) {
			gangland.getLogger().warning(UnhandledError.COMMANDS_ERROR + ": " + exception.getMessage());
			exception.printStackTrace();
			return false;
		}
		return true;
	}

	public void addCommand(CommandHandler sub) {
		commands.put(sub.getLabel(), sub);
	}

	public Map<String, CommandHandler> getCommands() {
		return new HashMap<>(commands);
	}

	private void onHelp(Map.Entry<String, CommandHandler> entry, CommandSender sender, String[] args) {
		if (entry.getValue().getHelpInfo().size() == 0) return;

		// Get the page number if it exists
		int page = 1;
		int index = IntStream.range(0, args.length - 1)
		                     .filter(i -> args[i].equalsIgnoreCase("help"))
		                     .findFirst()
		                     .orElse(-1);
		if (index != -1) try {
			page = Integer.parseInt(args[index + 1]);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
		}

		// display the help of the command (if mentioned)
		try {
			entry.getValue().help(sender, page);
		} catch (IllegalArgumentException exception) {
			ChatUtil.errorMessage(exception.getMessage());
		}
	}

	public void show(CommandSender cs) {
		PluginDescriptionFile pdf = gangland.getDescription();
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

}
