package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class BankBalanceCommand extends SubArgument {

	private final UserManager<Player> userManager;

	protected BankBalanceCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"balance", "bal"}, tree, parent);

		this.userManager = gangland.getInitializer().getUserManager();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Bank         bank   = Bank.getInstance(user);

			if (!user.hasBank() || bank == null) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			player.sendMessage(MessageAddon.BANK_BALANCE_PLAYER.toString()
															   .replace("%balance%", SettingAddon.formatDouble(
																	   bank.getEconomy().getBalance())));
		};
	}
}
