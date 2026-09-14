package org.luckyraven.gangland.npcshops.command.banker;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.npcshops.banker.BankerManager;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

class BankerEditCommand extends SubArgument {

	private final JavaPlugin       plugin;
	private final Tree<Argument> tree;
	private final BankerManager  bankerManager;

	protected BankerEditCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, BankerManager bankerManager) {
		super(plugin, "edit", tree, parent);

		this.plugin      = plugin;
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
		this.addSubArgument(new BankerEditNameCommand(plugin, tree, this, bankerManager));
	}

}
