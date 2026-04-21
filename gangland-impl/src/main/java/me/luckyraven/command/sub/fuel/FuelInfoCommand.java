package me.luckyraven.command.sub.fuel;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.JsonFormatter;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.item.fuel.Fuel;
import me.luckyraven.item.fuel.FuelBar;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class FuelInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	FuelInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager) {
		super(gangland, "info", tree, parent);

		this.gangland    = gangland;
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
