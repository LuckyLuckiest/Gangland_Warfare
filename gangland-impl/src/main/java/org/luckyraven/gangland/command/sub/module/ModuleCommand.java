package org.luckyraven.gangland.command.sub.module;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.module.update.ModuleUpdateService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code /glw module} — the operator view of the runtime module folder: what loaded, what was skipped, and installing,
 * updating or removing a module jar from the Maven repository named by {@code Modules.Repository}. Console-runnable
 * because the folder is a server-owner concern; every change takes effect on the next start.
 */
@CommandHandler
public final class ModuleCommand extends Command {

	private final ModuleLoader        moduleLoader;
	private final ModuleUpdateService updateService;

	public ModuleCommand(JavaPlugin gangland, ModuleLoader moduleLoader, ModuleUpdateService updateService) {
		super(gangland, "module", false);

		this.moduleLoader  = moduleLoader;
		this.updateService = updateService;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("module"))
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
		List<Argument> arguments = new ArrayList<>();

		arguments.add(new ModuleListCommand(getPlugin(), getArgumentTree(), getArgument(), moduleLoader));
		arguments.add(new ModuleInstallCommand(getPlugin(), getArgumentTree(), getArgument(), moduleLoader,
		                                       updateService));
		arguments.add(new ModuleUpdateCommand(getPlugin(), getArgumentTree(), getArgument(), moduleLoader,
		                                      updateService));
		arguments.add(new ModuleRemoveCommand(getPlugin(), getArgumentTree(), getArgument(), moduleLoader));

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Module");
	}

}
