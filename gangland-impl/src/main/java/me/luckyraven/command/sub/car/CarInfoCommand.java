package me.luckyraven.command.sub.car;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

class CarInfoCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	CarInfoCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "info", tree, parent);

		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			ItemStack itemStack = player.getInventory().getItemInMainHand();

			if (!Car.isCarItem(itemStack)) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Not a car item!"));
				return;
			}

			String carId = Car.getCarId(itemStack);

			if (carId == null) return;

			CarAddon carAddon = gangland.getInitializer().getCarAddon();
			Car      car      = carAddon.getCar(carId);

			if (car == null) {
				user.sendMessage(GanglandChatUtil.prefixMessage("Car not registered: &c" + carId));
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
