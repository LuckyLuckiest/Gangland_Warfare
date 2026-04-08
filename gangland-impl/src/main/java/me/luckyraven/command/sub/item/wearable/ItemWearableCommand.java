package me.luckyraven.command.sub.item.wearable;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ItemWearableCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	public ItemWearableCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "wearable", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<give/list/info>"));
	}

	private void initializeArguments() {
		Argument give = new ItemWearableGiveCommand(gangland, tree, this);
		Argument info = new ItemWearableInfoCommand(gangland, tree, this);
		Argument list = new ItemWearableListCommand(gangland, tree, this);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		this.addAllSubArguments(arguments);
	}

}
