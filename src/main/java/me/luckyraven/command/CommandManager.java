package me.luckyraven.command;

import me.luckyraven.Gangland;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.UnhandledError;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.luckyraven.util.ChatUtil.color;

public final class CommandManager implements CommandExecutor {

	private final Map<String, CommandHandler> commands;
	private final Gangland                    gangland;

	public CommandManager(Gangland gangland) {
		this.gangland = gangland;
		this.commands = new HashMap<>();
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
	                         @NotNull String[] args) {
		try {
			YamlConfiguration message = gangland.getInitializer().getLanguageLoader().getMessage();
			if (!sender.hasPermission("gangland.command.main")) {
				message.getString("Errors.Permissions.Command");
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
					else entry.getValue().runExecute(sender, args);
					match = true;
				}

			if (!match) {
				sender.sendMessage(setArguments(message.getString("Commands.Syntax.Doesnt_Exist"),
				                                String.format("/%s %s", label, Arrays.asList(args))));
				return false;
			}
		} catch (Exception exception) {
			gangland.getLogger().warning(UnhandledError.COMMANDS_ERROR.getMessage() + ": " + exception.getMessage());
			exception.printStackTrace();
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
			gangland.getLogger().warning(UnhandledError.HELP_ERROR.getMessage() + ": " + exception.getMessage());
			exception.printStackTrace();
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

}
