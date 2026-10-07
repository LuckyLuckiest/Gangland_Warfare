package org.luckyraven.gangland.copsncrooks.command.cops;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.copsncrooks.command.cops.setup.SetupCommand;
import org.luckyraven.gangland.copsncrooks.command.cops.spawner.CopSpawnerCommand;
import org.luckyraven.gangland.copsncrooks.npc.police.CopService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.setup.SetupCommands;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.bean.command.CommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public class CopCommand extends Command {

	private final CopService      copService;
	private final CopSpawnManager copSpawnManager;
	private final StationRegistry stationRegistry;
	private final CopLoader       copLoader;
	private final SetupCommands   setupCommands;

	public CopCommand(JavaPlugin plugin, CopService copService, CopSpawnManager copSpawnManager,
	                  StationRegistry stationRegistry, CopLoader copLoader, SetupCommands setupCommands) {
		super(plugin, "cop", false, "cops");

		this.copService      = copService;
		this.copSpawnManager = copSpawnManager;
		this.stationRegistry = stationRegistry;
		this.copLoader       = copLoader;
		this.setupCommands   = setupCommands;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("cop"))
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
		Argument spawner = new CopSpawnerCommand(getPlugin(), getArgumentTree(), getArgument(), copSpawnManager,
		                                           stationRegistry, copLoader);
		Argument list    = new CopListCommand(getPlugin(), getArgumentTree(), getArgument(), copService);
		Argument setup   = new SetupCommand(getPlugin(), getArgumentTree(), getArgument(), setupCommands);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(spawner);
		arguments.add(list);
		arguments.add(setup);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Cops");
	}
}
