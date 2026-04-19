package me.luckyraven.command.sub.shop;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.shop.view.ShopAdminView;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;

class ShopEditCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final ShopRegistry   shopRegistry;
	private final ShopAdminView  adminView;

	protected ShopEditCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          ShopRegistry shopRegistry, ShopAdminView adminView) {
		super(gangland, "edit", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.shopRegistry = shopRegistry;
		this.adminView    = adminView;

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

			adminView.open(player, def);
		}, sender -> new ArrayList<>(shopRegistry.keys()));

		this.addSubArgument(key);
	}

}
