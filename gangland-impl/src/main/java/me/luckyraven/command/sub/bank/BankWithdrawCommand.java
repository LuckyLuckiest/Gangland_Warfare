package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.economy.bank.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.NavigableSet;

class BankWithdrawCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;

	protected BankWithdrawCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager) {
		super(gangland, "withdraw", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;

		this.addSubArgument(bankWithdraw());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasBank()) {
				user.sendMessage(Messages.MUST_CREATE_BANK.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
		};
	}

	private OptionalArgument bankWithdraw() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Bank bank = user.getBank();

			if (!user.hasBank() || bank == null) {
				user.sendMessage(Messages.MUST_CREATE_BANK.toString());
				return;
			}

			double argAmount;

			try {
				argAmount = Double.parseDouble(args[2]);
			} catch (NumberFormatException exception) {
				String string  = Messages.MUST_BE_NUMBERS.toString();
				String replace = string.replace("%command%", args[2]);

				user.sendMessage(replace);
				return;
			}

			double inBank = bank.getEconomy().getBalance() - argAmount;

			boolean processed = BankCommand.processMoney(user, bank, bank.getEconomy().getBalance(), argAmount, inBank,
			                                             user.getEconomy().getBalance() + argAmount);

			if (!processed) return;

			String string  = Messages.BANK_MONEY_WITHDRAW_PLAYER.toString();
			String replace = string.replace("%amount%", Settings.formatDouble(argAmount));

			user.getUser().sendMessage(replace);

		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null || !user.hasBank()) return null;

			Bank bank = user.getBank();

			if (bank == null) return null;

			EconomyHandler economy = bank.getEconomy();
			double         balance = economy.getBalance();

			if (balance <= 0D) return List.of("<amount>");

			NavigableSet<Double> values = NumberUtil.getSetOfNumbers(balance);

			return values.stream()
					.map(value -> Double.parseDouble(value.toString()))
					.map(value -> Math.round(value * 100.0) / 100.0)
					.sorted()
					.map(value -> value % 1 == 0 ? String.valueOf(value.longValue()) : String.format("%.2f", value))
					.toList();
		});
	}
}
