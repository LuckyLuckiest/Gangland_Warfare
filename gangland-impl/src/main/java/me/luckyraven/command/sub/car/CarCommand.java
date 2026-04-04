package me.luckyraven.command.sub.car;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CarCommand extends CommandHandler {

	public CarCommand(Gangland gangland) {
		super(gangland, "car", true, "cars");

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
		Argument give = new CarGiveCommand(getGangland(), getArgumentTree(), getArgument());
		Argument info = new CarInfoCommand(getGangland(), getArgumentTree(), getArgument());
		Argument list = new CarListCommand(getGangland(), getArgumentTree(), getArgument());

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
