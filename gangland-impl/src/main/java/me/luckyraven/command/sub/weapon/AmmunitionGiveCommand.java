package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;
import java.util.Map;

class AmmunitionGiveCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;

	protected AmmunitionGiveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "give", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		ammunitionGive();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void ammunitionGive() {
		Argument name = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player  player         = (Player) sender;
			String  ammoName       = args[2];
			boolean giveAmmunition = giveAmmunition(player, ammoName.toLowerCase(), 1);

			if (giveAmmunition) {
				String gaveAmmo = MessageAddon.RECEIVED_AMMO.toString();
				player.sendMessage(gaveAmmo.replace("%ammo%", ammoName).replace("%amount%", "1"));
			} else {
				String invalidAmmo = MessageAddon.INVALID_AMMO.toString();
				player.sendMessage(invalidAmmo.replace("%args%", ammoName));
			}
		}, sender -> {
			AmmunitionAddon ammunitionAddon = gangland.getInitializer().getAmmunitionAddon();

			return ammunitionAddon.getAmmunitionKeys()
					.stream().toList();
		});

		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player player   = (Player) sender;
			String ammoName = args[2];
			int    ammoAmount;

			try {
				ammoAmount = Integer.parseInt(args[3]);
			} catch (NumberFormatException exception) {
				player.sendMessage(ChatUtil.commandMessage(MessageAddon.MUST_BE_NUMBERS.toString()));
				return;
			}

			boolean giveAmmunition = giveAmmunition(player, ammoName.toLowerCase(), ammoAmount);

			if (giveAmmunition) {
				String gaveAmmo = MessageAddon.RECEIVED_AMMO.toString();
				player.sendMessage(
						gaveAmmo.replace("%ammo%", ammoName).replace("%amount%", String.valueOf(ammoAmount)));
			} else {
				String invalidAmmo = MessageAddon.INVALID_AMMO.toString();
				player.sendMessage(invalidAmmo.replace("%args%", ammoName));
			}
		}, sender -> List.of("<amount>"));

		name.addSubArgument(amount);
		this.addSubArgument(name);
	}

	private boolean giveAmmunition(Player player, String name, int amount) {
		Ammunition ammunition = gangland.getInitializer().getAmmunitionAddon().getAmmunition(name);

		if (ammunition == null) return false;

		int             slots      = (int) Math.ceil(amount / 64D);
		int             amountLeft = amount;
		PlayerInventory inventory  = player.getInventory();
		ItemStack[]     items      = new ItemStack[slots];

		for (int i = 0; i < items.length; ++i) {
			int amountGive = amountLeft % 65;

			if (amountLeft <= 0) break;

			items[i]   = ammunition.buildItem(amountGive);
			amountLeft = Math.max(0, amountLeft - amountGive);
		}

		Map<Integer, ItemStack> left = inventory.addItem(items);

		// make the player drop from their inventory the rest of items
		for (ItemStack item : left.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), item);
		}

		return true;
	}

}
