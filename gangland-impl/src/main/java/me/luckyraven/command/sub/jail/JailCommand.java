package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.copsncrooks.detainment.DetainmentRegistry;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.jail.JailRegistry;
import me.luckyraven.copsncrooks.jail.JailService;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class JailCommand extends Command {

	private final JailService        jailService;
	private final JailRegistry       jailRegistry;
	private final DetainmentService  detainmentService;
	private final DetainmentRegistry detainmentRegistry;

	public JailCommand(Gangland gangland,
	                   JailService jailService,
	                   JailRegistry jailRegistry,
	                   DetainmentService detainmentService,
	                   DetainmentRegistry detainmentRegistry) {
		super(gangland, "jail", false);

		this.jailService        = jailService;
		this.jailRegistry       = jailRegistry;
		this.detainmentService  = detainmentService;
		this.detainmentRegistry = detainmentRegistry;

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
		                                          detainmentRegistry);
		Argument releaseArg = new JailReleaseCommand(getGangland(), getArgumentTree(), getArgument(),
		                                             detainmentService);
		Argument listArg     = new JailListCommand(getGangland(), getArgumentTree(), getArgument(), jailRegistry);
		Argument infoArg     = new JailInfoCommand(getGangland(), getArgumentTree(), getArgument(), jailRegistry);
		Argument teleportArg = new JailTeleportCommand(getGangland(), getArgumentTree(), getArgument(), jailRegistry);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(createArg);
		arguments.add(removeArg);
		arguments.add(playerArg);
		arguments.add(releaseArg);
		arguments.add(listArg);
		arguments.add(infoArg);
		arguments.add(teleportArg);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Jail");
	}
}
