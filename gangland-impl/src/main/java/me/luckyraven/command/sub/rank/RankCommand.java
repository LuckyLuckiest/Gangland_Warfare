package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class RankCommand extends Command {

	public RankCommand(Gangland gangland) {
		super(gangland, "rank", false);

		List<CommandInformation> list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("rank"))
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
		Argument create       = new RankCreateCommand(getGangland(), getArgumentTree(), getArgument());
		Argument delete       = new RankDeleteCommand(getGangland(), getArgumentTree(), getArgument());
		Argument list         = new RankListCommand(getGangland(), getArgumentTree(), getArgument());
		Argument permission   = new RankPermissionCommand(getGangland(), getArgumentTree(), getArgument());
		Argument info         = new RankInfoCommand(getGangland(), getArgumentTree(), getArgument());
		Argument parent       = new RankParentCommand(getGangland(), getArgumentTree(), getArgument());
		Argument traverseTree = new RankTraverseCommand(getGangland(), getArgumentTree(), getArgument());

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(create);
		arguments.add(delete);
		arguments.add(list);
		arguments.add(permission);
		arguments.add(info);
		arguments.add(parent);
		arguments.add(traverseTree);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Rank");
	}

}
