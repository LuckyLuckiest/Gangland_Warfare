package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.market.view.LedgerAdminView;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Opens {@link LedgerAdminView} — paginated transaction log (admin).
 */
class MarketLedgerCommand extends SubArgument {

	private final LedgerAdminView ledgerView;

	MarketLedgerCommand(Gangland gangland, Tree<Argument> tree, Argument parent, LedgerAdminView ledgerView) {
		super(gangland, "ledger", tree, parent);
		this.ledgerView = ledgerView;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(Messages.NOT_PLAYER.toString());
				return;
			}
			ledgerView.open(player);
		};
	}
}
