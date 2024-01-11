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

		List<CommandInformation> list = getCommands().entrySet()
		                                             .stream()
		                                             .filter(entry -> entry.getKey().startsWith("waypoint"))
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
		Argument create = new WaypointCreateCommand(getGangland(), getArgumentTree(), getArgument());
		Argument delete = new WaypointDeleteCommand(getGangland(), getArgumentTree(), getArgument());

		Argument select   = new WaypointSelectCommand(getGangland(), getArgumentTree(), getArgument());
		Argument deselect = new WaypointDeselectCommand(getGangland(), getArgumentTree(), getArgument());

		Argument list = new WaypointListCommand(getGangland(), getArgumentTree(), getArgument());
		Argument info = new WaypointInfoCommand(getGangland(), getArgumentTree(), getArgument());

		Argument type     = new WaypointTypeCommand(getGangland(), getArgumentTree(), getArgument());
		Argument gangId   = new WaypointGangIdCommand(getGangland(), getArgumentTree(), getArgument());
		Argument timer    = new WaypointTimerCommand(getGangland(), getArgumentTree(), getArgument());
		Argument cooldown = new WaypointCooldownCommand(getGangland(), getArgumentTree(), getArgument());
		Argument shield   = new WaypointShieldCommand(getGangland(), getArgumentTree(), getArgument());
		Argument cost     = new WaypointCostCommand(getGangland(), getArgumentTree(), getArgument());
		Argument radius   = new WaypointRadiusCommand(getGangland(), getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);

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
