package me.luckyraven.command.sub.shop;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

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
				sender.sendMessage(GanglandChatUtil.commandMessage("&7No shops defined yet."));
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
