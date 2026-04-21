package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.spawn.CivilianSpawnManager;
import me.luckyraven.core.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public class CivilianCommand extends Command {

	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	public CivilianCommand(Gangland gangland,
	                       CivilianService civilianService,
	                       CivilianSpawnManager civilianSpawnManager) {
		super(gangland, "civilian", false, "civ");

		this.civilianService      = civilianService;
		this.civilianSpawnManager = civilianSpawnManager;

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
		Argument spawner = new CivilianSpawnerCommand(getGangland(), getArgumentTree(), getArgument(), civilianService,
		                                              civilianSpawnManager);
		Argument list   = new CivilianListCommand(getGangland(), getArgumentTree(), getArgument(), civilianService);
		Argument groups = new CivilianGroupsCommand(getGangland(), getArgumentTree(), getArgument(), civilianService);
		Argument spawn = new CivilianSpawnCommand(getGangland(), getArgumentTree(), getArgument(), civilianService,
		                                          civilianSpawnManager);
		Argument spawnGroup = new CivilianSpawnGroupCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                    civilianService);

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
