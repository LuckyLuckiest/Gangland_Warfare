package me.luckyraven.command.argument.types;

import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

public class OptionalArgument extends Argument {

	public OptionalArgument(JavaPlugin plugin, Tree<Argument> tree) {
		super(plugin, "?", tree);
	}

	public OptionalArgument(JavaPlugin plugin, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action) {
		super(plugin, "?", tree, action);
	}

	public OptionalArgument(JavaPlugin plugin, String[] possibleArguments, Tree<Argument> tree,
							TriConsumer<Argument, CommandSender, String[]> action) {
		super(plugin, argumentsWithOptional(possibleArguments), tree, action);
	}

	private static String[] argumentsWithOptional(String[] possibleArguments) {
		String[] arguments = new String[possibleArguments.length + 1];
		System.arraycopy(possibleArguments, 0, arguments, 0, possibleArguments.length);
		arguments[arguments.length - 1] = "?";
		return arguments;
	}

	@Override
	public List<String> getArgumentString() {
		if (getArguments().length > 1) return Arrays.stream(getArguments()).filter(arg -> !arg.equals("?")).toList();
		return List.of(getArguments());
	}

}
