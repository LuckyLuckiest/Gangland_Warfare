package org.luckyraven.gangland.civilians.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.civilians.command.spawner.CivilianSpawnerCommand;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.keystone.bean.command.CommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public class CivilianCommand extends Command {

	private final CivilianService      civilianService;
	private final CivilianSpawnManager civilianSpawnManager;

	public CivilianCommand(JavaPlugin plugin,
	                       CivilianService civilianService,
	                       CivilianSpawnManager civilianSpawnManager) {
		super(plugin, "civilian", false, "civ");

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
		Argument spawner = new CivilianSpawnerCommand(getPlugin(), getArgumentTree(), getArgument(), civilianService,
		                                              civilianSpawnManager);
		Argument list   = new CivilianListCommand(getPlugin(), getArgumentTree(), getArgument(), civilianService);
		Argument groups = new CivilianGroupsCommand(getPlugin(), getArgumentTree(), getArgument(), civilianService);
		Argument spawn = new CivilianSpawnCommand(getPlugin(), getArgumentTree(), getArgument(), civilianService,
		                                          civilianSpawnManager);
		Argument spawnGroup = new CivilianSpawnGroupCommand(getPlugin(), getArgumentTree(), getArgument(),
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
