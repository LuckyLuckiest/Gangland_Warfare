package org.luckyraven.gangland.command.sub.shop;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;

class ShopListCommand extends SubArgument {

	private final ShopRegistry shopRegistry;

	protected ShopListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, ShopRegistry shopRegistry) {
		super(gangland, "list", tree, parent);
		this.shopRegistry = shopRegistry;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (shopRegistry.keys().isEmpty()) {
				sender.sendMessage(Messages.SHOP_LIST_EMPTY.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.color("&8─── &6Shops &8───"));
			for (String key : shopRegistry.keys()) {
				var def       = shopRegistry.get(key);
				int buyCount  = def != null ? def.getBuyEntries().size() : 0;
				int sellCount = def != null ? def.getSellEntries().size() : 0;
				sender.sendMessage(GanglandChatUtil.color(
						"&7- &f" + key + " &8(buy: &e" + buyCount + "&8, sell: &e" + sellCount + "&8)"));
			}
		};
	}

}
