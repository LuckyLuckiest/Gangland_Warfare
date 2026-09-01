package org.luckyraven.gangland.command.sub.weapon;

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
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

import java.util.List;
import java.util.Map;

class AmmunitionGiveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final AmmunitionManager   ammunitionManager;

	protected AmmunitionGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                UserManager<Player> userManager,
	                                AmmunitionManager ammunitionManager) {
		super(gangland, "give", tree, parent);

		this.gangland          = gangland;
		this.tree              = tree;
		this.userManager       = userManager;
		this.ammunitionManager = ammunitionManager;

		ammunitionGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void ammunitionGive() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String  ammoName       = args[2];
			boolean giveAmmunition = giveAmmunition(player, ammoName.toLowerCase(), 1);

			if (giveAmmunition) {
				String gaveAmmo = Messages.RECEIVED_AMMO.toString();
				user.sendMessage(gaveAmmo.replace("%ammo%", ammoName).replace("%amount%", "1"));
			} else {
				String invalidAmmo = Messages.INVALID_AMMO.toString();
				user.sendMessage(invalidAmmo.replace("%args%", ammoName));
			}
		}, sender -> {
			return ammunitionManager.getAmmunitionKeys()
					.stream().toList();
		});

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			String ammoName = args[2];
			int    ammoAmount;

			try {
				ammoAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				user.sendMessage(GanglandChatUtil.commandMessage(Messages.MUST_BE_NUMBERS.toString()));
				return;
			}

			boolean giveAmmunition = giveAmmunition(player, ammoName.toLowerCase(), ammoAmount);

			if (giveAmmunition) {
				String gaveAmmo = Messages.RECEIVED_AMMO.toString();
				user.sendMessage(gaveAmmo.replace("%ammo%", ammoName).replace("%amount%", String.valueOf(ammoAmount)));
			} else {
				String invalidAmmo = Messages.INVALID_AMMO.toString();
				user.sendMessage(invalidAmmo.replace("%args%", ammoName));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveAmmunition(Player player, String name, int amount) {
		Ammunition ammunition = ammunitionManager.getAmmunition(name);

		if (ammunition == null) return false;

		ItemStack       sampleItem   = ammunition.buildItem(player);
		int             maxStackSize = sampleItem.getMaxStackSize();
		int             slots        = (int) Math.ceil(amount / (double) maxStackSize);
		int             amountLeft   = amount;
		PlayerInventory inventory    = player.getInventory();
		ItemStack[]     items        = new ItemStack[slots];

		for (int i = 0; i < items.length; ++i) {
			int amountGive = Math.min(amountLeft, maxStackSize);

			if (amountGive <= 0) break;

			ItemStack item = ammunition.buildItem(player);

			item.setAmount(amountGive);

			items[i] = item;

			amountLeft -= amountGive;
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		// make the player drop from their inventory the rest of items
		for (ItemStack item : left.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), item);
		}

		return true;
	}

}
