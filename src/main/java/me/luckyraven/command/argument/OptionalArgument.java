package me.luckyraven.command.argument;

import me.luckyraven.datastructure.Tree;

public class OptionalArgument extends Argument {

	public OptionalArgument(Tree<Argument> tree) {
		super("?", tree);
	}

	public OptionalArgument(Tree<Argument> tree, ArgumentAction action) {
		super("?", tree, action);
	}

}
