package me.luckyraven.command.sub.shop;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.io.IOException;
import java.util.List;

class ShopCreateCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final ShopRegistry   shopRegistry;

	protected ShopCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent, ShopRegistry shopRegistry) {
		super(gangland, "create", tree, parent);

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

			if (!raw.matches("[a-z0-9_]+")) {
				sender.sendMessage(Messages.SHOP_KEY_INVALID.toString());
				return;
			}

			if (shopRegistry.exists(raw)) {
				sender.sendMessage(Messages.SHOP_ALREADY_EXISTS.toString().replace("%shop%", raw));
				return;
			}

			try {
				shopRegistry.createEmpty(raw);
				sender.sendMessage(Messages.SHOP_CREATED.toString().replace("%shop%", raw));
			} catch (IOException e) {
				sender.sendMessage(Messages.SHOP_CREATE_FAILED.toString()
				                                              .replace("%detail%", String.valueOf(e.getMessage())));
			}
		}, sender -> List.of("<key>"));

		this.addSubArgument(key);
	}

}
