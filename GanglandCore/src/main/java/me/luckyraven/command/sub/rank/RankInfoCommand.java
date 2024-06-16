package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;

class RankInfoCommand extends SubArgument {

	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("info", tree, parent);

		this.tree = tree;

		this.rankManager = gangland.getInitializer().getRankManager();

		rankInfo();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void rankInfo() {
		Argument infoName = new OptionalArgument(tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			StringBuilder permBuilder = new StringBuilder();
			for (int i = 0; i < rank.getPermissions().size(); i++) {
				permBuilder.append(rank.getPermissions().get(i));
				if (i < rank.getPermissions().size() - 1) permBuilder.append(", ");
			}

			StringBuilder parentBuilder = new StringBuilder();
			for (int i = 0; i < rank.getNode().getChildren().size(); i++) {
				parentBuilder.append(rank.getNode().getChildren().get(i).getData().getName());
				if (i < rank.getNode().getChildren().size() - 1) parentBuilder.append(", ");
			}

			sender.sendMessage(MessageAddon.RANK_INFO_PRIMARY.toString()
															 .replace("%rank%", rank.getName())
															 .replace("%id%", String.valueOf(rank.getUsedId()))
															 .replace("%parent%", parentBuilder.toString()));
			sender.sendMessage(
					MessageAddon.RANK_INFO_SECONDARY.toString().replace("%permissions%", permBuilder.toString()));
		});

		this.addSubArgument(infoName);
	}
}
