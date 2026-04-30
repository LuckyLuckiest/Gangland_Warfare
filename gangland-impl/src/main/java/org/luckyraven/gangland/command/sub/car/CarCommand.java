package org.luckyraven.gangland.command.sub.car;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class CarCommand extends Command {

	private final UserManager<Player> userManager;
	private final CarAddon            carAddon;

	public CarCommand(Gangland gangland, @Qualifier("online") UserManager<Player> userManager, CarAddon carAddon) {
		super(gangland, "car", true, "cars");

		this.userManager = userManager;
		this.carAddon    = carAddon;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("car"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument give = new CarGiveCommand(getGangland(), getArgumentTree(), getArgument(), userManager, carAddon);
		Argument info = new CarInfoCommand(getGangland(), getArgumentTree(), getArgument(), userManager, carAddon);
		Argument list = new CarListCommand(getGangland(), getArgumentTree(), getArgument(), carAddon);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Car");
	}

}
