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

public class Argument {

	@Getter
	private final String[]       arguments;
	@Getter
	private final Node<Argument> node;

	private final Tree<Argument> tree;
	private final ArgumentAction action;

	@Setter
	private ArgumentAction executeOnPass;

	public Argument(String argument, Tree<Argument> tree) {
		this(argument, tree, null);
	}

	public Argument(String argument, Tree<Argument> tree, ArgumentAction action) {
		this(new String[]{argument}, tree, action);
	}

	public Argument(String[] arguments, Tree<Argument> tree, ArgumentAction action) {
		this.arguments = arguments;
		this.tree = tree;
		this.node = new Node<>(this);
		this.action = action;
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
		Argument[] modifiedArg = Arrays.stream(args).map(arg -> createArgumentInstance(arg, tree)).toArray(
				Argument[]::new);

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
	}

	private Argument createArgumentInstance(String arg, Tree<Argument> tree) {
		if (arg.toLowerCase().contains("confirm")) return new ConfirmArgument(tree);
		return new Argument(arg, tree);
	}

	public void executeArgument(CommandSender sender, String[] args) {
		if (this.action != null) this.action.execute(sender, args);
		else sender.sendMessage(ChatUtil.errorMessage("Not implemented method!"));
	}

	void executeOnPass(CommandSender sender, String[] args) {
		if (executeOnPass != null) executeOnPass.execute(sender, args);
	}

	private Argument traverseList(Argument[] list, CommandSender sender, String[] args) {
		return traverseList(tree.getRoot(), list, 0, new OptionalArgument(tree), sender, args);
	}

	private <T extends Argument> T traverseList(Node<T> node, Argument[] list, int index, OptionalArgument dummy,
	                                            CommandSender sender, String[] args) {
		if (node == null || index >= list.length) return null;

		if (node.getData().equals(dummy)) return node.getData();

		if (!node.getData().equals(list[index])) return null;

		if (index == list.length - 1) return node.getData();

		for (Node<T> child : node.getChildren()) {
			T result = traverseList(child, list, index + 1, dummy, sender, args);
			if (result != null) {
				result.executeOnPass(sender, args);
				return result;
			}
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
	public String toString() {
		return String.format("[%s]", arguments[0]);
	}

}
