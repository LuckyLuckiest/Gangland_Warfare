package org.luckyraven.gangland.command.sub.fuel;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class FuelCommand extends Command {

	private final UserManager<Player> userManager;

	public FuelCommand(Gangland gangland, @Qualifier("online") UserManager<Player> userManager) {
		super(gangland, "fuel", true, "fuels");

		this.userManager = userManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("fuel"))
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
		Argument add    = new FuelAddCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		Argument remove = new FuelRemoveCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		Argument info   = new FuelInfoCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		Argument refuel = new FuelRefuelCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		Argument defuel = new FuelDefuelCommand(getGangland(), getArgumentTree(), getArgument(), userManager);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(add);
		arguments.add(remove);
		arguments.add(info);
		arguments.add(refuel);
		arguments.add(defuel);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Fuel");
	}

}
