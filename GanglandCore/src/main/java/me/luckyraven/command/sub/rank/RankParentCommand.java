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

class RankParentCommand extends SubArgument {

	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankParentCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("parent", tree, parent);

		this.tree = tree;

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
		Argument parentStr = new OptionalArgument(tree, (argument, sender, args) -> {
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
					sender.sendMessage(MessageAddon.RANK_PARENT_ADD.toString()
																   .replace("%parent%", childRank.getName())
																   .replace("%rank%", rank.getName()));
				}

				case "remove" -> {
					if (!rank.getNode().getChildren().contains(childRank.getNode())) {
						sender.sendMessage(MessageAddon.INVALID_RANK_PARENT.toString());
						return;
					}
					rank.getNode().remove(childRank.getNode());
					sender.sendMessage(MessageAddon.RANK_PARENT_REMOVE.toString()
																	  .replace("%parent%", childRank.getName())
																	  .replace("%rank%", rank.getName()));
				}
			}
		});

		Argument parentName = new OptionalArgument(tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<parent>"));
		});

		parentName.addSubArgument(parentStr);

		Argument addParent = new Argument("add", tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, this.getPermission() + ".add");

		Argument removeParent = new Argument("remove", tree, (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		}, this.getPermission() + ".remove");

		this.addSubArgument(addParent);
		this.addSubArgument(removeParent);

		addParent.addSubArgument(parentName);
		removeParent.addSubArgument(parentName);
	}
}
