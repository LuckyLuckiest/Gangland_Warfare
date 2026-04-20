package me.luckyraven.file.configuration.copsncrooks;

import lombok.CustomLog;
import me.luckyraven.command.sub.bank.BankCommand;
import me.luckyraven.copsncrooks.npc.banker.config.BankerSettings;
import me.luckyraven.copsncrooks.npc.banker.economy.BankerEconomyContract;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTier;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTierRegistry;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.economy.bank.EconomyException;
import me.luckyraven.economy.bank.EconomyHandler;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * Gangland-side implementation of {@link BankerEconomyContract}. Bridges the cops-n-crooks banker views to the real
 * {@link UserManager} + {@link Bank} entities + tier registry + Bank repository. Every mutation refreshes the Bank's
 * rolling-window counters through {@link Bank#resetIfStale} so cap enforcement uses fresh values.
 */
@CustomLog
public final class GanglandBankerEconomy implements BankerEconomyContract {

	private final UserManager<Player> userManager;
	private final BankTierRegistry    tierRegistry;
	private final BankerSettings      settings;
	private final IRepository<Bank>   bankRepository;

	public GanglandBankerEconomy(UserManager<Player> userManager, BankTierRegistry tierRegistry,
	                             BankerSettings settings, IRepository<Bank> bankRepository) {
		this.userManager    = userManager;
		this.tierRegistry   = tierRegistry;
		this.settings       = settings;
		this.bankRepository = bankRepository;
	}

	@Override
	public BankerSnapshot snapshot(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null) {
			return emptySnapshot();
		}

		Bank bank = user.getBank();
		if (bank == null) {
			return new BankerSnapshot(false,
			                          user.getEconomy().getBalance(),
			                          0, 0, 0,
			                          settings.getDailyDepositLimit(),
			                          settings.getDailyWithdrawLimit(),
			                          null, null);
		}

		refreshWindow(bank);

		BankTier current = resolveTier(bank);
		BankTier next    = tierRegistry.next(current == null ? null : current.id());

		double depositLimit  = settings.getDailyDepositLimit();
		double withdrawLimit = settings.getDailyWithdrawLimit();
		double remainingDep  = Math.max(0, depositLimit - bank.getDepositedToday());
		double remainingWd   = Math.max(0, withdrawLimit - bank.getWithdrawnToday());

		return new BankerSnapshot(true,
		                          user.getEconomy().getBalance(),
		                          bank.getEconomy().getBalance(),
		                          remainingDep, remainingWd,
		                          depositLimit, withdrawLimit,
		                          current, next);
	}

	@Override
	public CreationInfo creationInfo(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null) {
			return new CreationInfo(false, 0, settings.getCreateFee(), settings.getInitialBalance(), false);
		}
		double  cash    = user.getEconomy().getBalance();
		double  fee     = settings.getCreateFee();
		double  initial = settings.getInitialBalance();
		boolean has     = user.hasBank();
		boolean afford  = cash >= fee;
		return new CreationInfo(has, cash, fee, initial, afford);
	}

	@Override
	public RenameInfo renameInfo(Player player) {
		User<Player> user = userManager.getUser(player);
		double       fee  = settings.getRenameFee();
		if (user == null || !user.hasBank() || user.getBank() == null) {
			return new RenameInfo(false, null, user == null ? 0 : user.getEconomy().getBalance(), fee, false);
		}
		double cash = user.getEconomy().getBalance();
		return new RenameInfo(true, user.getBank().getName(), cash, fee, cash >= fee);
	}

	@Override
	public DeletionInfo deletionInfo(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasBank() || user.getBank() == null) {
			return new DeletionInfo(false, null, 0, settings.getDeleteFee(), 0);
		}
		Bank   bank      = user.getBank();
		double balance   = bank.getEconomy().getBalance();
		double deleteFee = settings.getDeleteFee();
		double refund    = balance + settings.getCreateFee() / 2D - deleteFee;
		return new DeletionInfo(true, bank.getName(), balance, deleteFee, refund);
	}

	@Override
	public Result tryDeposit(Player player, double amount) {
		if (amount <= 0) return Result.ECONOMY_ERROR;

		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		refreshWindow(bank);

		if (user.getEconomy().getBalance() < amount) return Result.INSUFFICIENT_CASH;

		boolean bypass = player.hasPermission(BankCommand.BYPASS_CAP_PERMISSION);

		double limit = settings.getDailyDepositLimit();
		if (!bypass && bank.getDepositedToday() + amount > limit) return Result.DAILY_DEPOSIT_REACHED;

		BankTier tier = resolveTier(bank);
		if (tier != null && bank.getEconomy().getBalance() + amount > tier.maxBalance()) {
			return Result.CAP_EXCEEDED;
		}

		EconomyHandler cash = user.getEconomy();
		try {
			cash.withdraw(amount);
		} catch (EconomyException e) {
			log.warn("Deposit withdraw-from-cash failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		bank.getEconomy().deposit(amount);
		if (!bypass) bank.recordDeposit(amount);
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public Result tryWithdraw(Player player, double amount) {
		if (amount <= 0) return Result.ECONOMY_ERROR;

		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		refreshWindow(bank);

		if (bank.getEconomy().getBalance() < amount) return Result.INSUFFICIENT_BANK_FUNDS;

		boolean bypass = player.hasPermission(BankCommand.BYPASS_CAP_PERMISSION);

		double limit = settings.getDailyWithdrawLimit();
		if (!bypass && bank.getWithdrawnToday() + amount > limit) return Result.DAILY_WITHDRAW_REACHED;

		try {
			bank.getEconomy().withdraw(amount);
		} catch (EconomyException e) {
			log.warn("Withdraw from bank failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		user.getEconomy().deposit(amount);
		if (!bypass) bank.recordWithdraw(amount);
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public Result tryUpgrade(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		BankTier current = resolveTier(bank);
		BankTier next    = tierRegistry.next(current == null ? null : current.id());
		if (next == null) return Result.ALREADY_MAX_TIER;

		if (bank.getEconomy().getBalance() < next.upgradeCost()) return Result.INSUFFICIENT_BANK_FUNDS;

		if (next.upgradeCost() > 0) {
			try {
				bank.getEconomy().withdraw(next.upgradeCost());
			} catch (EconomyException e) {
				log.warn("Upgrade cost withdraw failed for {}: {}", player.getName(), e.getMessage());
				return Result.ECONOMY_ERROR;
			}
		}
		bank.setTierId(next.id());
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public Result tryCreateAccount(Player player, String accountName) {
		if (accountName == null || accountName.isBlank()) return Result.NAME_EMPTY;

		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		if (user.hasBank()) return Result.ALREADY_HAS_ACCOUNT;

		double fee = settings.getCreateFee();
		if (user.getEconomy().getBalance() < fee) return Result.CANNOT_AFFORD_CREATION;

		Bank bank = new Bank(user.getUser().getUniqueId(), accountName);
		bank.getEconomy().setUser(user);

		try {
			if (fee > 0) user.getEconomy().withdraw(fee);
		} catch (EconomyException e) {
			log.warn("Account creation fee withdraw failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		bank.getEconomy().setBalance(settings.getInitialBalance());

		BankTier first = tierRegistry.first();
		if (first != null) bank.setTierId(first.id());

		user.setBank(bank);
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public Result tryRenameAccount(Player player, String newName) {
		if (newName == null || newName.isBlank()) return Result.NAME_EMPTY;

		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		String trimmed = newName.trim();
		if (trimmed.equals(bank.getName())) return Result.NAME_UNCHANGED;

		double fee = settings.getRenameFee();
		if (user.getEconomy().getBalance() < fee) return Result.CANNOT_AFFORD_RENAME;

		try {
			if (fee > 0) user.getEconomy().withdraw(fee);
		} catch (EconomyException e) {
			log.warn("Rename fee withdraw failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		bank.setName(trimmed);
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public Result tryDeleteAccount(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		double deleteFee = settings.getDeleteFee();
		if (bank.getEconomy().getBalance() < deleteFee) return Result.INSUFFICIENT_BANK_FUNDS;

		double refund = bank.getEconomy().getBalance() + settings.getCreateFee() / 2D - deleteFee;
		if (refund > 0) user.getEconomy().deposit(refund);
		user.setBank(null);
		bankRepository.delete(bank);
		return Result.SUCCESS;
	}

	private void refreshWindow(Bank bank) {
		bank.resetIfStale(Instant.now(), Duration.ofSeconds(settings.getResetPeriodSeconds()));
	}

	@Nullable
	private BankTier resolveTier(Bank bank) {
		BankTier tier = tierRegistry.get(bank.getTierId());
		if (tier != null) return tier;
		BankTier fallback = tierRegistry.get(settings.getFallbackTierId());
		if (fallback != null) return fallback;
		return tierRegistry.first();
	}

	private BankerSnapshot emptySnapshot() {
		return new BankerSnapshot(false, 0, 0, 0, 0,
		                          settings.getDailyDepositLimit(),
		                          settings.getDailyWithdrawLimit(),
		                          null, null);
	}

}
