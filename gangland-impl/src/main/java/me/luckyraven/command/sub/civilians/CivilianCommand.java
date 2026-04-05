package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public class CivilianCommand extends Command {

	public CivilianCommand(Gangland gangland) {
		super(gangland, "civilian", false, "civ");

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("civilian"))
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
		Argument spawner    = new CivilianSpawnerCommand(getGangland(), getArgumentTree(), getArgument());
		Argument list       = new CivilianListCommand(getGangland(), getArgumentTree(), getArgument());
		Argument groups     = new CivilianGroupsCommand(getGangland(), getArgumentTree(), getArgument());
		Argument spawn      = new CivilianSpawnCommand(getGangland(), getArgumentTree(), getArgument());
		Argument spawnGroup = new CivilianSpawnGroupCommand(getGangland(), getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(spawner);
		arguments.add(list);
		arguments.add(groups);
		arguments.add(spawn);
		arguments.add(spawnGroup);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Civilians");
	}
}
