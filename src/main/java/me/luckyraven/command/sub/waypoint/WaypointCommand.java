package me.luckyraven.command.sub.waypoint;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WaypointCommand extends CommandHandler {

	public WaypointCommand(Gangland gangland) {
		super(gangland, "waypoint", true);

		List<CommandInformation> list = getCommands().entrySet().parallelStream().filter(
				entry -> entry.getKey().startsWith("waypoint")).sorted(Map.Entry.comparingByKey()).map(
				Map.Entry::getValue).toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		Argument create = new WaypointCreateCommand(gangland, getArgumentTree(), getArgument());

		Argument select   = new WaypointSelectCommand(gangland, getArgumentTree(), getArgument());
		Argument deselect = new WaypointDeselectCommand(gangland, getArgumentTree(), getArgument());

		Argument list = new WaypointListCommand(gangland, getArgumentTree(), getArgument());
		Argument info = new WaypointInfoCommand(gangland, getArgumentTree(), getArgument());

		Argument type     = new WaypointTypeCommand(gangland, getArgumentTree(), getArgument());
		Argument gangId   = new WaypointGangIdCommand(gangland, getArgumentTree(), getArgument());
		Argument timer    = new WaypointTimerCommand(gangland, getArgumentTree(), getArgument());
		Argument cooldown = new WaypointCooldownCommand(gangland, getArgumentTree(), getArgument());
		Argument shield   = new WaypointShieldCommand(gangland, getArgumentTree(), getArgument());
		Argument cost     = new WaypointCostCommand(gangland, getArgumentTree(), getArgument());
		Argument radius   = new WaypointRadiusCommand(gangland, getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);

		arguments.add(select);
		arguments.add(deselect);

		arguments.add(list);
		arguments.add(info);

		arguments.add(type);
		arguments.add(gangId);
		arguments.add(timer);
		arguments.add(cooldown);
		arguments.add(shield);
		arguments.add(cost);
		arguments.add(radius);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Waypoint");
	}

}
