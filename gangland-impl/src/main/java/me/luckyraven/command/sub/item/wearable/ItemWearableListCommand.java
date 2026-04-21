package me.luckyraven.command.sub.item.wearable;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

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
