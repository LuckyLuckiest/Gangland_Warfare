package org.luckyraven.gangland.command.sub.item;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.command.sub.item.money.ItemMoneyCommand;
import org.luckyraven.gangland.command.sub.item.unique.ItemUniqueCommand;
import org.luckyraven.gangland.command.sub.item.wearable.ItemWearableCommand;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.gadget.wearable.WearableAddon;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class ItemCommand extends Command {

	private final UserManager<Player> userManager;
	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService moneyDepositService;
	private final UniqueItemAddon     uniqueItemAddon;
	private final WearableAddon       wearableAddon;

	public ItemCommand(Gangland gangland,
	                   @Qualifier("online") UserManager<Player> userManager,
	                   MoneyAddon moneyAddon,
	                   MoneyDepositService moneyDepositService,
	                   UniqueItemAddon uniqueItemAddon,
	                   WearableAddon wearableAddon) {
		super(gangland, "item", true);

		this.userManager         = userManager;
		this.moneyAddon          = moneyAddon;
		this.moneyDepositService = moneyDepositService;
		this.uniqueItemAddon     = uniqueItemAddon;
		this.wearableAddon       = wearableAddon;

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
		Argument wearable = new ItemWearableCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                            wearableAddon);
		Argument unique = new ItemUniqueCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                        uniqueItemAddon);
		Argument money = new ItemMoneyCommand(getGangland(), getArgumentTree(), getArgument(), userManager,
		                                      moneyAddon, moneyDepositService);

		List<Argument> arguments = new ArrayList<>();

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
