package me.luckyraven.command.sub.item.unique;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.JsonFormatter;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.unique.UniqueItem;
import me.luckyraven.item.unique.UniqueItemUtil;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
