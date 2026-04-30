package org.luckyraven.gangland.command.sub.item.unique;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.JsonFormatter;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;
import org.luckyraven.gangland.item.unique.UniqueItemUtil;
import org.luckyraven.gangland.util.GanglandChatUtil;

class ItemUniqueInfoCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final UniqueItemAddon     uniqueItemAddon;

	ItemUniqueInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                      UserManager<Player> userManager,
	                      UniqueItemAddon uniqueItemAddon) {
		super(gangland, "info", tree, parent);

		this.userManager     = userManager;
		this.uniqueItemAddon = uniqueItemAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!UniqueItemUtil.isUniqueItem(itemStack)) {
				user.sendMessage(Messages.ITEM_UNIQUE_NOT_UNIQUE.toString());
				return;
			}

			String     key        = UniqueItemUtil.getUniqueItemKey(itemStack);
			UniqueItem uniqueItem = uniqueItemAddon.getUniqueItem(key);

			if (uniqueItem == null) {
				user.sendMessage(Messages.ITEM_UNIQUE_NOT_REGISTERED.toString().replace("%key%", key));
				return;
			}

			String info = "&7Key&8: &b" + uniqueItem.getUniqueItem() +
			              "\n&7Name&8: &b" + uniqueItem.getName() +
			              "\n&7Material&8: &b" + uniqueItem.getMaterial().name() +
			              "\n&7Add On Join&8: &b" + uniqueItem.isAddOnJoin() +
			              "\n&7Add On Respawn&8: &b" + uniqueItem.isAddOnRespawn() +
			              "\n&7Drop On Death&8: &b" + uniqueItem.isDropOnDeath();

			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(info), " ".repeat(3)));
		};
	}

}
