package me.luckyraven.command.argument;

import me.luckyraven.datastructure.Tree;
import org.bukkit.command.CommandSender;

public abstract class SubArgument extends Argument {

	public SubArgument(String argument, Tree<Argument> tree, Argument parent) {
		this(argument, tree, parent, argument);
	}

	public SubArgument(String argument, Tree<Argument> tree, Argument parent, String permission) {
		this(new String[]{argument}, tree, parent, permission);
	}

	public SubArgument(String[] arguments, Tree<Argument> tree, Argument parent) {
		this(arguments, tree, parent, "");
	}

	public SubArgument(String[] arguments, Tree<Argument> tree, Argument parent, String subPermission) {
		super(arguments, tree, null);
		super.action = action();

		setPermission(parent.getPermission() + "." + subPermission);
	}

	protected abstract TriConsumer<Argument, CommandSender, String[]> action();

}
