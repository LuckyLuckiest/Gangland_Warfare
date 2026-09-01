package org.luckyraven.gangland.command.sub.car;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.JsonFormatter;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

class CarInfoCommand extends SubArgument {

	private final UserManager<Player> userManager;
	private final CarAddon            carAddon;

	CarInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	               UserManager<Player> userManager, CarAddon carAddon) {
		super(gangland, "info", tree, parent);

		this.userManager = userManager;
		this.carAddon    = carAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!Car.isCarItem(itemStack)) {
				user.sendMessage(Messages.CAR_NOT_A_CAR.toString());
				return;
			}

			String carId = Car.getCarId(itemStack);

			if (carId == null) return;

			Car car = carAddon.getCar(carId);

			if (car == null) {
				user.sendMessage(Messages.CAR_NOT_REGISTERED.toString().replace("%id%", carId));
				return;
			}

			String info = "&7ID&8: &b" + car.getCarId() +
			              "\n&7Name&8: &b" + car.getDisplayName() +
			              "\n&7Material&8: &b" + car.getItemMaterial().name() +
			              "\n&7Max Speed&8: &b" + car.getMaxSpeed() +
			              "\n&7Max Health&8: &b" + car.getMaxHealth() +
			              "\n&7Max Durability&8: &b" + car.getMaxDurability() +
			              "\n&7Fuel Enabled&8: &b" + car.isFuelEnabled() +
			              "\n&7Fuel Key&8: &b" + car.getFuelKey();

			JsonFormatter jsonFormatter = new JsonFormatter();

			user.sendMessage(jsonFormatter.formatToJson(GanglandChatUtil.color(info), " ".repeat(3)));
		};
	}

}
