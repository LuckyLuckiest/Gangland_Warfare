package me.luckyraven.command.sub.car;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Map;

class CarListCommand extends SubArgument {

	private final CarAddon carAddon;

	CarListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CarAddon carAddon) {
		super(gangland, "list", tree, parent);

		this.carAddon = carAddon;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Map<String, Car> cars = carAddon.getCars();

			sender.sendMessage(Messages.CAR_LIST_HEADER.toString());

			Iterator<Map.Entry<String, Car>> iterator = cars.entrySet().iterator();
			StringBuilder                    builder  = new StringBuilder();

			while (iterator.hasNext()) {
				Car car = iterator.next().getValue();

				builder.append("&b").append(car.getDisplayName());
				if (iterator.hasNext()) builder.append("&7, ");
			}

			sender.sendMessage(GanglandChatUtil.color(builder.toString()));
		};
	}

}
