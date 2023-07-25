package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.ConfirmArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.command.sub.DebugCommand;
import me.luckyraven.command.sub.OptionCommand;
import me.luckyraven.command.sub.ReadNBTCommand;
import me.luckyraven.data.HelpInfo;
import me.luckyraven.datastructure.Node;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class CommandHandler implements TabCompleter {

	@Getter
	private static final Map<String, CommandHandler> commandMap = new HashMap<>();

	@Getter
	private final String         label;
	@Getter
	private final Set<String>    alias;
	@Getter
	private final String         permission;
	@Getter
	private final boolean        user;
	@Getter
	private final HelpInfo       helpInfo;
	@Getter
	private final Argument       argument;
	@Getter
	private final Tree<Argument> argumentTree;

	private final Gangland gangland;

	public CommandHandler(Gangland gangland, String label, boolean user, String... alias) {
		this.gangland = gangland;
		this.label = label.toLowerCase();

		this.alias = new HashSet<>();
		for (String s : alias) this.alias.add(s.toLowerCase());

		this.permission = "gangland.command." + label;

		this.user = user;
		this.helpInfo = new HelpInfo();
		this.argumentTree = new Tree<>();

		String[] args = new String[alias.length + 1];
		args[0] = label;
		System.arraycopy(alias, 0, args, 1, args.length - 1);

		this.argument = new Argument(args, argumentTree, this::onExecute, this.permission);
		this.argumentTree.add(argument.getNode());

		initializeArguments(gangland);

		commandMap.put(this.label, this);
	}

	protected abstract void onExecute(Argument argument, CommandSender commandSender, String[] arguments);

	protected abstract void initializeArguments(Gangland gangland);

	protected abstract void help(CommandSender sender, int page);

	public void runExecute(CommandSender sender, String[] args) {
		// sender has the permission
		if (!sender.hasPermission(permission)) {
			sender.sendMessage(MessageAddon.COMMAND_NO_PERM.toString());
			return;
		}

		// check if the user should be a Player
		if (user && !(sender instanceof Player)) {
			sender.sendMessage(MessageAddon.NOT_PLAYER.toString());
			return;
		}

		// execute if all checks out
		argument.execute(sender, args);
	}

	@Nullable
	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
	                                  @NotNull String[] args) {
		// commands according to user permission
		List<CommandHandler> commandHandlers = new ArrayList<>();

		// classes that I don't want displayed in tab completion
		List<Class<? extends CommandHandler>> filters = Arrays.asList(DebugCommand.class, OptionCommand.class,
		                                                              ReadNBTCommand.class);

		for (CommandHandler handler : commandMap.values()) {
			if (filters.stream().anyMatch(filterClass -> filterClass.isInstance(handler))) continue;

			if (sender.hasPermission(handler.getPermission())) commandHandlers.add(handler);
		}

		if (args.length == 1) return commandHandlers.stream().map(CommandHandler::getLabel).toList();

		Argument arg = findArgument(args, commandHandlers);

		if (arg == null) return null;

		// TODO if there was an optional argument then no need to get the last valid because it will never be equal

		return arg.getNode().getChildren().stream().map(Node::getData).map(Argument::getArgumentsString).flatMap(
				List::stream).toList();
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

	public Map<String, CommandInformation> getCommands() {
		return gangland.getInitializer().getInformationManager().getCommands();
	}

	public CommandInformation getCommandInformation(String info) {
		return getCommands().get(info);
	}

}
