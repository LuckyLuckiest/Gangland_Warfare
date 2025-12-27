package me.luckyraven.command;

import lombok.RequiredArgsConstructor;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class CommandTabCompleter implements TabCompleter {

	private final Map<String, CommandHandler> commandMap;

	@Nullable
	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
									  @NotNull String[] args) {
		// commands according to user permission
		List<CommandHandler> commandHandlers = CommandManager.getPermissibleCommands(sender);

		// display all the initial arguments
		if (args.length == 1)
			return collectedArguments(args, commandHandlers.stream().map(CommandHandler::getLabel).toList());

		CommandHandler commandHandler = commandMap.get(args[0].toLowerCase());
		// this won't solve the case of multiple optional values but would definitely stop the tab completion
		// end the command tab completion if the size was greater than the height of the tree
		if (commandHandler != null && args.length > commandHandler.getArgumentTree().height()) return null;

		// find the argument last valid argument
		Argument arg = findArgument(args, commandHandlers);
		if (arg == null) return null;

		List<String> arguments = new ArrayList<>();

		// loop through all the children
		for (Argument argument : arg.getNode().getChildren()
				.stream().map(Tree.Node::getData).toList()) {
			String permission = argument.getPermission();

			if (permission.isEmpty() || sender.hasPermission(permission)) arguments.addAll(
					argument.getArgumentString(sender));
		}
		return collectedArguments(args, arguments);
	}

	private List<String> collectedArguments(String[] args, List<String> arguments) {
		String lastArg = args[args.length - 1].toLowerCase();

		if (lastArg.isEmpty()) return arguments;

		return arguments.stream().map(String::toLowerCase).filter(arg -> arg.contains(lastArg)).distinct().toList();
	}

	private Argument findArgument(String[] args, List<CommandHandler> commandHandlers) {
		for (Tree<Argument> tree : commandHandlers.stream().map(CommandHandler::getArgumentTree).toList()) {
			// We want to find the parent of the argument being typed
			// so we can show its children as completions
			int targetDepth = args.length - 2;
			if (targetDepth < 0) targetDepth = 0;

			Argument found = traverseToDepth(tree.getRoot(), args, 0, targetDepth);
			if (found != null) return found;
		}

		return null;
	}

	/**
	 * Traverses the argument tree to a specific depth, properly handling OptionalArguments. Uses raw string args
	 * instead of Argument objects to properly handle matching.
	 *
	 * @param node Current node being examined
	 * @param args The raw string arguments from command
	 * @param currentIndex Current position in the args array
	 * @param targetDepth The depth we want to reach
	 *
	 * @return The argument at the target depth, or null if not found
	 */
	@Nullable
	private Argument traverseToDepth(Tree.Node<Argument> node, String[] args, int currentIndex, int targetDepth) {
		if (node == null || currentIndex >= args.length) return null;

		Argument nodeData = node.getData();
		String   inputArg = args[currentIndex];

		// Check if current node matches the argument at this position
		if (!argumentMatches(nodeData, inputArg)) return null;

		// If we've reached our target depth, return this node's data
		if (currentIndex == targetDepth) return nodeData;

		// Try to traverse deeper through children
		for (Tree.Node<Argument> child : node.getChildren()) {
			Argument result = traverseToDepth(child, args, currentIndex + 1, targetDepth);
			if (result != null) return result;
		}

		return null;
	}

	/**
	 * Checks if an Argument matches a given input string. OptionalArguments match any input (they accept anything).
	 * Regular Arguments match if the input equals one of their argument strings.
	 */
	private boolean argumentMatches(Argument argument, String input) {
		// OptionalArgument matches anything
		if (argument instanceof OptionalArgument) return true;

		// ConfirmArgument matches if input contains "confirm"
		if (argument instanceof ConfirmArgument) {
			return input.toLowerCase().contains("confirm");
		}

		// Regular argument - check if input matches any of its argument strings
		return Arrays.stream(argument.getArguments()).anyMatch(arg -> arg.equalsIgnoreCase(input));
	}

}
