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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
			if (filters.stream().anyMatch(filterClass -> filterClass.isInstance(handler))) continue;

			String permission = handler.getPermission();

			if (permission.isEmpty() || sender.hasPermission(handler.getPermission())) commandHandlers.add(handler);
		}

		if (args.length == 1) return collectedArguments(args, commandHandlers.stream()
		                                                                     .map(CommandHandler::getLabel)
		                                                                     .toList());

		CommandHandler commandHandler = commandMap.get(args[0].toLowerCase());
		// this won't solve the case of multiple optional values but would definitely stop the tab completion
		// end the command tab completion if the size was greater than the height of the tree
		if (commandHandler != null && args.length > commandHandler.getArgumentTree().height()) return null;

		Argument arg = findArgument(args, commandHandlers);
		if (arg == null) return null;

		List<String> arguments = new ArrayList<>();

		for (Argument argument : arg.getNode().getChildren().stream().map(Tree.Node::getData).toList()) {
			String permission = argument.getPermission();

			if (permission.isEmpty() || sender.hasPermission(permission)) arguments.addAll(
					argument.getArgumentString());
		}
		return collectedArguments(args, arguments);
	}

	private List<String> collectedArguments(String[] args, List<String> arguments) {
		String lastArg = args[args.length - 1].toLowerCase();

		if (lastArg.isEmpty()) return arguments;

		return arguments.stream().filter(argString -> argString.toLowerCase().startsWith(lastArg)).toList();
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
