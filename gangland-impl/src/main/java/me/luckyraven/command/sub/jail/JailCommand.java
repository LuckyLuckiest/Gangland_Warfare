package me.luckyraven.command.sub.jail;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JailCommand extends CommandHandler {

	public JailCommand(Gangland gangland) {
		super(gangland, "jail", false);

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
		Argument createArg   = new JailCreateCommand(getGangland(), getArgumentTree(), getArgument());
		Argument removeArg   = new JailRemoveCommand(getGangland(), getArgumentTree(), getArgument());
		Argument playerArg   = new JailThrowCommand(getGangland(), getArgumentTree(), getArgument());
		Argument releaseArg  = new JailReleaseCommand(getGangland(), getArgumentTree(), getArgument());
		Argument listArg     = new JailListCommand(getGangland(), getArgumentTree(), getArgument());
		Argument infoArg     = new JailInfoCommand(getGangland(), getArgumentTree(), getArgument());
		Argument teleportArg = new JailTeleportCommand(getGangland(), getArgumentTree(), getArgument());

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
