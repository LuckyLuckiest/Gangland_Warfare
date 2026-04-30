package org.luckyraven.gangland.command.sub.item.unique;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Iterator;
import java.util.Map;

class ItemUniqueListCommand extends SubArgument {

	private final UniqueItemAddon uniqueItemAddon;

	ItemUniqueListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UniqueItemAddon uniqueItemAddon) {
		super(gangland, "list", tree, parent);

		this.uniqueItemAddon = uniqueItemAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Map<String, UniqueItem> uniqueItems = uniqueItemAddon.getUniqueItems();

			sender.sendMessage(Messages.ITEM_UNIQUE_LIST_HEADER.toString());

			Iterator<Map.Entry<String, UniqueItem>> iterator = uniqueItems.entrySet().iterator();
			StringBuilder                           builder  = new StringBuilder();

			while (iterator.hasNext()) {
				UniqueItem uniqueItem = iterator.next().getValue();

				builder.append("&b").append(uniqueItem.getName());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(GanglandChatUtil.color(builder.toString()));
		};
	}

}
