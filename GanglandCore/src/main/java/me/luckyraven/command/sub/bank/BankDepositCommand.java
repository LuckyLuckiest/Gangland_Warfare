package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BankDepositCommand extends SubArgument {

	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected BankDepositCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super("deposit", tree, parent);

		this.tree = tree;

		this.userManager = gangland.getInitializer().getUserManager();

		this.addSubArgument(bankDeposit());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument bankDeposit() {
		return new OptionalArgument(tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasBank()) {
				player.sendMessage(MessageAddon.MUST_CREATE_BANK.toString());
				return;
			}

			double argAmount;

			try {
				argAmount = Double.parseDouble(args[2]);
			} catch (NumberFormatException exception) {
				player.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
				return;
			}

			Bank bank = null;
			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank) {
					bank = (Bank) account;
					break;
				}

			if (bank == null) throw new NullPointerException("Bank is null");

			double inBank = bank.getEconomy().getBalance() + argAmount;

			if (inBank > SettingAddon.getBankMaxBalance()) {
				player.sendMessage(MessageAddon.CANNOT_EXCEED_MAXIMUM.toString());

			}

			BankCommand.processMoney(user, bank, user.getEconomy().getBalance(), argAmount, inBank,
									 user.getEconomy().getBalance() - argAmount);

			user.getUser()
				.sendMessage(MessageAddon.BANK_MONEY_DEPOSIT_PLAYER.toString()
																   .replace("%amount%",
																			SettingAddon.formatDouble(argAmount)));

		});
	}

}
