package org.luckyraven.gangland.command.sub.fuel;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.JsonFormatter;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.luckyraven.gangland.item.fuel.FuelBar;
import org.luckyraven.gangland.util.GanglandChatUtil;

class FuelInfoCommand extends SubArgument {

	private final UserManager<Player> userManager;

	FuelInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager) {
		super(gangland, "info", tree, parent);

		this.userManager = userManager;
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

			String fuelKey     = Fuel.getFuelKey(held);
			int    currentFuel = Fuel.readFuelCurrent(held);
			int    maxFuel     = Fuel.readFuelMax(held);

			String info = "&7Fuel Key&8: &b" + (fuelKey != null ? fuelKey : "N/A") +
			              "\n&7Current Fuel&8: &b" + currentFuel +
			              "\n&7Max Fuel&8: &b" + maxFuel +
			              "\n&7Bar&8: " + FuelBar.render(currentFuel, maxFuel);

			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(info), " ".repeat(3)));
		};
	}

}
