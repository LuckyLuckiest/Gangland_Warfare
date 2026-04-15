package me.luckyraven.command.sub.trader;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

class TraderEditCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final TraderManager       traderManager;
	private final ShopRegistry        shopRegistry;
	private final TraderTraitRegistry traitRegistry;

	protected TraderEditCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
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
