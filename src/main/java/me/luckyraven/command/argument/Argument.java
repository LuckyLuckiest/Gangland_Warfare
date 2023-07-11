package me.luckyraven.command.argument;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.datastructure.Node;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public class Argument implements Cloneable {

	@Getter
	private final String[]       arguments;
	@Getter
	private final Node<Argument> node;
	private final Tree<Argument> tree;
	@Getter
	private final String         permission;

	private final TriConsumer<Argument, CommandSender, String[]> action;
	@Getter
	@Setter
	private       BiConsumer<CommandSender, String[]>            executeOnPass;

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
		this.node = new Node<>(this);
		this.permission = permission;
		this.action = action;
	}

	public Argument(Argument other) {
		this.arguments = other.arguments.clone();
		this.node = other.getNode().clone();
		this.tree = other.tree;
		this.permission = other.permission;
		this.action = other.action;
		this.executeOnPass = other.executeOnPass;
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
				StringBuilder invalidArg = new StringBuilder(MessageAddon.ARGUMENTS_WRONG);
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
			sender.sendMessage(ChatUtil.errorMessage(exception.getMessage()));
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

	private <T extends Argument> T traverseList(Node<T> node, Argument[] list, int index, OptionalArgument dummy,
	                                            CommandSender sender, String[] args) {
		if (node == null || index >= list.length) return null;
		if (!node.getData().equals(list[index]) && !node.getData().equals(dummy)) return null;

		if (!sender.hasPermission(node.getData().getPermission())) {
			sender.sendMessage(MessageAddon.NOPERM_CMD);
			return null;
		}

		node.getData().executeOnPass(sender, args);

		if (index == list.length - 1) return node.getData();

		for (Node<T> child : node.getChildren()) {
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
			throw new RuntimeException(exception);
		}
	}

	public List<String> getArgumentsString() {
		return List.of(toString());
	}

	@Override
	public String toString() {
		return arguments[0];
	}

}
