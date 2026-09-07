package org.luckyraven.gangland.command.sub.shop;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.shop.ShopDefinition;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.shop.view.ShopAdminFlow;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;

class ShopEditCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final ShopRegistry   shopRegistry;
	private final ShopAdminFlow  adminFlow;

	protected ShopEditCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          ShopRegistry shopRegistry, ShopAdminFlow adminFlow) {
		super(gangland, "edit", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.shopRegistry = shopRegistry;
		this.adminFlow    = adminFlow;

		registerKeyArgument();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<key>"));
	}

	private void registerKeyArgument() {
		Argument key = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			String         raw = args[2].toLowerCase();
			ShopDefinition def = shopRegistry.get(raw);
			if (def == null) {
				player.sendMessage(Messages.SHOP_NOT_DEFINED.toString().replace("%shop%", raw));
				return;
			}

			adminFlow.start(player, def);
		}, sender -> new ArrayList<>(shopRegistry.keys()));

		this.addSubArgument(key);
	}

}
