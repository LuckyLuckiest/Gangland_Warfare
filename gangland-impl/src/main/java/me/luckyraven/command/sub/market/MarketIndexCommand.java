package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.market.contract.MarketPriceContract;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

class MarketIndexCommand extends SubArgument {

	private final MarketPriceContract market;

	MarketIndexCommand(Gangland gangland, Tree<Argument> tree, Argument parent, MarketPriceContract market) {
		super(gangland, "index", tree, parent);
		this.market = market;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(GanglandChatUtil.color(
				"&8[&6Market&8] &fIndex &7= &e" + String.format("%.3f", market.index())));
	}
}
