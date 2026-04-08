package me.luckyraven.command.sub.item;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.sub.item.money.ItemMoneyCommand;
import me.luckyraven.command.sub.item.repair.ItemRepairCommand;
import me.luckyraven.command.sub.item.unique.ItemUniqueCommand;
import me.luckyraven.command.sub.item.wearable.ItemWearableCommand;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class ItemCommand extends Command {

	public ItemCommand(Gangland gangland) {
		super(gangland, "item", true);

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("item"))
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
		Argument repair   = new ItemRepairCommand(getGangland(), getArgumentTree(), getArgument());
		Argument wearable = new ItemWearableCommand(getGangland(), getArgumentTree(), getArgument());
		Argument unique   = new ItemUniqueCommand(getGangland(), getArgumentTree(), getArgument());
		Argument money    = new ItemMoneyCommand(getGangland(), getArgumentTree(), getArgument());

		List<Argument> arguments = new ArrayList<>();

		arguments.add(repair);
		arguments.add(wearable);
		arguments.add(unique);
		arguments.add(money);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Item");
	}

}
