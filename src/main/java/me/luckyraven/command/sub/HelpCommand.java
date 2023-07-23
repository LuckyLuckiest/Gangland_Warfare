package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import org.bukkit.command.CommandSender;

import java.util.stream.Collectors;

public class HelpCommand extends CommandHandler {

	public HelpCommand(Gangland gangland) {
		super(gangland, "help", false, "general", "?");
		getHelpInfo().add(getCommandInformation("general"));
		getHelpInfo().add(getCommandInformation("general_page"));

		getHelpInfo().addAll(gangland.getInitializer()
		                             .getCommandManager()
		                             .getCommands()
		                             .values()
		                             .parallelStream()
		                             .flatMap(entry -> entry.getHelpInfo().getList().stream())
		                             .collect(Collectors.toList()));
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		for (String arg : arguments)
			if (getAlias().contains(arg)) {
				help(commandSender, 1);
				break;
			}
	}

	@Override
	protected void initializeArguments(Gangland gangland) {

	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Help");
	}

}
