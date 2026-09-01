package org.luckyraven.gangland.command.sub.banker;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerManager;
import org.luckyraven.keystone.bean.command.CommandHandler;

import java.util.Map;

@CommandHandler
public class BankerCommand extends Command {

	private final BankerManager bankerManager;

	public BankerCommand(Gangland gangland, BankerManager bankerManager) {
		super(gangland, "banker", true, "bankers");
		this.bankerManager = bankerManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("banker"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] args) {
		help(sender, 1);
	}

	@Override
	protected void initializeArguments() {
		getArgument().addSubArgument(new BankerCreateCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                     bankerManager));
		getArgument().addSubArgument(new BankerEditCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                   bankerManager));
		getArgument().addSubArgument(new BankerRemoveCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                     bankerManager));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Banker");
	}

}
