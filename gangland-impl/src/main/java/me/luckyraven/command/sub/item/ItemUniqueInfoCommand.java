package me.luckyraven.command.sub.item;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.item.unique.UniqueItem;
import me.luckyraven.item.unique.UniqueItemUtil;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class ItemUniqueInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	ItemUniqueInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!UniqueItemUtil.isUniqueItem(itemStack)) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Not a unique item!"));
				return;
			}

			String          key             = UniqueItemUtil.getUniqueItemKey(itemStack);
			UniqueItemAddon uniqueItemAddon = gangland.getInitializer().getUniqueItemAddon();
			UniqueItem      uniqueItem      = uniqueItemAddon.getUniqueItem(key);

			if (uniqueItem == null) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Unique item not registered: &c" + key));
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
