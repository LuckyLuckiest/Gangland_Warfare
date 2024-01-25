package me.luckyraven.command.argument;

import me.luckyraven.datastructure.Tree;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public abstract class SubArgument extends Argument {

	protected SubArgument(String argument, Tree<Argument> tree, Argument parent) {
		this(argument, tree, parent, argument);
	}

	protected SubArgument(String argument, Tree<Argument> tree, Argument parent, String permission) {
		this(new String[]{argument}, tree, parent, permission);
	}

	protected SubArgument(String[] arguments, Tree<Argument> tree, Argument parent) {
		this(arguments, tree, parent, Objects.requireNonNull(arguments[0]));
	}

	protected SubArgument(String[] arguments, Tree<Argument> tree, Argument parent, String subPermission) {
		super(arguments, tree, null);
		super.action = action();

		setPermission(parent.getPermission() + "." + subPermission);
	}

	protected abstract TriConsumer<Argument, CommandSender, String[]> action();

}
