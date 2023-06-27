package me.luckyraven.command;

import lombok.Getter;
import me.luckyraven.datastructure.Node;
import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public class Argument {

	@Getter
	private final String[]       arguments;
	@Getter
	private final Node<Argument> node;

	private final Tree<Argument> tree;
	private final ArgumentAction action;

	public Argument(String[] arguments, Tree<Argument> tree, ArgumentAction action) {
		this.arguments = arguments;
		this.tree = tree;
		this.node = new Node<>(this);
		this.action = action;
	}

	public void addSubArgument(Argument argument) {
		if (tree.contains(argument)) return;
		node.add(argument.getNode());
	}

	public void execute(CommandSender sender, String[] args) {
		Argument[] modifiedArg = Arrays.stream(args).map(arg -> new Argument(new String[]{arg}, tree, null)).toArray(
				Argument[]::new);

		Argument arg = tree.traverseToList(modifiedArg);

		if (arg == null) {
			StringBuilder invalidArg = new StringBuilder("Invalid argument: ");
			Argument      lastValid  = tree.traverseLastValid(modifiedArg);
			if (lastValid != null) {
				for (int i = 0; i < args.length; i++)
					if (Arrays.stream(lastValid.arguments).anyMatch(args[i]::equalsIgnoreCase)) {
						invalidArg.append(args[i + 1]);

						sender.sendMessage(invalidArg.toString());
						break;
					}
			} else sender.sendMessage(invalidArg.append(args[0]).toString());
		} else {
			if (arg.action != null) arg.action.execute(sender, args);
			else sender.sendMessage("Not implemented yet!");
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;

		Argument argument = (Argument) obj;

		return Arrays.stream(argument.arguments).anyMatch(
				arg -> Arrays.stream(this.arguments).anyMatch(arg::equalsIgnoreCase));
	}

	@Override
	public String toString() {
		return String.format("[%s]", arguments[0]);
	}

	@FunctionalInterface
	public interface ArgumentAction {

		void execute(CommandSender sender, String[] args);

	}

}
