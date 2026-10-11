package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw arena list}. */
public final class ArenaListCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaListCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "list", tree, parent);
		this.deps = deps;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			var all = deps.registry().all();
			if (all.isEmpty()) {
				sender.sendMessage(deps.text().text("Arenas.List_Empty"));
				return;
			}
			sender.sendMessage(deps.text().text("Arenas.List_Header"));
			for (var arena : all) {
				sender.sendMessage(deps.text().text("Arenas.List_Entry", "name", arena.name(), "state",
				                                    deps.text().text(arena.enabled() ? "Arenas.State_On" : "Arenas.State_Off"),
				                                    "world", arena.box().world(), "a", arena.spawnsA().size(), "b",
				                                    arena.spawnsB().size()));
			}
		};
	}
}
