package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.gang.rank.RankManager;
import org.bukkit.command.CommandSender;

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
