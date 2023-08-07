package me.luckyraven.command.argument;

import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;

public abstract class SubArgument extends Argument {

	public SubArgument(String[] arguments, Tree<Argument> tree, Argument parent) {
		this(arguments, tree, "", parent);
	}

	public SubArgument(String[] arguments, Tree<Argument> tree, String subPermission, Argument parent) {
		super(arguments, tree, null);
		super.action = action();
		setPermission(parent.getPermission() + "." + subPermission);
	}

	public SubArgument(String argument, Tree<Argument> tree, Argument parent) {
		this(new String[]{argument}, tree, argument, parent);
	}

	protected abstract TriConsumer<Argument, CommandSender, String[]> action();

}
