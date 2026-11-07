package org.luckyraven.gangland.command.sub.item.unique;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Map;
import java.util.stream.Collectors;

class ItemUniqueListCommand extends SubArgument {

	private final UniqueItemAddon uniqueItemAddon;

	ItemUniqueListCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, UniqueItemAddon uniqueItemAddon) {
		super(gangland, "list", tree, parent);

		this.uniqueItemAddon = uniqueItemAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Map<String, UniqueItem> uniqueItems = uniqueItemAddon.getUniqueItems();

			sender.sendMessage(Messages.ITEM_UNIQUE_LIST_HEADER.toString());

			String line = uniqueItems.values()
					.stream()
					.map(uniqueItem -> "&b" + uniqueItem.getName())
					.collect(Collectors.joining("&7, "));

			sender.sendMessage(GanglandChatUtil.color(line));
		};
	}

}
