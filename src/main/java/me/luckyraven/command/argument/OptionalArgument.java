package me.luckyraven.command.argument;

import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public class OptionalArgument extends Argument {

	public OptionalArgument(Tree<Argument> tree) {
		super("?", tree);
	}

	public OptionalArgument(Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action) {
		super("?", tree, action);
	}

	public OptionalArgument(String[] possibleArguments, Tree<Argument> tree,
	                        TriConsumer<Argument, CommandSender, String[]> action) {
		super(argumentsWithOptional(possibleArguments), tree, action);
	}

	private static String[] argumentsWithOptional(String[] possibleArguments) {
		String[] arguments = new String[possibleArguments.length + 1];
		System.arraycopy(possibleArguments, 0, arguments, 0, possibleArguments.length);
		arguments[arguments.length - 1] = "?";
		return arguments;
	}

	@Override
	public List<String> getArgumentsString() {
		if (getArguments().length > 1) return Arrays.stream(getArguments()).filter(arg -> !arg.equals("?")).toList();
		return List.of(getArguments());
	}

}
