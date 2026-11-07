package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.gang.rank.RankManager;

import java.util.stream.Collectors;

class RankTraverseCommand extends SubArgument {

	private final RankManager rankManager;

	protected RankTraverseCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "traverse", tree, parent);

		this.rankManager = rankManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			String path = rankManager.getRankTree()
			                         .getAllNodes()
			                         .stream()
			                         .map(Tree.Node::getData)
			                         .map(rank -> "&e" + rank.getName())
			                         .collect(Collectors.joining(" &b-> "));

			sender.sendMessage(ChatUtil.color(path));
		};
	}
}
