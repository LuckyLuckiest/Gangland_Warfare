package me.luckyraven.command.sub.item.unique;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.unique.UniqueItem;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

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
