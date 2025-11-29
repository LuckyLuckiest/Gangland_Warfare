package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.List;

class RankParentCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankParentCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "parent", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.rankManager = gangland.getInitializer().getRankManager();

		rankParent();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		};
	}

	private void rankParent() {
		Argument parentStr = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			Rank childRank = rankManager.get(args[4]);

			if (childRank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK_PARENT.toString());
				return;
			}

			switch (args[2].toLowerCase()) {
				case "add" -> {
					if (rank.getNode().getChildren().contains(childRank.getNode())) {
						sender.sendMessage(MessageAddon.RANK_EXIST.toString());
						return;
					}
					rank.getNode().add(childRank.getNode());

					String string = MessageAddon.RANK_PARENT_ADD.toString();
					String replace = string
							.replace("%parent%", childRank.getName())
							.replace("%rank%", rank.getName());

					sender.sendMessage(replace);
				}

				case "remove" -> {
					if (!rank.getNode().getChildren().contains(childRank.getNode())) {
						sender.sendMessage(MessageAddon.INVALID_RANK_PARENT.toString());
						return;
					}
					rank.getNode().remove(childRank.getNode());

					String string = MessageAddon.RANK_PARENT_REMOVE.toString();
					String replace = string
							.replace("%parent%", childRank.getName())
							.replace("%rank%", rank.getName());

					sender.sendMessage(replace);
				}
			}
		}, sender -> List.of("<add/remove>"));

		Argument parentName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<parent>"));
		}, sender -> List.of("<parent>"));

		parentName.addSubArgument(parentStr);

		Argument addParent = new Argument(gangland, "add", tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, this.getPermission() + ".add");

		Argument removeParent = new Argument(gangland, "remove", tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, this.getPermission() + ".remove");

		this.addSubArgument(addParent);
		this.addSubArgument(removeParent);

		addParent.addSubArgument(parentName);
		removeParent.addSubArgument(parentName);
	}
}
