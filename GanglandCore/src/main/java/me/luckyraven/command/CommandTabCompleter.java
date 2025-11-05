package me.luckyraven.command;

import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CommandTabCompleter implements TabCompleter {

	private final JavaPlugin                  plugin;
	private final Map<String, CommandHandler> commandMap;

	public CommandTabCompleter(JavaPlugin plugin, Map<String, CommandHandler> commandMap) {
		this.plugin     = plugin;
		this.commandMap = commandMap;
	}

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
					argument.getArgumentString());
		}
		return collectedArguments(args, arguments);
	}

	private List<String> collectedArguments(String[] args, List<String> arguments) {
		String lastArg = args[args.length - 1].toLowerCase();

		if (lastArg.isEmpty()) return arguments;

		return arguments.stream().map(String::toLowerCase).filter(arg -> arg.contains(lastArg)).distinct().toList();
	}

	private Argument findArgument(String[] args, List<CommandHandler> commandHandlers) {
		Argument[] modifiedArg = new Argument[args.length];

		for (Tree<Argument> tree : commandHandlers.stream().map(CommandHandler::getArgumentTree).toList()) {
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				if (arg.toLowerCase().contains("confirm")) modifiedArg[i] = new ConfirmArgument(plugin, tree);
				else modifiedArg[i] = new Argument(plugin, arg, tree);
			}

			Argument found = tree.traverseLastValid(modifiedArg);
			if (found != null) return found;
		}

		return null;
	}

}
