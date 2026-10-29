package org.luckyraven.gangland.fights.command.arena;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw arena off <name>}. */
public final class ArenaOffCommand extends SubArgument {

	private final ArenaDeps deps;

	public ArenaOffCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, ArenaDeps deps) {
		super(plugin, "off", tree, parent);
		this.deps = deps;
		this.addSubArgument(deps.operand(plugin, tree, "name", null, this::run));
	}

	private void run(CommandSender sender, String name) {
		Arena arena = deps.registry().byName(name).orElse(null);
		if (arena == null) {
			sender.sendMessage(deps.text().text("Arenas.Not_Found", "name", name));
			return;
		}
		if (!deps.registry().setEnabled(arena.name(), false)) {
			sender.sendMessage(deps.text().text("Arenas.Save_Failed"));
			return;
		}
		sender.sendMessage(deps.text().text("Arenas.Disabled", "name", arena.name()));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> ArenaDeps.usage(sender, "/glw arena off <name>");
	}
}
