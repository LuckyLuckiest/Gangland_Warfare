package me.luckyraven.command.sub.cops;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CopCommand extends CommandHandler {

	public CopCommand(Gangland gangland) {
		super(gangland, "cop", false, "cops");

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
		Argument spawner = new CopSpawnerCommand(getGangland(), getArgumentTree(), getArgument());
		Argument list    = new CopListCommand(getGangland(), getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(spawner);
		arguments.add(list);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Cops");
	}
}
