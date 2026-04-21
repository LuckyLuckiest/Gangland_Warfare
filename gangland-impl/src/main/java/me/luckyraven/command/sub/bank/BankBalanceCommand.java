package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
