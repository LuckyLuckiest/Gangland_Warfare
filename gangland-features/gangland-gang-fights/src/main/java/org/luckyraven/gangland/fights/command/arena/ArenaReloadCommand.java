package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw arena reload}. Stub until W6-A. */
public final class ArenaReloadCommand extends SubArgument {

	public ArenaReloadCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent) {
		super(plugin, "reload", tree, parent);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
		};
	}
}
