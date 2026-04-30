package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

class BankBalanceCommand extends SubArgument {

	private final UserManager<Player> userManager;

	protected BankBalanceCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager) {
		super(gangland, new String[]{"balance", "bal"}, tree, parent);

		this.userManager = userManager;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Bank bank = user.getBank();

			if (!user.hasBank() || bank == null) {
				user.sendMessage(Messages.MUST_CREATE_BANK.toString());
				return;
			}

			String string      = Messages.BANK_BALANCE_PLAYER.toString();
			String replacement = Settings.formatAmount(bank.getEconomy().getAmount());
			String replace     = string.replace("%balance%", replacement);

			user.sendMessage(replace);
		};
	}
}
