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
				sender.sendMessage(GanglandChatUtil.commandMessage(
						"&cKey must be lowercase alphanumeric / underscore only."));
				return;
			}

			if (shopRegistry.exists(raw)) {
				sender.sendMessage(GanglandChatUtil.commandMessage("&cShop '" + raw + "' already exists."));
				return;
			}

			try {
				shopRegistry.createEmpty(raw);
				sender.sendMessage(GanglandChatUtil.commandMessage(
						"&aCreated shop '&f" + raw + "&a'. Use &e/glw shop edit " + raw + " &ato add items."));
			} catch (IOException e) {
				sender.sendMessage(GanglandChatUtil.commandMessage("&cFailed to create: " + e.getMessage()));
			}
		}, sender -> List.of("<key>"));

		this.addSubArgument(key);
	}

}
