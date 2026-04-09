package me.luckyraven.command.sub.car;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.gadget.car.config.CarAddon;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
