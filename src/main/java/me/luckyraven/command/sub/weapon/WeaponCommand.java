package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.data.CommandInformation;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WeaponCommand extends CommandHandler {

	public WeaponCommand(Gangland gangland) {
		super(gangland, "weapon", true);

		List<CommandInformation> list = getCommands().entrySet()
		                                             .stream()
		                                             .filter(entry -> entry.getKey().startsWith("weapon"))
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
		Argument give = new WeaponGiveCommand(getGangland(), getArgumentTree(), getArgument());
		Argument info = new WeaponInfoCommand(getGangland(), getArgumentTree(), getArgument());
		Argument list = new WeaponListCommand(getGangland(), getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Weapon");
	}

}
