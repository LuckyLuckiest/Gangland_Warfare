package me.luckyraven.command.sub.trader.edit;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

class TraderEditShopCommand extends SubArgument {

	private static final double TARGET_RANGE = 5D;

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final TraderManager  traderManager;
	private final ShopRegistry   shopRegistry;

	protected TraderEditShopCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                TraderManager traderManager, ShopRegistry shopRegistry) {
		super(gangland, "shop", tree, parent);
		this.gangland      = gangland;
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
		Argument valueArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
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
