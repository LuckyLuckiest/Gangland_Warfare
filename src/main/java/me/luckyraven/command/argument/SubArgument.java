package me.luckyraven.command.argument;

import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;

public abstract class SubArgument extends Argument {

	public SubArgument(String argument, Tree<Argument> tree) {
		this(new String[]{argument}, tree);
	}

	public SubArgument(String[] arguments, Tree<Argument> tree) {
		this(arguments, tree, "");
	}

	public SubArgument(String[] arguments, Tree<Argument> tree, String permission) {
		super(arguments, tree, null, permission);
		super.action = action();
	}

	public SubArgument(String argument, Tree<Argument> tree, String permission) {
		this(new String[]{argument}, tree, permission);
	}

	protected abstract TriConsumer<Argument, CommandSender, String[]> action();

}
