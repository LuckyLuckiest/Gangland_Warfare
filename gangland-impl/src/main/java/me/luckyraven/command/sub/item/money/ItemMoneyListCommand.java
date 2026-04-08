package me.luckyraven.command.sub.item.money;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyItem;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Map;

class ItemMoneyListCommand extends SubArgument {

	private final Gangland gangland;

	ItemMoneyListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			MoneyAddon             moneyAddon = gangland.getInitializer().getMoneyAddon();
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
