package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

class RankParentRemoveCommand extends SubArgument {

	private final RankManager rankManager;

	RankParentRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "remove", tree, parent);

		this.rankManager = gangland.getInitializer().getRankManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			Rank childRank = rankManager.get(args[4]);

			if (childRank == null) {
				sender.sendMessage(Messages.INVALID_RANK_PARENT.toString());
				return;
			}

			if (!rank.getNode().getChildren().contains(childRank.getNode())) {
				sender.sendMessage(Messages.INVALID_RANK_PARENT.toString());
				return;
			}

			rank.getNode().remove(childRank.getNode());

			String string  = Messages.RANK_PARENT_REMOVE.toString();
			String replace = string.replace("%parent%", childRank.getName()).replace("%rank%", rank.getName());

			sender.sendMessage(replace);
		};
	}
}