package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.sub.DownloadPluginCommand;
import me.luckyraven.command.sub.debug.DebugCommand;
import me.luckyraven.command.sub.debug.OptionCommand;
import me.luckyraven.command.sub.debug.ReadNBTCommand;
import me.luckyraven.command.sub.debug.TimerCommand;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.UnhandledError;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static me.luckyraven.util.ChatUtil.color;

public final class CommandManager implements CommandExecutor {

	// classes that shouldn't be displayed in tab completion
	@Getter private static final List<Class<? extends CommandHandler>> filters = Arrays.asList(DebugCommand.class,
																							   OptionCommand.class,
																							   ReadNBTCommand.class,
																							   TimerCommand.class,
																							   DownloadPluginCommand.class);

	private static final Map<String, CommandHandler> commands = new HashMap<>();

	private final Gangland gangland;

	public CommandManager(Gangland gangland) {
		this.gangland = gangland;
	}

	public static List<CommandHandler> getPermissibleCommands(CommandSender sender) {
		List<CommandHandler> commandHandlers = new ArrayList<>();

		for (CommandHandler handler : commands.values()) {
			// filter the commands for non-dev users
			if (!isDev(sender)) if (filters.stream().anyMatch(filterClass -> filterClass.isInstance(handler))) continue;

			String permission = handler.getPermission();

			// check if the user has the permission to suggest the tab completion
			if (permission.isEmpty() || sender.hasPermission(handler.getPermission())) commandHandlers.add(handler);
		}

		return commandHandlers;
	}

	public static Map<String, CommandHandler> getCommands() {
		return new HashMap<>(commands);
	}

	private static boolean isDev(CommandSender sender) {
		if (!(sender instanceof Player player)) return false;

		UUID senderUuid = player.getUniqueId();
		// main & second account
		UUID uuid1 = UUID.fromString("4b2d5e4d-a089-4660-b777-dd71f3fbbbfa");
		UUID uuid2 = UUID.fromString("ad72b2bb-bc30-4c55-a275-106976e70894");

		return senderUuid.equals(uuid1) || senderUuid.equals(uuid2);
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

			for (Map.Entry<String, CommandHandler> entry : commands.entrySet()) {
				if (!(entry.getKey().equalsIgnoreCase(args[0]) ||
					  entry.getValue().getAlias().contains(args[0].toLowerCase()))) continue;

				if (Arrays.stream(args).anyMatch("help"::equalsIgnoreCase)) onHelp(entry, sender, args);
				else entry.getValue().runExecute(sender, args);

				match = true;
				break;
			}

			if (!match) {
				sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_DONT_EXIST.toString(),
														 String.format("/%s %s", label, Arrays.asList(args))));

				List<CommandHandler> commandHandlers = getPermissibleCommands(sender);

				Set<String> dictionary = commandHandlers.stream()
														.map(CommandHandler::getAlias)
														.flatMap(Collection::stream)
														.filter(s -> !s.equals("?"))
														.collect(Collectors.toSet());

				dictionary.addAll(commandHandlers.stream()
												 .map(handler -> handler.getArgument().getArguments()[0])
												 .collect(Collectors.toSet()));

				sender.sendMessage(
						ChatUtil.color(ChatUtil.generateCommandSuggestion(args[0], dictionary, label, null)));
				return false;
			}
		} catch (Throwable throwable) {
			Gangland.getLog4jLogger().error("{}: {}", UnhandledError.COMMANDS_ERROR, throwable.getMessage(), throwable);
			return false;
		}
		return true;
	}

	public void addCommand(CommandHandler sub) {
		commands.put(sub.getLabel(), sub);
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

}
