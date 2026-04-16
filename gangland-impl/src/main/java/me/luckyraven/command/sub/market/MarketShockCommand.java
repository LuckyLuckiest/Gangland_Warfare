package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.market.contract.MarketMessageContract;
import me.luckyraven.market.event.MarketShock;
import me.luckyraven.market.event.MarketShockRegistry;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

class MarketShockCommand extends SubArgument {

	private final Gangland              gangland;
	private final Tree<Argument>        tree;
	private final MarketShockRegistry   shockRegistry;
	private final MarketMessageContract messages;

	MarketShockCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                   MarketShockRegistry shockRegistry, MarketMessageContract messages) {
		super(gangland, "shock", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.shockRegistry = shockRegistry;
		this.messages      = messages;

		this.addSubArgument(shockName());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private OptionalArgument shockName() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (args.length < 3) {
				return;
			}
			String name = args[2];
			shockRegistry.fire(name).ifPresentOrElse(fired -> sender.sendMessage(
					messages.shockFired(fired.target().kind().name() + ":" + fired.target().id(), fired.multiplier(),
					                    fired.durationMillis() / 60_000L)), () -> sender.sendMessage(
					GanglandChatUtil.color("&cUnknown shock: " + name)));
		}, sender -> shockRegistry.templates()
				.stream().map(MarketShock::shockId).sorted().toList());
	}
}
