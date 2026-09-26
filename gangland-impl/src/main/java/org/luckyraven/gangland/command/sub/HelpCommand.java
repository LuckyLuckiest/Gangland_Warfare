package org.luckyraven.gangland.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
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

	private final InformationManager informationManager;
	private final CommandManager     commandManager;

	public HelpCommand(JavaPlugin gangland, InformationManager informationManager, CommandManager commandManager) {
		super(gangland, "help", false, "general", "?");

		this.informationManager = informationManager;
		this.commandManager     = commandManager;
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
		refreshHelpInfo();
		getHelpInfo().displayHelp(sender, page, "Help");
	}

	/**
	 * Rebuilds the aggregate list on every render instead of once at construction time: this command is built during
	 * the CORE package scan (LOWEST priority only orders it last <em>within that scan</em>), but every module's own
	 * commands register in separate, later {@code scanAndRegisterCommands} calls - a one-time constructor snapshot
	 * would permanently miss every module command's help entries.
	 */
	private void refreshHelpInfo() {
		List<CommandInformation> list = new ArrayList<>();

		list.add(informationManager.getCommands().get("general"));
		list.add(informationManager.getCommands().get("general_page"));
		// Keystone's registry is typed on its own Command; help info lives on the JavaPlugin subclass.
		list.addAll(commandManager.commandView()
		                          .values()
		                          .parallelStream()
		                          .filter(Command.class::isInstance)
		                          .map(Command.class::cast)
		                          .flatMap(entry -> entry.getHelpInfo().getList()
										  .stream())
		                          .toList());

		getHelpInfo().clear();
		getHelpInfo().addAll(list);
	}

}
