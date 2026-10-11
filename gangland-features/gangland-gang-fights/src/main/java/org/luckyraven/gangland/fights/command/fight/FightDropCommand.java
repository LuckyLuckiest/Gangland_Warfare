package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw gang fight drop}. Stub until W6-S. */
public final class FightDropCommand extends SubArgument {

	public FightDropCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent) {
		super(plugin, "drop", tree, parent);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
		};
	}
}
