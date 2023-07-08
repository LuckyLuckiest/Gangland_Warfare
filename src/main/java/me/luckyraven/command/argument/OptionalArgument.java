package me.luckyraven.command.argument;

import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;

public class OptionalArgument extends Argument {

	public OptionalArgument(Tree<Argument> tree) {
		super("?", tree);
	}

	public OptionalArgument(Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action) {
		super("?", tree, action);
	}

}
