package org.luckyraven.gangland.copsncrooks.command.jail;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.intake.JailIntakeService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.jail.JailExitService;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.keystone.bean.command.CommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class JailCommand extends Command {

	private final JailService        jailService;
	private final JailRegistry       jailRegistry;
	private final DetainmentService  detainmentService;
	private final DetainmentRegistry detainmentRegistry;
	private final JailIntakeService  jailIntakeService;
	private final ReleasePipeline    releasePipeline;
	private final JailExitService    jailExitService;
	private final DetainmentSettings detainmentSettings;
	private final CommandMessages    commandMessages;

	public JailCommand(JavaPlugin plugin,
	                   JailService jailService,
	                   JailRegistry jailRegistry,
	                   DetainmentService detainmentService,
	                   DetainmentRegistry detainmentRegistry,
	                   JailIntakeService jailIntakeService,
	                   ReleasePipeline releasePipeline,
	                   JailExitService jailExitService,
	                   DetainmentSettings detainmentSettings,
	                   CommandMessages commandMessages) {
		super(plugin, "jail", false);

		this.jailService        = jailService;
		this.jailRegistry       = jailRegistry;
		this.detainmentService  = detainmentService;
		this.detainmentRegistry = detainmentRegistry;
		this.jailIntakeService  = jailIntakeService;
		this.releasePipeline    = releasePipeline;
		this.jailExitService    = jailExitService;
		this.detainmentSettings = detainmentSettings;
		this.commandMessages    = commandMessages;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("jail"))
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
		Argument createArg = new JailCreateCommand(getPlugin(), getArgumentTree(), getArgument(), jailService,
		                                           jailRegistry, detainmentSettings, commandMessages);
		Argument removeArg = new JailRemoveCommand(getPlugin(), getArgumentTree(), getArgument(), jailService,
		                                           jailRegistry, commandMessages);
		Argument playerArg = new JailThrowCommand(getPlugin(), getArgumentTree(), getArgument(), detainmentService,
		                                          detainmentRegistry, jailIntakeService, commandMessages);
		Argument releaseArg = new JailReleaseCommand(getPlugin(), getArgumentTree(), getArgument(),
		                                             detainmentService, releasePipeline, commandMessages);
		Argument listArg     = new JailListCommand(getPlugin(), getArgumentTree(), getArgument(), jailRegistry,
		                                           commandMessages);
		Argument infoArg     = new JailInfoCommand(getPlugin(), getArgumentTree(), getArgument(), jailRegistry,
		                                           commandMessages);
		Argument teleportArg = new JailTeleportCommand(getPlugin(), getArgumentTree(), getArgument(), jailRegistry,
		                                               commandMessages);
		Argument setExitArg = new JailSetExitCommand(getPlugin(), getArgumentTree(), getArgument(), jailRegistry,
		                                             jailExitService, commandMessages);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(createArg);
		arguments.add(removeArg);
		arguments.add(playerArg);
		arguments.add(releaseArg);
		arguments.add(listArg);
		arguments.add(infoArg);
		arguments.add(teleportArg);
		arguments.add(setExitArg);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Jail");
	}
}
