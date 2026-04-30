package org.luckyraven.gangland.command.sub.jail;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.intake.JailIntakeService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.jail.JailExitService;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

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

	public JailCommand(Gangland gangland,
	                   JailService jailService,
	                   JailRegistry jailRegistry,
	                   DetainmentService detainmentService,
	                   DetainmentRegistry detainmentRegistry,
	                   JailIntakeService jailIntakeService,
	                   ReleasePipeline releasePipeline,
	                   JailExitService jailExitService) {
		super(gangland, "jail", false);

		this.jailService        = jailService;
		this.jailRegistry       = jailRegistry;
		this.detainmentService  = detainmentService;
		this.detainmentRegistry = detainmentRegistry;
		this.jailIntakeService  = jailIntakeService;
		this.releasePipeline    = releasePipeline;
		this.jailExitService    = jailExitService;

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
		commandSender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<create|remove|release>"));
	}

	@Override
	protected void initializeArguments() {
		Argument createArg = new JailCreateCommand(getGangland(), getArgumentTree(), getArgument(), jailService,
		                                           jailRegistry);
		Argument removeArg = new JailRemoveCommand(getGangland(), getArgumentTree(), getArgument(), jailService,
		                                           jailRegistry);
		Argument playerArg = new JailThrowCommand(getGangland(), getArgumentTree(), getArgument(), detainmentService,
		                                          detainmentRegistry, jailIntakeService);
		Argument releaseArg = new JailReleaseCommand(getGangland(), getArgumentTree(), getArgument(),
		                                             detainmentService, releasePipeline);
		Argument listArg     = new JailListCommand(getGangland(), getArgumentTree(), getArgument(), jailRegistry);
		Argument infoArg     = new JailInfoCommand(getGangland(), getArgumentTree(), getArgument(), jailRegistry);
		Argument teleportArg = new JailTeleportCommand(getGangland(), getArgumentTree(), getArgument(), jailRegistry);
		Argument setExitArg = new JailSetExitCommand(getGangland(), getArgumentTree(), getArgument(), jailRegistry,
		                                             jailExitService);

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
