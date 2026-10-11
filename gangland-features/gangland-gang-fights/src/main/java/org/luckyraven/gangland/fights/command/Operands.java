package org.luckyraven.gangland.fights.command;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;
import java.util.function.Supplier;

/** Builds the positional operands of the fights verbs: chained {@link OptionalArgument}s with a completion source. */
public final class Operands {

	private Operands() {
	}

	/** One operand; the verbs chain several with {@code addSubArgument}. The action is a no-op until a lane fills it. */
	public static OptionalArgument operand(JavaPlugin plugin, Tree<Argument> tree, String label,
	                                       Supplier<List<String>> suggestions) {
		OptionalArgument operand = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
		}, sender -> suggestions.get());
		operand.setDisplayName(label);
		return operand;
	}

	public static OptionalArgument operand(JavaPlugin plugin, Tree<Argument> tree, String label) {
		return operand(plugin, tree, label, List::of);
	}
}
