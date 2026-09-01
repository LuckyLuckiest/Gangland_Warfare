package org.luckyraven.gangland.command.sub;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.CommandManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.bean.command.CommandPriority;

import java.util.ArrayList;
import java.util.List;

@CommandHandler(priority = CommandPriority.LOWEST)
public final class HelpCommand extends Command {

	public HelpCommand(Gangland gangland, InformationManager informationManager) {
		super(gangland, "help", false, "general", "?");

		List<CommandInformation> list = new ArrayList<>();

		list.add(informationManager.getCommands().get("general"));
		list.add(informationManager.getCommands().get("general_page"));
		// Keystone's registry is typed on its own Command; help info lives on the Gangland subclass
		list.addAll(CommandManager.getCommands()
		                          .values()
		                          .parallelStream()
		                          .filter(Command.class::isInstance)
		                          .map(Command.class::cast)
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
