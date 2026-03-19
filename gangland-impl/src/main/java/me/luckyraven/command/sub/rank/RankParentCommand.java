package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

class RankParentCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	protected RankParentCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "parent", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<add/remove>"));
		};
	}

	private void initializeArguments() {
		Argument addArg    = new RankParentAddCommand(gangland, tree, this);
		Argument removeArg = new RankParentRemoveCommand(gangland, tree, this);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(addArg);
		arguments.add(removeArg);

		this.addAllSubArguments(arguments);
	}
}
