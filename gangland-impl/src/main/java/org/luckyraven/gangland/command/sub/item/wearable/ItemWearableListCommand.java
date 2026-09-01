package org.luckyraven.gangland.command.sub.item.wearable;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gadget.wearable.WearableAddon;
import org.luckyraven.gangland.item.wearable.Wearable;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Iterator;
import java.util.Map;

class ItemWearableListCommand extends SubArgument {

	private final WearableAddon wearableAddon;

	ItemWearableListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, WearableAddon wearableAddon) {
		super(gangland, "list", tree, parent);

		this.wearableAddon = wearableAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Map<String, Wearable> wearables = wearableAddon.getWearables();

			sender.sendMessage(Messages.ITEM_WEARABLE_LIST_HEADER.toString());

			Iterator<Map.Entry<String, Wearable>> iterator = wearables.entrySet().iterator();
			StringBuilder                         builder  = new StringBuilder();

			while (iterator.hasNext()) {
				Wearable wearable = iterator.next().getValue();

				builder.append("&b").append(wearable.getName());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(GanglandChatUtil.color(builder.toString()));
		};
	}

}
