package org.luckyraven.gangland.command.sub.shop;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;

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
				sender.sendMessage(Messages.SHOP_NOT_DEFINED.toString().replace("%shop%", raw));
				return;
			}

			try {
				boolean removed = shopRegistry.delete(raw);
				if (removed) {
					sender.sendMessage(Messages.SHOP_REMOVED.toString().replace("%shop%", raw));
				} else {
					sender.sendMessage(Messages.SHOP_REMOVE_UNTRACKED.toString().replace("%shop%", raw));
				}
			} catch (IOException e) {
				sender.sendMessage(Messages.SHOP_REMOVE_FAILED.toString()
				                                              .replace("%detail%", String.valueOf(e.getMessage())));
			}
		}, sender -> new ArrayList<>(shopRegistry.keys()));

		this.addSubArgument(key);
	}

}
