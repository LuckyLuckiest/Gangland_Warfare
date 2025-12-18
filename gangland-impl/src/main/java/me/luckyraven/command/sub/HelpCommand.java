package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public final class HelpCommand extends CommandHandler {

	public HelpCommand(Gangland gangland) {
		super(gangland, "help", false, "general", "?");

		List<CommandInformation> list = new ArrayList<>();

		list.add(getCommandInformation("general"));
		list.add(getCommandInformation("general_page"));
		list.addAll(CommandManager.getCommands()
								  .values()
								  .parallelStream()
								  .flatMap(entry -> entry.getHelpInfo().getList()
														 .stream())
								  .toList());

		getHelpInfo().addAll(list);
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
	protected void initializeArguments() { }

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Help");
	}

}
