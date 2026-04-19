package me.luckyraven.command.sub.fuel;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

class FuelRefuelCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	FuelRefuelCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager) {
		super(gangland, "refuel", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		refuelAmount();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack held = player.getInventory().getItemInMainHand();

			if (!Fuel.hasFuelCapacity(held)) {
				user.sendMessage(Messages.FUEL_NO_CAPACITY.toString());
				return;
			}

			int       maxFuel = Fuel.readFuelMax(held);
			ItemStack updated = Fuel.writeFuelCurrent(held, maxFuel);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(Messages.FUEL_REFUELED_FULL.toString()
			                                            .replace("%max%", String.valueOf(maxFuel)));
		};
	}

	private void refuelAmount() {
		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack held = player.getInventory().getItemInMainHand();

			if (!Fuel.hasFuelCapacity(held)) {
				user.sendMessage(Messages.FUEL_NO_CAPACITY.toString());
				return;
			}

			int refuelAmount;

			try {
				refuelAmount = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString());
				return;
			}

			if (refuelAmount <= 0) {
				user.sendMessage(Messages.FUEL_AMOUNT_INVALID.toString());
				return;
			}

			int       current    = Fuel.readFuelCurrent(held);
			int       maxFuel    = Fuel.readFuelMax(held);
			int       newCurrent = Math.min(current + refuelAmount, maxFuel);
			ItemStack updated    = Fuel.writeFuelCurrent(held, newCurrent);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(Messages.FUEL_REFUELED_AMOUNT.toString()
			                                              .replace("%added%", String.valueOf(newCurrent - current))
			                                              .replace("%current%", String.valueOf(newCurrent))
			                                              .replace("%max%", String.valueOf(maxFuel)));
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amount);
	}

}
