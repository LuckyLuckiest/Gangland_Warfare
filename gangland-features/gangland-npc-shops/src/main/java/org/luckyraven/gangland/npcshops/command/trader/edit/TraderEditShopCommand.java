package org.luckyraven.gangland.npcshops.command.trader.edit;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;

class TraderEditShopCommand extends SubArgument {

	private static final double TARGET_RANGE = 5D;

	private final JavaPlugin       plugin;
	private final Tree<Argument> tree;
	private final TraderManager  traderManager;
	private final ShopRegistry   shopRegistry;

	protected TraderEditShopCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                                TraderManager traderManager, ShopRegistry shopRegistry) {
		super(plugin, "shop", tree, parent);
		this.plugin      = plugin;
		this.tree          = tree;
		this.traderManager = traderManager;
		this.shopRegistry  = shopRegistry;

		registerValueArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<shopKey>"));
	}

	private void registerValueArgument() {
		Argument valueArg = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			String key = args[3].toLowerCase();
			if (!shopRegistry.exists(key)) {
				player.sendMessage(Messages.TRADER_SHOP_MISSING.toString().replace("%shop%", key));
				return;
			}

			TraderNpc trader = traderManager.findTargetedTrader(player, TARGET_RANGE);
			if (trader == null) {
				player.sendMessage(Messages.TRADER_LOOK_AT.toString()
				                                          .replace("%range%", String.valueOf((int) TARGET_RANGE)));
				return;
			}

			if (traderManager.retargetShop(trader.getData().getId(), key)) {
				player.sendMessage(Messages.TRADER_SHOP_CHANGED.toString().replace("%shop%", key));
			}
		}, sender -> new ArrayList<>(shopRegistry.keys()));

		this.addSubArgument(valueArg);
	}

}
