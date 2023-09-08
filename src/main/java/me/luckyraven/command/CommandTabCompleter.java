package me.luckyraven.command;

import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.sub.debug.DebugCommand;
import me.luckyraven.command.sub.debug.OptionCommand;
import me.luckyraven.command.sub.debug.ReadNBTCommand;
import me.luckyraven.command.sub.debug.TimerCommand;
import me.luckyraven.datastructure.Tree;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class CommandTabCompleter implements TabCompleter {

	private final Map<String, CommandHandler> commandMap;

	// classes that I don't want displayed in tab completion
	private final List<Class<? extends CommandHandler>> filters = Arrays.asList(DebugCommand.class, OptionCommand.class,
	                                                                            ReadNBTCommand.class,
	                                                                            TimerCommand.class);

	public CommandTabCompleter(Map<String, CommandHandler> commandMap) {
		this.commandMap = commandMap;
	}

	@Nullable
	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
	                                  @NotNull String[] args) {
		// commands according to user permission
		List<CommandHandler> commandHandlers = new ArrayList<>();

		for (CommandHandler handler : commandMap.values()) {
			// filter the commands for non-dev users
			if (!isDev(sender)) if (filters.stream().anyMatch(filterClass -> filterClass.isInstance(handler))) continue;

			String permission = handler.getPermission();

			// check if the user has the permission to suggest the tab completion
			if (permission.isEmpty() || sender.hasPermission(handler.getPermission())) commandHandlers.add(handler);
		}

		// display all the initial arguments
		if (args.length == 1) return collectedArguments(args, commandHandlers.stream()
		                                                                     .map(CommandHandler::getLabel)
		                                                                     .toList());

		CommandHandler commandHandler = commandMap.get(args[0].toLowerCase());
		// this won't solve the case of multiple optional values but would definitely stop the tab completion
		// end the command tab completion if the size was greater than the height of the tree
		if (commandHandler != null && args.length > commandHandler.getArgumentTree().height()) return null;

		// find the argument last valid argument
		Argument arg = findArgument(args, commandHandlers);
		if (arg == null) return null;

		List<String> arguments = new ArrayList<>();

		// loop through all the children
		for (Argument argument : arg.getNode().getChildren().stream().map(Tree.Node::getData).toList()) {
			String permission = argument.getPermission();

			if (permission.isEmpty() || sender.hasPermission(permission)) arguments.addAll(
					argument.getArgumentString());
		}
		return collectedArguments(args, arguments);
	}

	private boolean isDev(CommandSender sender) {
		if (!(sender instanceof Player player)) return false;

		UUID senderUuid = player.getUniqueId();
		// main & second account
		UUID uuid1 = UUID.fromString("4b2d5e4d-a089-4660-b777-dd71f3fbbbfa");
		UUID uuid2 = UUID.fromString("ad72b2bb-bc30-4c55-a275-106976e70894");

		return senderUuid.equals(uuid1) || senderUuid.equals(uuid2);
	}

	private List<String> collectedArguments(String[] args, List<String> arguments) {
		String lastArg = args[args.length - 1].toLowerCase();

		if (lastArg.isEmpty()) return arguments;

		Set<String> caseInsensitiveArguments = arguments.stream().map(String::toLowerCase).collect(
				Collectors.toCollection(LinkedHashSet::new));

		return caseInsensitiveArguments.parallelStream().filter(arg -> arg.startsWith(lastArg)).toList();
	}

	private Argument findArgument(String[] args, List<CommandHandler> commandHandlers) {
		Argument[] modifiedArg = new Argument[args.length];

		for (Tree<Argument> tree : commandHandlers.stream().map(CommandHandler::getArgumentTree).toList()) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if (arg.toLowerCase().contains("confirm")) modifiedArg[i] = new ConfirmArgument(tree);
				else modifiedArg[i] = new Argument(arg, tree);
			}

			Argument found = tree.traverseLastValid(modifiedArg);
			if (found != null) return found;
		}

		return null;
	}

}
