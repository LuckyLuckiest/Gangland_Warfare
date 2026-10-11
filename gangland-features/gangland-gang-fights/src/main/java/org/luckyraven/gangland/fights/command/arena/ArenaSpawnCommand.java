package org.luckyraven.gangland.fights.command.arena;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.command.Operands;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw arena spawn}. Stub until W6-A. */
public final class ArenaSpawnCommand extends SubArgument {

	public ArenaSpawnCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent) {
		super(plugin, "spawn", tree, parent);
		OptionalArgument op0 = Operands.operand(plugin, tree, "a|b", () -> List.of("a", "b"));

		this.addSubArgument(op0);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
		};
	}
}
