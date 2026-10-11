package org.luckyraven.gangland.fights.command.fight;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.command.Operands;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw gang fight propose}. Stub until W6-S. */
public final class FightProposeCommand extends SubArgument {

	public FightProposeCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent) {
		super(plugin, "propose", tree, parent);
		OptionalArgument op0 = Operands.operand(plugin, tree, "gang");
		OptionalArgument op1 = Operands.operand(plugin, tree, "size", () -> List.of("1", "2", "3", "4", "5"));
		OptionalArgument op2 = Operands.operand(plugin, tree, "bet");

		op1.addSubArgument(op2);
		op0.addSubArgument(op1);
		this.addSubArgument(op0);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
		};
	}
}
