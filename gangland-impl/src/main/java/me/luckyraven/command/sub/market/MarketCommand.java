package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.market.contract.MarketMessageContract;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.market.event.MarketShockRegistry;
import me.luckyraven.market.registry.MarketItemRegistry;
import me.luckyraven.market.snapshot.SnapshotService;
import me.luckyraven.market.view.LedgerAdminView;
import me.luckyraven.market.view.MarketDetailView;
import me.luckyraven.market.view.MarketOverviewView;
import me.luckyraven.util.command.CommandHandler;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Root {@code /glw market …} dispatcher. Each subcommand lives in its own {@code SubArgument} class.
 */
@CommandHandler
public final class MarketCommand extends Command {

	private final MarketPriceContract   market;
	private final MarketMessageContract messages;
	private final MarketShockRegistry   shockRegistry;
	private final MarketItemRegistry    itemRegistry;
	private final MarketOverviewView    overviewView;
	private final MarketDetailView      detailView;
	private final LedgerAdminView       ledgerView;
	private final SnapshotService       snapshotService;

	public MarketCommand(Gangland gangland, MarketPriceContract market, MarketMessageContract messages,
	                     MarketShockRegistry shockRegistry, MarketItemRegistry itemRegistry,
	                     MarketOverviewView overviewView, MarketDetailView detailView, LedgerAdminView ledgerView,
	                     SnapshotService snapshotService) {
		super(gangland, "market", false);

		this.market          = market;
		this.messages        = messages;
		this.shockRegistry   = shockRegistry;
		this.itemRegistry    = itemRegistry;
		this.overviewView    = overviewView;
		this.detailView      = detailView;
		this.ledgerView      = ledgerView;
		this.snapshotService = snapshotService;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("market"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] arguments) {
		help(sender, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument marketPriceCommand = new MarketPriceCommand(getGangland(), getArgumentTree(), getArgument(), market,
		                                                     messages);
		Argument marketIndexCommand = new MarketIndexCommand(getGangland(), getArgumentTree(), getArgument(), market);
		Argument marketShockCommand = new MarketShockCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                     shockRegistry, messages);
		Argument marketOverrideCommand = new MarketOverrideCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                           itemRegistry, messages);
		Argument marketUnoverrideCommand = new MarketUnoverrideCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                               itemRegistry, messages);
		Argument marketFreezeCommand = new MarketFreezeCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                       itemRegistry, messages);
		Argument marketOverviewCommand = new MarketOverviewCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                           overviewView);
		Argument marketHistoryCommand = new MarketHistoryCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                         market, detailView);
		Argument marketLedgerCommand = new MarketLedgerCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                       ledgerView);
		Argument marketSnapshotCommand = new MarketSnapshotCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                           snapshotService);

		List<Argument> arguments = new ArrayList<>();

		arguments.add(marketPriceCommand);
		arguments.add(marketIndexCommand);
		arguments.add(marketShockCommand);
		arguments.add(marketOverrideCommand);
		arguments.add(marketUnoverrideCommand);
		arguments.add(marketFreezeCommand);
		arguments.add(marketOverviewCommand);
		arguments.add(marketHistoryCommand);
		arguments.add(marketLedgerCommand);
		arguments.add(marketSnapshotCommand);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Market");
	}
}
