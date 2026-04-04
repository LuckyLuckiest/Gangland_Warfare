package me.luckyraven.command.sub.fuel;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.util.GanglandChatUtil;
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

	FuelRefuelCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "refuel", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = gangland.getInitializer().getUserManager();

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
				user.sendMessage(GanglandChatUtil.prefixMessage("Held item has no fuel capacity!"));
				return;
			}

			int       maxFuel = Fuel.readFuelMax(held);
			ItemStack updated = Fuel.writeFuelCurrent(held, maxFuel);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(GanglandChatUtil.commandMessage("Item fully refueled to &b" + maxFuel + "&7."));
		};
	}

	private void refuelAmount() {
		Argument amount = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack held = player.getInventory().getItemInMainHand();

			if (!Fuel.hasFuelCapacity(held)) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Held item has no fuel capacity!"));
				return;
			}

			int refuelAmount;

			try {
				refuelAmount = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				user.sendMessage(GanglandChatUtil.commandMessage(Messages.MUST_BE_NUMBERS.toString()));
				return;
			}

			if (refuelAmount <= 0) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Amount must be greater than zero."));
				return;
			}

			int       current    = Fuel.readFuelCurrent(held);
			int       maxFuel    = Fuel.readFuelMax(held);
			int       newCurrent = Math.min(current + refuelAmount, maxFuel);
			ItemStack updated    = Fuel.writeFuelCurrent(held, newCurrent);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(GanglandChatUtil.commandMessage(
					"Added &b" + (newCurrent - current) + "&7 fuel. Current: &b" + newCurrent + "&7/&b" + maxFuel +
					"&7."));
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amount);
	}

}
