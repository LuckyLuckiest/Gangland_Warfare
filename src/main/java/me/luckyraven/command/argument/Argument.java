package me.luckyraven.command.argument;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.PluginManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;

public class Argument implements Cloneable {

	private final @Getter String[]            arguments;
	private final @Getter Tree.Node<Argument> node;

	private final Tree<Argument> tree;

	TriConsumer<Argument, CommandSender, String[]> action;

	private @Getter String                              permission;
	private @Getter
	@Setter         BiConsumer<CommandSender, String[]> executeOnPass;

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
		this.arguments = arguments;
		this.tree = tree;
		this.node = new Tree.Node<>(this);
		this.action = action;

		setPermission(permission);
	}

	public Argument(Argument other) {
		this.arguments = other.arguments.clone();
		this.node = other.getNode().clone();
		this.tree = other.tree;
		this.permission = other.permission;
		this.action = other.action;
		this.executeOnPass = other.executeOnPass;
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

	public void setPermission(String permission) {
		this.permission = permission;

		addPermission(permission);
	}

	public void addPermission(String permission) {
		if (permission.isEmpty()) return;

		Permission    perm          = new Permission(permission);
		PluginManager pluginManager = Bukkit.getPluginManager();

		if (!pluginManager.getPermissions().stream().map(Permission::getName).toList().contains(permission))
			pluginManager.addPermission(perm);
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
			Argument arg = traverseList(modifiedArg, sender, args);

			if (arg == null) {
				StringBuilder invalidArg = new StringBuilder(MessageAddon.ARGUMENTS_WRONG.toString());
				Argument      lastValid  = tree.traverseLastValid(modifiedArg);
				if (lastValid != null) {
					for (int i = 0; i < args.length; i++)
						if (Arrays.stream(lastValid.arguments).anyMatch(args[i]::equalsIgnoreCase)) {
							invalidArg.append(args[i + 1]);

							sender.sendMessage(invalidArg.toString());
							break;
						}
				} else sender.sendMessage(invalidArg.append(args[0]).toString());
			} else arg.executeArgument(sender, args);
		} catch (Exception exception) {
			if (exception.getMessage() != null) sender.sendMessage(exception.getMessage());
			else sender.sendMessage("null");

			Bukkit.getLogger().log(Level.WARNING, exception.getMessage(), exception);
		}
	}

	public void executeArgument(CommandSender sender, String[] args) {
		if (this.action != null) this.action.accept(this, sender, args);
		else sender.sendMessage(ChatUtil.errorMessage("Not implemented method!"));
	}

	void executeOnPass(CommandSender sender, String[] args) {
		if (executeOnPass != null) executeOnPass.accept(sender, args);
	}

	private Argument traverseList(Argument[] list, CommandSender sender, String[] args) {
		return traverseList(tree.getRoot(), list, 0, new OptionalArgument(tree), sender, args);
	}

	private <T extends Argument> T traverseList(Tree.Node<T> node, Argument[] list, int index, OptionalArgument dummy,
	                                            CommandSender sender, String[] args) {
		if (node == null || index >= list.length) return null;
		if (!node.getData().equals(list[index]) && !node.getData().equals(dummy)) return null;

		if (!sender.hasPermission(node.getData().getPermission())) {
			sender.sendMessage(MessageAddon.COMMAND_NO_PERM.toString());
			return null;
		}

		node.getData().executeOnPass(sender, args);

		if (index == list.length - 1) return node.getData();

		for (Tree.Node<T> child : node.getChildren()) {
			T result = traverseList(child, list, index + 1, dummy, sender, args);
			if (result != null) return result;
		}

		return null;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Argument argument)) return false;

		return Arrays.stream(argument.arguments).anyMatch(
				arg -> Arrays.stream(this.arguments).anyMatch(arg::equalsIgnoreCase));
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
		return List.of(toString());
	}

}
