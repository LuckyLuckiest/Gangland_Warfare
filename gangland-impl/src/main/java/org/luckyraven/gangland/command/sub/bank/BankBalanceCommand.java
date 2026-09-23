package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;

import org.luckyraven.keystone.bean.Qualifier;

class BankBalanceCommand extends SubArgument {

	private final UserManager<Player> userManager;

	protected BankBalanceCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                             @Qualifier("online") UserManager<Player> userManager) {
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
