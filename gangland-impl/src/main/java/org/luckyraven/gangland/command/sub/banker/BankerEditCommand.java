package org.luckyraven.gangland.command.sub.banker;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class BankerEditCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final BankerManager  bankerManager;

	protected BankerEditCommand(Gangland gangland, Tree<Argument> tree, Argument parent, BankerManager bankerManager) {
		super(gangland, "edit", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.bankerManager = bankerManager;

		initializeArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void initializeArgument() {
		this.addSubArgument(new BankerEditNameCommand(gangland, tree, this, bankerManager));
	}

}
