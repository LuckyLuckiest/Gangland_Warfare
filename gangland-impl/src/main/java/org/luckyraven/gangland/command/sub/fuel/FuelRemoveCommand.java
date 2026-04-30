package org.luckyraven.gangland.command.sub.fuel;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

class FuelRemoveCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	FuelRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager) {
		super(gangland, "remove", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		removeAmount();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
	}

	private void removeAmount() {
		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack held = player.getInventory().getItemInMainHand();

			if (!Fuel.hasFuelCapacity(held)) {
				user.sendMessage(Messages.FUEL_NO_CAPACITY.toString());
				return;
			}

			int decrease;

			try {
				decrease = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString());
				return;
			}

			if (decrease <= 0) {
				user.sendMessage(Messages.FUEL_AMOUNT_INVALID.toString());
				return;
			}

			int       currentMax = Fuel.readFuelMax(held);
			int       newMax     = Math.max(0, currentMax - decrease);
			ItemStack updated    = Fuel.setMaxFuel(held, newMax);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(Messages.FUEL_CAPACITY_DECREASED.toString()
			                                                 .replace("%amount%", String.valueOf(decrease))
			                                                 .replace("%new_max%", String.valueOf(newMax)));
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amount);
	}

}
