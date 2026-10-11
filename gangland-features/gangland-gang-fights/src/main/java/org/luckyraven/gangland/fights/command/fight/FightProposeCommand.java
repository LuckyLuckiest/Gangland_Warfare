package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/** {@code /glw gang fight propose <gang> <size> [bet]}. */
public final class FightProposeCommand extends SubArgument {

	private final @Nullable FightManager manager;

	public FightProposeCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, @Nullable FightManager manager) {
		super(plugin, "propose", tree, parent);
		this.manager = manager;

		TriConsumer<Argument, CommandSender, String[]> propose = (argument, sender, args) -> propose(sender, args);
		OptionalArgument gang = FightVerbs.operand(plugin, tree, "gang", (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<size> [bet]")), this::gangNames);
		OptionalArgument size = FightVerbs.operand(plugin, tree, "size", propose,
		                                           () -> List.of("1", "2", "3", "4", "5"));
		OptionalArgument bet  = FightVerbs.operand(plugin, tree, "bet", propose, () -> List.of("0"));

		size.addSubArgument(bet);
		gang.addSubArgument(size);
		this.addSubArgument(gang);
	}

	private List<String> gangNames() {
		return manager == null ? List.of() : manager.gangNames();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang> <size> [bet]"));
	}

	private void propose(CommandSender sender, String[] args) {
		Player player = FightVerbs.player(sender);
		if (player == null || manager == null) return;

		Double size = FightVerbs.number(player, args, FightVerbs.FIRST_OPERAND + 1);
		if (size == null) return;

		double bet = 0;
		if (args.length > FightVerbs.FIRST_OPERAND + 2) {
			Double parsed = FightVerbs.number(player, args, FightVerbs.FIRST_OPERAND + 2);
			if (parsed == null) return;
			bet = parsed;
		}
		manager.propose(player.getUniqueId(), args[FightVerbs.FIRST_OPERAND], size.intValue(), bet);
	}
}
