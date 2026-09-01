package org.luckyraven.gangland.command.sub.rank.parent;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

public class RankParentCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	public RankParentCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "parent", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.rankManager = rankManager;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		};
	}

	private void initializeArguments() {
		Argument addArg    = new RankParentAddCommand(gangland, tree, this, rankManager);
		Argument removeArg = new RankParentRemoveCommand(gangland, tree, this, rankManager);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(addArg);
		arguments.add(removeArg);

		this.addAllSubArguments(arguments);
	}
}
