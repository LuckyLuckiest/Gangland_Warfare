package org.luckyraven.gangland.gadget.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class CarCommand extends Command {

	private final UserManager<Player> userManager;
	private final CarAddon            carAddon;

	public CarCommand(JavaPlugin plugin, @Qualifier("online") UserManager<Player> userManager, CarAddon carAddon) {
		super(plugin, "car", true, "cars");

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
		Argument give = new CarGiveCommand(getPlugin(), getArgumentTree(), getArgument(), userManager, carAddon);
		Argument info = new CarInfoCommand(getPlugin(), getArgumentTree(), getArgument(), userManager, carAddon);
		Argument list = new CarListCommand(getPlugin(), getArgumentTree(), getArgument(), carAddon);

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
