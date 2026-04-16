package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.market.view.MarketDetailView;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Opens {@link MarketDetailView} for the given item — 30-day chart + deltas + live price.
 */
class MarketHistoryCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final MarketPriceContract market;
	private final MarketDetailView    detailView;

	MarketHistoryCommand(Gangland gangland, Tree<Argument> tree, Argument parent, MarketPriceContract market,
	                     MarketDetailView detailView) {
		super(gangland, "history", tree, parent);

		this.gangland   = gangland;
		this.tree       = tree;
		this.market     = market;
		this.detailView = detailView;

		this.addSubArgument(itemArg());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<item>"));
	}

	private OptionalArgument itemArg() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}
			if (args.length < 3) {
				return;
			}
			detailView.open(player, args[2].toLowerCase());
		}, sender -> market.allStates()
				.stream().map(MarketItemState::getItemId).sorted().toList());
	}
}
