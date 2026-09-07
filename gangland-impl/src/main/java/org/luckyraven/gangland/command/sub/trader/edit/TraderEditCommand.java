package org.luckyraven.gangland.command.sub.trader.edit;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderManager;
import org.luckyraven.gangland.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;

public class TraderEditCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final TraderManager       traderManager;
	private final ShopRegistry        shopRegistry;
	private final TraderTraitRegistry traitRegistry;

	public TraderEditCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                         TraderManager traderManager, ShopRegistry shopRegistry,
	                         TraderTraitRegistry traitRegistry) {
		super(gangland, "edit", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.traderManager = traderManager;
		this.shopRegistry  = shopRegistry;
		this.traitRegistry = traitRegistry;

		initializeArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<shop|trait|name>"));
	}

	private void initializeArgument() {
		this.addSubArgument(new TraderEditShopCommand(gangland, tree, this, traderManager, shopRegistry));
		this.addSubArgument(new TraderEditTraitCommand(gangland, tree, this, traderManager, traitRegistry));
		this.addSubArgument(new TraderEditNameCommand(gangland, tree, this, traderManager));
	}

}
