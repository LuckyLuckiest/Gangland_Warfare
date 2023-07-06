package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.stream.Collectors;

public class SCHelp extends CommandHandler {

	public SCHelp(Gangland gangland) {
		super(gangland, "help", false, "general", "?");
		getHelpInfo().add(getCommandInformation("general"));
		getHelpInfo().add(getCommandInformation("general_page"));

		getHelpInfo().addAll(gangland.getInitializer()
		                             .getCommandManager()
		                             .getCommands()
		                             .values()
		                             .stream()
		                             .flatMap(entry -> entry.getHelpInfo().getList().stream())
		                             .collect(Collectors.toList()));
	}

	@Override
	protected void onExecute(CommandSender commandSender, String[] arguments) {
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
