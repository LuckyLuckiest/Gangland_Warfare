package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.rank.Permission;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.List;

class RankInfoCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

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
		Argument infoName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			StringBuilder    permBuilder = new StringBuilder();
			List<Permission> permissions = rank.getPermissions();
			for (int i = 0; i < permissions.size(); i++) {
				permBuilder.append(permissions.get(i));
				if (i < permissions.size() - 1) permBuilder.append(", ");
			}

			StringBuilder parentBuilder = new StringBuilder();
			for (int i = 0; i < rank.getNode().getChildren().size(); i++) {
				parentBuilder.append(rank.getNode().getChildren().get(i).getData().getName());
				if (i < rank.getNode().getChildren().size() - 1) parentBuilder.append(", ");
			}

			String string = MessageAddon.RANK_INFO_PRIMARY.toString();
			String replace = string.replace("%rank%", rank.getName())
								   .replace("%id%", String.valueOf(rank.getUsedId()))
								   .replace("%parent%", parentBuilder.toString());

			sender.sendMessage(replace);

			String string1  = MessageAddon.RANK_INFO_SECONDARY.toString();
			String replace1 = string1.replace("%permissions%", permBuilder.toString());

			sender.sendMessage(replace1);
		}, sender -> List.of("<name>"));

		this.addSubArgument(infoName);
	}
}
