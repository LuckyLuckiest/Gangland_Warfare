package me.luckyraven.command.sub.bank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTier;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTierRegistry;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.economy.bank.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NavigableSet;

class BankDepositCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GanglandDatabase    ganglandDatabase;
	private final BankTierRegistry    tierRegistry;

	protected BankDepositCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager, GanglandDatabase ganglandDatabase,
	                             BankTierRegistry tierRegistry) {
		super(gangland, "deposit", tree, parent);

		this.gangland         = gangland;
		this.tree             = tree;
		this.userManager      = userManager;
		this.ganglandDatabase = ganglandDatabase;
		this.tierRegistry     = tierRegistry;

		this.addSubArgument(bankDeposit());
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

	private OptionalArgument bankDeposit() {
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

			BigDecimal cashBal = user.getEconomy().getAmount();
			BigDecimal bankBal = bank.getEconomy().getAmount();
			BigDecimal inBank  = bankBal.add(argAmount);
			BankTier   tier    = resolveTier(bank);

			if (tier != null && inBank.compareTo(tier.maxBalance()) > 0) {
				user.sendMessage(Messages.CANNOT_EXCEED_MAXIMUM.toString());
				return;
			}

			boolean bypass = player.hasPermission(BankCommand.BYPASS_CAP_PERMISSION);
			if (!bypass) {
				bank.resetIfStale(Instant.now(), Duration.ofSeconds(Settings.getBankResetPeriodSeconds()));

				BigDecimal limit = tier == null ? Currency.ZERO : tier.dailyDepositLimit();
				if (limit.signum() > 0
				    && Currency.of(bank.getDepositedToday()).add(argAmount).compareTo(limit) > 0) {
					user.sendMessage(Messages.BANKER_DAILY_DEPOSIT_REACHED.toString()
					                                                      .replace("%money_symbol%",
					                                                               Settings.getMoneySymbol())
					                                                      .replace("%limit%",
					                                                               Settings.formatAmount(limit)));
					return;
				}
			}

			boolean processed = BankCommand.processMoney(user, bank, cashBal, argAmount, inBank,
			                                             cashBal.subtract(argAmount));

			if (!processed) return;

			if (!bypass) bank.recordDeposit(argAmount.doubleValue());

			// Persist counters + balance immediately so /glw reload can't roll them back to the last autosave.
			IRepository<Bank> repo = ganglandDatabase.getRepositoryRegistry().getRepository(Bank.class);
			repo.save(bank);

			String string  = Messages.BANK_MONEY_DEPOSIT_PLAYER.toString();
			String replace = string.replace("%amount%", Settings.formatAmount(argAmount));

			user.getUser().sendMessage(replace);
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null || !user.hasBank()) return null;

			EconomyHandler economy = user.getEconomy();
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

	private BankTier resolveTier(Bank bank) {
		BankTier tier = tierRegistry.get(bank.getTierId());
		if (tier != null) return tier;
		return tierRegistry.first();
	}

}
