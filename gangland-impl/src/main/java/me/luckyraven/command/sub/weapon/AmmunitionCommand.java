package me.luckyraven.command.sub.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AmmunitionCommand extends CommandHandler {

	public AmmunitionCommand(Gangland gangland) {
		super(gangland, "ammo", true, "ammunition");

		var list = getCommands().entrySet()
								.stream()
								.filter(entry -> entry.getKey().startsWith("ammunition"))
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
		Argument give = new AmmunitionGiveCommand(getGangland(), getArgumentTree(), getArgument());
		Argument info = new AmmunitionInfoCommand(getGangland(), getArgumentTree(), getArgument());
		Argument list = new AmmunitionListCommand(getGangland(), getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(give);
		arguments.add(info);
		arguments.add(list);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Ammunition");
	}

}
