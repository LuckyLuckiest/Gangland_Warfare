package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

class RankParentCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankParentCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
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
