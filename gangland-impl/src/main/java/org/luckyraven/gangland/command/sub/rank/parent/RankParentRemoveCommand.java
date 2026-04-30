package org.luckyraven.gangland.command.sub.rank.parent;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Collection;
import java.util.List;

class RankParentRemoveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	RankParentRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "remove", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.rankManager = rankManager;

		rankParent();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		};
	}

	private void rankParent() {
		Argument rankArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<parent>"));
		}, sender -> {
			Collection<Rank> values = rankManager.getRanks().values();

			if (values.isEmpty()) return List.of("<name>");

			return values.stream().map(Rank::getName).toList();
		});

		Argument parentArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
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

			if (rank.equals(childRank)) {
				sender.sendMessage(Messages.RANK_PARENT_SAME.toString());
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
		}, sender -> {
			Collection<Rank> values = rankManager.getRanks().values();

			if (values.isEmpty()) return List.of("<name>");

			return values.stream().map(Rank::getName).toList();
		});

		rankArg.addSubArgument(parentArg);
		this.addSubArgument(rankArg);
	}
}
