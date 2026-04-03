package me.luckyraven.command.sub.item;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.gadget.wearable.WearableAddon;
import me.luckyraven.item.wearable.Wearable;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Map;

class ItemWearableListCommand extends SubArgument {

	private final Gangland gangland;

	ItemWearableListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			WearableAddon         wearableAddon = gangland.getInitializer().getWearableAddon();
			Map<String, Wearable> wearables     = wearableAddon.getWearables();

			sender.sendMessage(GanglandChatUtil.commandMessage("List of wearables"));

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
