package org.luckyraven.gangland.command.sub.item.wearable;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gadget.wearable.WearableAddon;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.wearable.Wearable;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;
import java.util.Map;

class ItemWearableGiveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final WearableAddon       wearableAddon;

	ItemWearableGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                        UserManager<Player> userManager,
	                        WearableAddon wearableAddon) {
		super(gangland, "give", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.wearableAddon = wearableAddon;

		wearableGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void wearableGive() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String  itemName = args[3];
			boolean gave     = giveWearable(player, itemName, 1);

			if (gave) {
				user.sendMessage(Messages.ITEM_WEARABLE_GAVE.toString()
				                                            .replace("%name%", itemName)
				                                            .replace("%amount%", "1"));
			} else {
				user.sendMessage(Messages.ITEM_WEARABLE_INVALID.toString().replace("%name%", itemName));
			}
		}, sender -> {
			return wearableAddon.getWearables().keySet()
					.stream().toList();
		});

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String itemName = args[3];
			int    itemAmount;

			try {
				itemAmount = Integer.parseInt(args[4]);
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString());
				return;
			}

			boolean gave = giveWearable(player, itemName, itemAmount);

			if (gave) {
				user.sendMessage(Messages.ITEM_WEARABLE_GAVE.toString()
				                                            .replace("%name%", itemName)
				                                            .replace("%amount%", String.valueOf(itemAmount)));
			} else {
				user.sendMessage(Messages.ITEM_WEARABLE_INVALID.toString().replace("%name%", itemName));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveWearable(Player player, String name, int amount) {
		Wearable wearable = wearableAddon.getWearable(name);

		if (wearable == null) return false;

		ItemStack       sampleItem   = wearable.buildItem(player);
		int             maxStackSize = sampleItem.getMaxStackSize();
		int             slots        = (int) Math.ceil(amount / (double) maxStackSize);
		int             amountLeft   = amount;
		PlayerInventory inventory    = player.getInventory();
		ItemStack[]     items        = new ItemStack[slots];

		for (int i = 0; i < items.length; ++i) {
			int amountGive = Math.min(amountLeft, maxStackSize);

			if (amountGive <= 0) break;

			ItemStack item = wearable.buildItem(player);

			item.setAmount(amountGive);

			items[i] = item;

			amountLeft -= amountGive;
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		for (ItemStack item : left.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), item);
		}

		return true;
	}

}
