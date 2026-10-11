package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.command.Operands;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw gang fight accept}. Stub until W6-S. */
public final class FightAcceptCommand extends SubArgument {

	public FightAcceptCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent) {
		super(plugin, "accept", tree, parent);
		OptionalArgument op0 = Operands.operand(plugin, tree, "gang");

		this.addSubArgument(op0);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
		};
	}
}
