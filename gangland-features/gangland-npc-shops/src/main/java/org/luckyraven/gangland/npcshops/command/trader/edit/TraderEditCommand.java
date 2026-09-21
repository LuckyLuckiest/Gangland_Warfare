package org.luckyraven.gangland.npcshops.command.trader.edit;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.trait.TraderTraitRegistry;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.shop.ShopRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;

public class TraderEditCommand extends SubArgument {

	private final JavaPlugin            plugin;
	private final Tree<Argument>      tree;
	private final TraderManager       traderManager;
	private final ShopRegistry        shopRegistry;
	private final TraderTraitRegistry traitRegistry;

	public TraderEditCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                         TraderManager traderManager, ShopRegistry shopRegistry,
	                         TraderTraitRegistry traitRegistry) {
		super(plugin, "edit", tree, parent);

		this.plugin      = plugin;
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
		this.addSubArgument(new TraderEditShopCommand(plugin, tree, this, traderManager, shopRegistry));
		this.addSubArgument(new TraderEditTraitCommand(plugin, tree, this, traderManager, traitRegistry));
		this.addSubArgument(new TraderEditNameCommand(plugin, tree, this, traderManager));
	}

}
