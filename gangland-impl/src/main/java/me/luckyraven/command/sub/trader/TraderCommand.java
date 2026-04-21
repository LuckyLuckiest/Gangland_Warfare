package me.luckyraven.command.sub.trader;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import me.luckyraven.core.bean.command.CommandHandler;
import me.luckyraven.shop.ShopRegistry;
import org.bukkit.command.CommandSender;

import java.util.Map;

@CommandHandler
public class TraderCommand extends Command {

	private final TraderManager       traderManager;
	private final ShopRegistry        shopRegistry;
	private final TraderTraitRegistry traitRegistry;

	public TraderCommand(Gangland gangland, TraderManager traderManager, ShopRegistry shopRegistry,
	                     TraderTraitRegistry traitRegistry) {
		super(gangland, "trader", true, "traders");
		this.traderManager = traderManager;
		this.shopRegistry  = shopRegistry;
		this.traitRegistry = traitRegistry;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("trader"))
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
		getArgument().addSubArgument(new TraderCreateCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                     traderManager, shopRegistry, traitRegistry));
		getArgument().addSubArgument(new TraderEditCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                   traderManager, shopRegistry, traitRegistry));
		getArgument().addSubArgument(new TraderRemoveCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                     traderManager));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Trader");
	}

}
