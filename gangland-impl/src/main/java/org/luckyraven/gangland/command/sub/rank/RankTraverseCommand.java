package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;

import java.util.List;

class RankTraverseCommand extends SubArgument {

	private final RankManager rankManager;

	protected RankTraverseCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "traverse", tree, parent);

		this.rankManager = rankManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			StringBuilder builder = new StringBuilder();

			List<Rank> ranks = rankManager.getRankTree().getAllNodes()
					.stream().map(Tree.Node::getData).toList();

			for (int i = 0; i < ranks.size(); i++) {
				builder.append("&e").append(ranks.get(i).getName());
				if (i < ranks.size() - 1) builder.append(" &b-> ");
			}

			sender.sendMessage(ChatUtil.color(builder.toString()));
		};
	}
}
