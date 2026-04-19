package me.luckyraven.command.sub.shop;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.ArrayList;

class ShopRemoveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final ShopRegistry   shopRegistry;

	protected ShopRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent, ShopRegistry shopRegistry) {
		super(gangland, "remove", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.shopRegistry = shopRegistry;

		registerKeyArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<key>"));
	}

	private void registerKeyArgument() {
		Argument key = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String raw = args[2].toLowerCase();

			if (!shopRegistry.exists(raw)) {
				sender.sendMessage(GanglandChatUtil.commandMessage("&cShop '" + raw + "' does not exist."));
				return;
			}

			try {
				boolean removed = shopRegistry.delete(raw);
				if (removed) {
					sender.sendMessage(GanglandChatUtil.commandMessage("&aRemoved shop '&f" + raw + "&a'."));
				} else {
					sender.sendMessage(GanglandChatUtil.commandMessage(
							"&cShop '" + raw + "' was not tracked on disk."));
				}
			} catch (IOException e) {
				sender.sendMessage(GanglandChatUtil.commandMessage("&cFailed to remove: " + e.getMessage()));
			}
		}, sender -> new ArrayList<>(shopRegistry.keys()));

		this.addSubArgument(key);
	}

}
