package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw arena reload}: re-reads fight/arenas.yml after a hand edit. */
public final class ArenaReloadCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaReloadCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "reload", tree, parent);
		this.deps = deps;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			deps.registry().reload();
			sender.sendMessage(deps.text().text("Arenas.Reloaded", "count", deps.registry().all().size()));
		};
	}
}
