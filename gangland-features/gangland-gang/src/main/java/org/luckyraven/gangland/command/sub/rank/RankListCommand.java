package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;

import java.util.stream.Collectors;

class RankListCommand extends SubArgument {

	private final RankManager rankManager;

	protected RankListCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "list", tree, parent);

		this.rankManager = rankManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(Messages.RANK_LIST_PRIMARY.toString());

			String ranks = rankManager.getRanks().values().stream().map(Rank::getName).collect(Collectors.joining(", "));

			String string  = Messages.RANK_LIST_SECONDARY.toString();
			String replace = string.replace("%ranks%", ranks);

			sender.sendMessage(replace);
		};
	}
}
