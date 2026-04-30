package org.luckyraven.gangland.command.sub.car;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.util.GanglandChatUtil;

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
