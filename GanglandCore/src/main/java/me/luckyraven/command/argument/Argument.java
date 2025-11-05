package me.luckyraven.command.argument;

import com.google.common.base.Preconditions;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class Argument implements Cloneable {

	private final boolean    displayAllArguments;
	@Getter(value = AccessLevel.NONE)
	private final JavaPlugin plugin;
	TriConsumer<Argument, CommandSender, String[]> action;
	private Tree.Node<Argument>                 node;
	private String[]                            arguments;
	@Getter(value = AccessLevel.NONE)
	private Tree<Argument>                      tree;
	@NotNull
	private String                              permission;
	@Setter
	private BiConsumer<CommandSender, String[]> executeOnPass;

	public Argument(JavaPlugin plugin, String argument, Tree<Argument> tree) {
		this(plugin, argument, tree, null);
	}

	public Argument(JavaPlugin plugin, String argument, Tree<Argument> tree,
					TriConsumer<Argument, CommandSender, String[]> action) {
		this(plugin, new String[]{argument}, tree, action);
	}

	public Argument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree,
					TriConsumer<Argument, CommandSender, String[]> action) {
		this(plugin, arguments, tree, action, "");
	}

	public Argument(JavaPlugin plugin, String argument, Tree<Argument> tree,
					TriConsumer<Argument, CommandSender, String[]> action, String permission) {
		this(plugin, new String[]{argument}, tree, action, permission);
	}

	public Argument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree,
					TriConsumer<Argument, CommandSender, String[]> action, String permission) {
		this(plugin, arguments, tree, action, permission, false);
	}

	public Argument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree,
					TriConsumer<Argument, CommandSender, String[]> action, String permission,
					boolean displayAllArguments) {
		Preconditions.checkNotNull(permission, "Permission string can't be null");

		this.arguments           = arguments;
		this.tree                = tree;
		this.node                = new Tree.Node<>(this);
		this.action              = action;
		this.displayAllArguments = displayAllArguments;
		this.plugin              = plugin;

		setPermission(permission);
	}

	public Argument(Argument other) {
		Preconditions.checkNotNull(other.permission, "Permission string can't be null");

		this.arguments           = other.arguments.clone();
		this.node                = other.getNode().clone(); // deep cloning needs a lot of processing (no time for that)
		this.tree                = other.tree;
		this.permission          = other.permission;
		this.displayAllArguments = other.displayAllArguments;
		this.action              = other.action;
		this.executeOnPass       = other.executeOnPass;
		this.plugin              = other.plugin;
	}

	/**
	 * Associates the argument with a permission
	 *
	 * @param permission the permission representation
	 */
	public void addPermission(String permission) {
		if (plugin instanceof Gangland gangland) gangland.getInitializer()
														 .getPermissionManager()
														 .addPermission(permission);
		else {
			if (permission.isEmpty()) return;

			PluginManager pluginManager = Bukkit.getPluginManager();
			Permission    perm          = new Permission(permission);
			List<String> permissions = pluginManager.getPermissions()
					.stream().map(Permission::getName).toList();

			// add the permission if it was not in the permission list
			if (!permissions.contains(permission)) pluginManager.addPermission(perm);
		}
	}

	/**
	 * Adds a sub argument to this argument
	 *
	 * @param argument the new argument attached
	 */
	public void addSubArgument(Argument argument) {
		if (tree.contains(argument)) return;
		if (argument.toString().contains("?")) node.add(argument.getNode());
		else node.add(0, argument.getNode());
	}

	/**
	 * Adds all the arguments in the list as a child to this argument
	 *
	 * @param elements the argument list
	 */
	public void addAllSubArguments(List<Argument> elements) {
		elements.forEach(this::addSubArgument);
	}

	/**
	 * <p>
	 * Executes the command according to the type of the argument string.
	 * <p>
	 * The argument tree would be traversed, and each argument according to its type would be executed on arrival or
	 * not.
	 * <p>
	 * According to the states of the argument, if it either executes successfully or gives a no permission message or
	 * redeems as not found.
	 * <p>
	 * The not found argument would search for the last invalid inputted argument and would replace it with a suggestion
	 * of a similar argument using a specific algorithm.
	 *
	 * @param sender the command sender
	 * @param args the argument string array
	 */
	public void execute(CommandSender sender, String[] args) {
		Argument[] modifiedArg = Arrays.stream(args).map(arg -> {
			if (arg.toLowerCase().contains("confirm")) return new ConfirmArgument(plugin, tree);
			return new Argument(plugin, arg, tree);
		}).toArray(Argument[]::new);

		try {
			ArgumentResult<Argument> argument = traverseList(modifiedArg, sender, args);

			switch (argument.getState()) {
				case SUCCESS -> argument.getArgument().executeArgument(sender, args);
				case NO_PERMISSION -> sender.sendMessage(MessageAddon.COMMAND_NO_PERM.toString());
				case NOT_FOUND -> notFound(sender, args, modifiedArg);
			}
		} catch (Throwable throwable) {
			if (throwable.getMessage() != null) sender.sendMessage(throwable.getMessage());
			else sender.sendMessage("null");

			Bukkit.getLogger().log(Level.WARNING, throwable.getMessage(), throwable);
		}
	}

	public void executeArgument(CommandSender sender, String[] args) {
		if (this.action != null) this.action.accept(this, sender, args);
		else sender.sendMessage(ChatUtil.errorMessage("Not implemented method!"));
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Argument argument)) return false;

		return Arrays.stream(argument.arguments)
				.anyMatch(arg -> Arrays.stream(this.arguments).anyMatch(arg::equalsIgnoreCase));
	}

	// TODO test this method
	@Override
	public Argument clone() {
		try {
			Argument argument = (Argument) super.clone();

			// deep clone argument data
			argument.node      = this.node.clone();
			argument.arguments = this.arguments.clone();
			argument.tree      = this.tree.clone();

			return argument;
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public String toString() {
		return arguments[0];
	}

	public List<String> getArgumentString() {
		return displayAllArguments ? List.of(arguments) : List.of(toString());
	}

	protected void setPermission(String permission) {
		this.permission = permission;

		addPermission(permission);
	}

	void executeOnPass(CommandSender sender, String[] args) {
		if (executeOnPass != null) executeOnPass.accept(sender, args);
	}

	private void notFound(CommandSender sender, String[] args, Argument[] modifiedArg) {
		StringBuilder invalidArg = new StringBuilder(MessageAddon.ARGUMENTS_WRONG.toString());
		Argument      lastValid  = tree.traverseLastValid(modifiedArg);

		if (lastValid == null) {
			sender.sendMessage(invalidArg.append(args[0]).toString());
			return;
		}

		for (int i = 0; i < args.length; i++) {
			if (Arrays.stream(lastValid.arguments).noneMatch(args[i]::equalsIgnoreCase)) continue;

			// print the last wrong argument inputted
			int    length = i;
			String lastInput;

			if (i + 1 < args.length) {
				length    = i + 1;
				lastInput = args[length];
				invalidArg.append(lastInput);
			} else {
				lastInput = args[length];
			}

			sender.sendMessage(invalidArg.toString());

			// get the last valid input children
			List<Tree.Node<Argument>> children = lastValid.node.getChildren();
			Set<String> dictionary = children.stream()
					.map(node -> node.getData().arguments)
					.flatMap(Stream::of)
					.filter(s -> !s.equals("?"))
					.collect(Collectors.toSet());
			String[] validArguments = Arrays.stream(args).toList().subList(0, length).toArray(String[]::new);

			sender.sendMessage(
					ChatUtil.color(ChatUtil.generateCommandSuggestion(lastInput, dictionary, "glw", validArguments)));
			break;
		}
	}

	private ArgumentResult<Argument> traverseList(Argument[] list, CommandSender sender, String[] args) {
		return traverseList(tree.getRoot(), list, 0, new OptionalArgument(plugin, tree), sender, args);
	}

	private <T extends Argument> ArgumentResult<T> traverseList(Tree.Node<T> node, T[] list, int index,
																OptionalArgument dummy, CommandSender sender,
																String[] args) {
		if (node == null || index >= list.length) return ArgumentResult.notFound();
		if (!node.getData().equals(list[index]) && !node.getData().equals(dummy)) return ArgumentResult.notFound();

		String permission = node.getData().getPermission();

		if (!permission.isEmpty() && !sender.hasPermission(permission))
			return ArgumentResult.noPermission(node.getData());

		node.getData().executeOnPass(sender, args);

		if (index == list.length - 1) return ArgumentResult.success(node.getData());

		for (Tree.Node<T> child : node.getChildren()) {
			ArgumentResult<T> result = traverseList(child, list, index + 1, dummy, sender, args);

			if (result.getState() == ArgumentResult.ResultState.SUCCESS) return result;
			else if (result.getState() == ArgumentResult.ResultState.NO_PERMISSION)
				return ArgumentResult.noPermission(node.getData());
		}

		return ArgumentResult.notFound();
	}

}
