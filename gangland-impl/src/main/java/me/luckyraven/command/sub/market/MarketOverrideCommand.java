package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.market.contract.MarketMessageContract;
import me.luckyraven.market.registry.MarketItemRegistry;
import me.luckyraven.market.registry.MarketItemState;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

import java.util.List;

class MarketOverrideCommand extends SubArgument {

	private final Gangland              gangland;
	private final Tree<Argument>        tree;
	private final MarketItemRegistry    itemRegistry;
	private final MarketMessageContract messages;

	MarketOverrideCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                      MarketItemRegistry itemRegistry, MarketMessageContract messages) {
		super(gangland, "override", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.itemRegistry = itemRegistry;
		this.messages     = messages;

		OptionalArgument item  = itemArg();
		OptionalArgument price = priceArg();
		item.addSubArgument(price);
		this.addSubArgument(item);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<item> <price>"));
	}

	private OptionalArgument itemArg() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<price>")),
		                            sender -> itemRegistry.all()
											.stream().map(MarketItemState::getItemId).sorted()
				                                     .toList());
	}

	private OptionalArgument priceArg() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (args.length < 4) {
				return;
			}
			String id = args[2].toLowerCase();
			try {
				double price = Double.parseDouble(args[3]);
				itemRegistry.find(id).ifPresentOrElse(state -> {
					state.setOverridePrice(price);
					itemRegistry.persist(state);
					sender.sendMessage(messages.overrideSet(id, price));
				}, () -> sender.sendMessage(messages.unknownItem(id)));
			} catch (NumberFormatException e) {
				sender.sendMessage(GanglandChatUtil.color("&cInvalid price: " + args[3]));
			}
		}, sender -> List.of("<price>"));
	}
}
