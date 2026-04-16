package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.market.view.MarketOverviewView;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Opens {@link MarketOverviewView} — paginated GUI listing every tracked market item.
 */
class MarketOverviewCommand extends SubArgument {

	private final MarketOverviewView overviewView;

	MarketOverviewCommand(Gangland gangland, Tree<Argument> tree, Argument parent, MarketOverviewView overviewView) {
		super(gangland, "overview", tree, parent);
		this.overviewView = overviewView;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}
			overviewView.open(player);
		};
	}
}
