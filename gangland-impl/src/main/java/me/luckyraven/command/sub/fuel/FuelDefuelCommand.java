package me.luckyraven.command.sub.fuel;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.fuel.Fuel;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

class FuelDefuelCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	FuelDefuelCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager) {
		super(gangland, "defuel", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		defuelAmount();
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

			ItemStack updated = Fuel.writeFuelCurrent(held, 0);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(Messages.FUEL_DEFUELED_FULL.toString());
		};
	}

	private void defuelAmount() {
		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack held = player.getInventory().getItemInMainHand();

			if (!Fuel.hasFuelCapacity(held)) {
				user.sendMessage(Messages.FUEL_NO_CAPACITY.toString());
				return;
			}

			int defuelAmount;

			try {
				defuelAmount = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString());
				return;
			}

			if (defuelAmount <= 0) {
				user.sendMessage(Messages.FUEL_AMOUNT_INVALID.toString());
				return;
			}

			int       current    = Fuel.readFuelCurrent(held);
			int       maxFuel    = Fuel.readFuelMax(held);
			int       newCurrent = Math.max(0, current - defuelAmount);
			ItemStack updated    = Fuel.writeFuelCurrent(held, newCurrent);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(Messages.FUEL_DEFUELED_AMOUNT.toString()
			                                              .replace("%removed%", String.valueOf(current - newCurrent))
			                                              .replace("%current%", String.valueOf(newCurrent))
			                                              .replace("%max%", String.valueOf(maxFuel)));
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amount);
	}

}
