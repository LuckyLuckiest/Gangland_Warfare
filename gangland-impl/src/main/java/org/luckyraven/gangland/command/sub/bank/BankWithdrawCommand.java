package org.luckyraven.gangland.command.sub.bank;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.NavigableSet;

class BankWithdrawCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GanglandDatabase    ganglandDatabase;

	protected BankWithdrawCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              UserManager<Player> userManager, GanglandDatabase ganglandDatabase) {
		super(gangland, "withdraw", tree, parent);

		this.gangland         = gangland;
		this.tree             = tree;
		this.userManager      = userManager;
		this.ganglandDatabase = ganglandDatabase;

		OptionalArgument amount = bankWithdraw();
		amount.addSubArgument(bankWithdrawTarget());

		this.addSubArgument(amount);
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

			BigDecimal argAmount;

			try {
				argAmount = Currency.parse(args[2]);
			} catch (NumberFormatException exception) {
				String string  = Messages.MUST_BE_NUMBERS.toString();
				String replace = string.replace("%command%", args[2]);

				user.sendMessage(replace);
				return;
			}

			BigDecimal bankBal = bank.getEconomy().getAmount();
			BigDecimal cashBal = user.getEconomy().getAmount();
			BigDecimal inBank  = bankBal.subtract(argAmount);

			boolean processed = BankCommand.processMoney(user, bank, bankBal, argAmount, inBank,
			                                             cashBal.add(argAmount));

			if (!processed) return;

			IRepository<Bank> repo = ganglandDatabase.getRepositoryRegistry().getRepository(Bank.class);
			repo.save(bank);

			String string  = Messages.BANK_MONEY_WITHDRAW_PLAYER.toString();
			String replace = string.replace("%amount%", Settings.formatAmount(argAmount));

			user.getUser().sendMessage(replace);

		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null || !user.hasBank()) return null;

			Bank bank = user.getBank();

			if (bank == null) return null;

			EconomyHandler economy = bank.getEconomy();
			double         balance = economy.getAmount().doubleValue();

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

	private OptionalArgument bankWithdrawTarget() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String name         = args[3];
			Player targetPlayer = Bukkit.getPlayer(name);

			if (targetPlayer == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", name));
				return;
			}

			User<Player> target = userManager.getUser(targetPlayer);

			if (target == null) return;

			if (!target.hasBank()) {
				sender.sendMessage(Messages.MUST_CREATE_BANK.toString());
				return;
			}

			BigDecimal argAmount;

			try {
				argAmount = Currency.parse(args[2]);
			} catch (NumberFormatException exception) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[2]));
				return;
			}

			Bank       bank     = target.getBank();
			BigDecimal current  = bank.getEconomy().getAmount();
			BigDecimal newValue = current.subtract(argAmount).max(Currency.ZERO);
			BigDecimal taken    = current.subtract(newValue);

			bank.getEconomy().setAmount(Currency.of(newValue));

			IRepository<Bank> repo = ganglandDatabase.getRepositoryRegistry().getRepository(Bank.class);
			repo.save(bank);

			target.getUser().sendMessage(Messages.BANK_MONEY_WITHDRAW_PLAYER.toString()
			                                                                .replace("%amount%",
			                                                                         Settings.formatAmount(taken)));
		}, sender -> Bukkit.getOnlinePlayers()
				.stream().map(Player::getName).toList());
	}
}
