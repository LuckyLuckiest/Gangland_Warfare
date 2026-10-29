package org.luckyraven.gangland.copsncrooks.command.cops;

import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.copsncrooks.command.cops.setup.SetupCommand;
import org.luckyraven.gangland.copsncrooks.command.cops.spawner.CopSpawnerCommand;
import org.luckyraven.gangland.copsncrooks.item.CopsItems;
import org.luckyraven.gangland.copsncrooks.npc.police.CopService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapView;
import org.luckyraven.gangland.copsncrooks.setup.SetupCommands;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.bean.command.CommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@CommandHandler
public class CopCommand extends Command {

	/** A {@code cop_setup_*} or {@code cop_spawner_*} row: documented in commands.json, shown by its parent row only. */
	private static final Pattern CHILD_ROW = Pattern.compile("cop_(setup|spawner)_.+");

	private final CopService      copService;
	private final CopSpawnManager copSpawnManager;
	private final StationRegistry stationRegistry;
	private final CopLoader       copLoader;
	private final SetupCommands   setupCommands;
	private final CommandMessages commandMessages;
	private final CopsItems       copsItems;
	private final ScannerMapView  scannerMapView;

	public CopCommand(JavaPlugin plugin, CopService copService, CopSpawnManager copSpawnManager,
	                  StationRegistry stationRegistry, CopLoader copLoader, SetupCommands setupCommands,
	                  CommandMessages commandMessages, CopsItems copsItems, ScannerMapView scannerMapView) {
		super(plugin, "cop", false, "cops");

		this.copService      = copService;
		this.copSpawnManager = copSpawnManager;
		this.stationRegistry = stationRegistry;
		this.copLoader       = copLoader;
		this.setupCommands   = setupCommands;
		this.commandMessages = commandMessages;
		this.copsItems       = copsItems;
		this.scannerMapView  = scannerMapView;

		getHelpInfo().addAll(parentPage(getCommands()));
	}

	/** The rows of the {@code /glw cop} help page, in key order. */
	static List<CommandInformation> parentPage(Map<String, CommandInformation> commands) {
		return commands.entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("cop") && !CHILD_ROW.matcher(entry.getKey()).matches())
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		help(commandSender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument spawner = new CopSpawnerCommand(getPlugin(), getArgumentTree(), getArgument(), copSpawnManager,
		                                           stationRegistry, copLoader, commandMessages);
		Argument list    = new CopListCommand(getPlugin(), getArgumentTree(), getArgument(), copService, commandMessages);
		Argument setup   = new SetupCommand(getPlugin(), getArgumentTree(), getArgument(), setupCommands);
		Argument item    = new CopItemCommand(getPlugin(), getArgumentTree(), getArgument(), copsItems,
		                                      commandMessages, scannerMapView);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(spawner);
		arguments.add(list);
		arguments.add(setup);
		arguments.add(item);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Cops");
	}
}
