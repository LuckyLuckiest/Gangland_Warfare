package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.banker.view.BankerFlow;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;

/**
 * Phone-banking entry point for the rich banker panel. Routed from {@code phone_banking.yml} as {@code /glw bank menu};
 * the command starts a {@link BankerFlow} with a {@code null} banker so the menu renders with the "Online Banking"
 * display fallback.
 */
final class BankMenuCommand extends SubArgument {

	private final BankerFlow bankerFlow;

	BankMenuCommand(Gangland gangland, Tree<Argument> tree, Argument parent, BankerFlow bankerFlow) {
		super(gangland, "menu", tree, parent);
		this.bankerFlow = bankerFlow;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;
			bankerFlow.startFromPhone(player);
		};
	}

}
