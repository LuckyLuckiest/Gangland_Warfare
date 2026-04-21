package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import org.bukkit.command.CommandSender;

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
				builder.append(ranks.get(i).getName());
				if (i < ranks.size() - 1) builder.append(" -> ");
			}

			sender.sendMessage(builder.toString());
		};
	}
}
