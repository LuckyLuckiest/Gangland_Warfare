package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.market.contract.MarketMessageContract;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

class MarketPriceCommand extends SubArgument {

	private final Gangland              gangland;
	private final Tree<Argument>        tree;
	private final MarketPriceContract   market;
	private final MarketMessageContract messages;

	MarketPriceCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                   MarketPriceContract market, MarketMessageContract messages) {
		super(gangland, "price", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;
		this.market   = market;
		this.messages = messages;

		this.addSubArgument(priceItem());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<item>"));
	}

	private OptionalArgument priceItem() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (args.length < 3) {
				return;
			}
			String          id    = args[2].toLowerCase();
			MarketItemState state = market.find(id).orElse(null);
			if (state == null) {
				sender.sendMessage(messages.unknownItem(id));
				return;
			}
			sender.sendMessage(messages.priceLine(id, state.effectivePrice(), market.percentageChange(id, 1)));
		}, sender -> market.allStates()
				.stream().map(MarketItemState::getItemId).sorted().toList());
	}
}
