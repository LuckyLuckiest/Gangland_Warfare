package me.luckyraven.command.argument;

import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public abstract class SubArgument extends Argument {

	protected SubArgument(JavaPlugin plugin, String argument, Tree<Argument> tree, Argument parent) {
		this(plugin, argument, tree, parent, argument);
	}

	protected SubArgument(JavaPlugin plugin, String argument, Tree<Argument> tree, Argument parent, String permission) {
		this(plugin, new String[]{argument}, tree, parent, permission);
	}

	protected SubArgument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree, Argument parent) {
		this(plugin, arguments, tree, parent, Objects.requireNonNull(arguments[0]));
	}

	protected SubArgument(JavaPlugin plugin, String[] arguments, Tree<Argument> tree, Argument parent,
						  String subPermission) {
		super(plugin, arguments, tree, null);
		super.action = action();

		String permission = String.format("%s.%s", parent.getPermission(), subPermission);
		setPermission(permission);
	}

	protected abstract TriConsumer<Argument, CommandSender, String[]> action();

}
