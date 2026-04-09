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
				user.sendMessage(GanglandChatUtil.prefixMessage("Held item has no fuel capacity!"));
				return;
			}

			int decrease;

			try {
				decrease = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				user.sendMessage(GanglandChatUtil.commandMessage(Messages.MUST_BE_NUMBERS.toString()));
				return;
			}

			if (decrease <= 0) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Amount must be greater than zero."));
				return;
			}

			int       currentMax = Fuel.readFuelMax(held);
			int       newMax     = Math.max(0, currentMax - decrease);
			ItemStack updated    = Fuel.setMaxFuel(held, newMax);
			player.getInventory().setItemInMainHand(updated);

			user.sendMessage(GanglandChatUtil.commandMessage(
					"Max fuel capacity decreased by &b" + decrease + "&7. New max: &b" + newMax + "&7."));
		}, sender -> List.of("<amount>"));

		this.addSubArgument(amount);
	}

}
