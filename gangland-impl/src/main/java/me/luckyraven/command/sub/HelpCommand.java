package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.CommandManager;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.command.data.InformationManager;
import me.luckyraven.core.command.CommandHandler;
import me.luckyraven.core.command.CommandPriority;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

@CommandHandler(priority = CommandPriority.LOWEST)
public final class HelpCommand extends Command {

	public HelpCommand(Gangland gangland, InformationManager informationManager) {
		super(gangland, "help", false, "general", "?");

		List<CommandInformation> list = new ArrayList<>();

		list.add(informationManager.getCommands().get("general"));
		list.add(informationManager.getCommands().get("general_page"));
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
