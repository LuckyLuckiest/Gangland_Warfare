package org.luckyraven.gangland.command.sub.item.money;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyItem;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Iterator;
import java.util.Map;

class ItemMoneyListCommand extends SubArgument {

	private final MoneyAddon moneyAddon;

	ItemMoneyListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, MoneyAddon moneyAddon) {
		super(gangland, "list", tree, parent);

		this.moneyAddon = moneyAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Map<String, MoneyItem> variations = moneyAddon.getVariations();

			sender.sendMessage(Messages.ITEM_MONEY_LIST_HEADER.toString());

			Iterator<Map.Entry<String, MoneyItem>> iterator = variations.entrySet().iterator();
			StringBuilder                          builder  = new StringBuilder();

			while (iterator.hasNext()) {
				MoneyItem variation = iterator.next().getValue();

				builder.append("&b").append(variation.getId());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(GanglandChatUtil.color(builder.toString()));
		};
	}

}
