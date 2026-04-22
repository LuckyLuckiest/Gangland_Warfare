package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.banker.view.BankerFlow;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
