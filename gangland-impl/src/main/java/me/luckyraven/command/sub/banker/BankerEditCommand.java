package me.luckyraven.command.sub.banker;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.banker.BankerManager;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

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
