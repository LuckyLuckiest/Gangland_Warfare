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

class MarketFreezeCommand extends SubArgument {

	private final Gangland              gangland;
	private final Tree<Argument>        tree;
	private final MarketItemRegistry    itemRegistry;
	private final MarketMessageContract messages;

	MarketFreezeCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                    MarketItemRegistry itemRegistry, MarketMessageContract messages) {
		super(gangland, "freeze", tree, parent);

		this.gangland     = gangland;
		this.tree         = tree;
		this.itemRegistry = itemRegistry;
		this.messages     = messages;

		this.addSubArgument(itemArg());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<item>"));
	}

	private OptionalArgument itemArg() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (args.length < 3) {
				return;
			}
			String id = args[2].toLowerCase();
			itemRegistry.find(id).ifPresentOrElse(state -> {
				state.setFrozen(!state.isFrozen());
				itemRegistry.persist(state);
				sender.sendMessage(state.isFrozen() ? messages.frozen(id) : messages.unfrozen(id));
			}, () -> sender.sendMessage(messages.unknownItem(id)));
		}, sender -> itemRegistry.all()
				.stream().map(MarketItemState::getItemId).sorted().toList());
	}
}
