package me.luckyraven.command.argument;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.Gangland;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Argument implements Cloneable {

	private final @Getter String[]            arguments;
	private final @Getter Tree.Node<Argument> node;
	private final @Getter boolean             displayAllArguments;

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	TriConsumer<Argument, CommandSender, String[]> action;

	@NotNull private @Getter String                              permission;
	private @Getter @Setter  BiConsumer<CommandSender, String[]> executeOnPass;

	public Argument(String argument, Tree<Argument> tree) {
		this(argument, tree, null);
	}

	public Argument(String argument, Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action) {
		this(new String[]{argument}, tree, action);
	}

	public Argument(String[] arguments, Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action) {
		this(arguments, tree, action, "");
	}

	public Argument(String argument, Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action,
					String permission) {
		this(new String[]{argument}, tree, action, permission);
	}

	public Argument(String[] arguments, Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action,
					String permission) {
		this(arguments, tree, action, permission, false);
	}

	public Argument(String[] arguments, Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action,
					String permission, boolean displayAllArguments) {
		Preconditions.checkNotNull(permission, "Permission string can't be null");

		this.arguments           = arguments;
		this.tree                = tree;
		this.node                = new Tree.Node<>(this);
		this.action              = action;
		this.displayAllArguments = displayAllArguments;

		// use gangland if it was available
		if (Bukkit.getPluginManager().isPluginEnabled("Gangland_Warfare")) {
			this.gangland = JavaPlugin.getPlugin(Gangland.class);
		} else this.gangland = null;

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

		// use gangland if it was available
		if (Bukkit.getPluginManager().isPluginEnabled("Gangland_Warfare")) {
			this.gangland = JavaPlugin.getPlugin(Gangland.class);
		} else this.gangland = null;
	}

	public static String getArgumentSequence(Argument argument) {
		List<String> sequence = new ArrayList<>();

		getArgumentSequence(sequence, argument.getNode());

		Collections.reverse(sequence);

		return "glw " + String.join(" ", sequence) + " " + argument.getArguments()[0];
	}

	private static void getArgumentSequence(List<String> list, Tree.Node<Argument> node) {
		if (node == null || node.getParent() == null) return;

		list.add(node.getParent().getData().getArguments()[0]);
		getArgumentSequence(list, node.getParent());
	}

	public void addPermission(String permission) {
		if (gangland != null) gangland.getInitializer().getPermissionManager().addPermission(permission);
		else {
			if (permission.isEmpty()) return;

			PluginManager pluginManager = Bukkit.getPluginManager();
			Permission    perm          = new Permission(permission);
			List<String>  permissions   = pluginManager.getPermissions().stream().map(Permission::getName).toList();

			// add the permission if it was not in the permission list
			if (!permissions.contains(permission)) pluginManager.addPermission(perm);
		}
	}

	public void addSubArgument(Argument argument) {
		if (tree.contains(argument)) return;
		if (argument.toString().contains("?")) node.add(argument.getNode());
		else node.add(0, argument.getNode());
	}

	public void addAllSubArguments(List<Argument> elements) {
		elements.forEach(this::addSubArgument);
	}

	public void execute(CommandSender sender, String[] args) {
		Argument[] modifiedArg = Arrays.stream(args).map(arg -> {
			if (arg.toLowerCase().contains("confirm")) return new ConfirmArgument(tree);
			return new Argument(arg, tree);
		}).toArray(Argument[]::new);

		try {
			ArgumentResult<Argument> argument = traverseList(modifiedArg, sender, args);

			switch (argument.getState()) {
				case SUCCESS -> argument.getArgument().executeArgument(sender, args);
				case NO_PERMISSION -> sender.sendMessage(MessageAddon.COMMAND_NO_PERM.toString());
				case NOT_FOUND -> {
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
						String[] validArguments = Arrays.stream(args)
														.toList()
														.subList(0, length)
														.toArray(String[]::new);

						sender.sendMessage(ChatUtil.color(
								ChatUtil.generateCommandSuggestion(lastInput, dictionary, "glw", validArguments)));
						break;
					}
				}
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

	@Override
	public Argument clone() {
		try {
			return (Argument) super.clone();
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

	private ArgumentResult<Argument> traverseList(Argument[] list, CommandSender sender, String[] args) {
		return traverseList(tree.getRoot(), list, 0, new OptionalArgument(tree), sender, args);
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
