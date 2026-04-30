package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;

import java.util.List;

class RankListCommand extends SubArgument {

	private final RankManager rankManager;

	protected RankListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "list", tree, parent);

		this.rankManager = rankManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(Messages.RANK_LIST_PRIMARY.toString());

			StringBuilder builder = new StringBuilder();
			List<Rank> ranks = rankManager.getRanks().values()
					.stream().toList();

			for (int i = 0; i < ranks.size(); i++) {
				builder.append(ranks.get(i).getName());
				if (i < ranks.size() - 1) builder.append(", ");
			}

			String string  = Messages.RANK_LIST_SECONDARY.toString();
			String replace = string.replace("%ranks%", builder.toString());

			sender.sendMessage(replace);
		};
	}
}
