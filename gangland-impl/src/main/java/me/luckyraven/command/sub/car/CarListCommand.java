package me.luckyraven.command.sub.car;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.Iterator;
import java.util.Map;

class CarListCommand extends SubArgument {

	private final Gangland gangland;

	CarListCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "list", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			CarAddon         carAddon = gangland.getInitializer().getCarAddon();
			Map<String, Car> cars     = carAddon.getCars();

			sender.sendMessage(GanglandChatUtil.commandMessage("List of cars"));

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
