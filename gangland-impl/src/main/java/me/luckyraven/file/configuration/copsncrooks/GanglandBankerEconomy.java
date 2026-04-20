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
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.economy.bank.EconomyException;
import me.luckyraven.economy.bank.EconomyHandler;
import me.luckyraven.persistence.repository.IRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Gangland-side implementation of {@link BankerEconomyContract}. Bridges the cops-n-crooks banker views to the real
 * {@link UserManager} + {@link Bank} entities + tier registry + Bank repository. Every entry point routes through
 * {@link #maintain(Bank)} first so the cap window is rolled and interest is accrued before any cap / balance check
 * reads the counters. Currency math runs in {@link BigDecimal}; {@code double} surfaces only at the cash-side boundary
 * (legacy {@link EconomyHandler} deposit / withdraw with a {@code double} argument, which internally widens back to
 * BigDecimal).
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

	/**
	 * Returns the UTC instant at which the next claim becomes available. {@code null} means the caller has never
	 * claimed and can do so immediately.
	 */
	@Nullable
	private static Instant readyAt(@Nullable Instant lastClaimAt, Duration window) {
		if (lastClaimAt == null) return null;
		return lastClaimAt.plus(window);
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
			                          Currency.of(user.getEconomy().getBalance()),
			                          Currency.ZERO, Currency.ZERO, Currency.ZERO, 0D,
			                          null, null, null);
		}

		BankTier current = maintain(bank);
		BankTier next    = tierRegistry.next(current == null ? null : current.id());

		BigDecimal depositLimit = current == null ? Currency.ZERO : current.dailyDepositLimit();
		BigDecimal remainingDep = depositLimit.signum() <= 0
		                          ? BigDecimal.valueOf(Double.MAX_VALUE)
		                          : depositLimit.subtract(Currency.of(bank.getDepositedToday())).max(Currency.ZERO);
		double interestRate = current == null ? 0D : current.interestRate();

		return new BankerSnapshot(true,
		                          Currency.of(user.getEconomy().getBalance()),
		                          bank.getEconomy().getAmount(),
		                          remainingDep,
		                          depositLimit,
		                          interestRate,
		                          bank.getCapResetAt(),
		                          current, next);
	}

	@Override
	public CreationInfo creationInfo(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null) {
			return new CreationInfo(false, Currency.ZERO,
			                        Currency.of(settings.getCreateFee()),
			                        Currency.of(settings.getInitialBalance()),
			                        false);
		}
		BigDecimal cash    = Currency.of(user.getEconomy().getBalance());
		BigDecimal fee     = Currency.of(settings.getCreateFee());
		BigDecimal initial = Currency.of(settings.getInitialBalance());
		boolean    has     = user.hasBank();
		boolean    afford  = cash.compareTo(fee) >= 0;
		return new CreationInfo(has, cash, fee, initial, afford);
	}

	@Override
	public RenameInfo renameInfo(Player player) {
		User<Player> user = userManager.getUser(player);
		BigDecimal   fee  = Currency.of(settings.getRenameFee());
		if (user == null || !user.hasBank() || user.getBank() == null) {
			BigDecimal cash = user == null ? Currency.ZERO : Currency.of(user.getEconomy().getBalance());
			return new RenameInfo(false, null, cash, fee, false);
		}
		BigDecimal cash = Currency.of(user.getEconomy().getBalance());
		return new RenameInfo(true, user.getBank().getName(), cash, fee, cash.compareTo(fee) >= 0);
	}

	@Override
	public Result tryDeposit(Player player, BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) return Result.ECONOMY_ERROR;

		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		BankTier tier = maintain(bank);

		BigDecimal normalised = Currency.of(amount);
		BigDecimal cashBal    = Currency.of(user.getEconomy().getBalance());
		if (cashBal.compareTo(normalised) < 0) return Result.INSUFFICIENT_CASH;

		boolean bypass = player.hasPermission(BankCommand.BYPASS_CAP_PERMISSION);

		BigDecimal limit = tier == null ? Currency.ZERO : tier.dailyDepositLimit();
		if (!bypass && limit.signum() > 0) {
			BigDecimal projected = Currency.of(bank.getDepositedToday()).add(normalised);
			if (projected.compareTo(limit) > 0) return Result.DAILY_DEPOSIT_REACHED;
		}

		if (tier != null && bank.getEconomy().getAmount().add(normalised).compareTo(tier.maxBalance()) > 0) {
			return Result.CAP_EXCEEDED;
		}

		EconomyHandler cash = user.getEconomy();
		try {
			cash.withdrawAmount(normalised);
		} catch (EconomyException e) {
			log.warn("Deposit withdraw-from-cash failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		bank.getEconomy().depositAmount(normalised);
		if (!bypass) bank.recordDeposit(normalised.doubleValue());
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public Result tryWithdraw(Player player, BigDecimal amount) {
		if (amount == null || amount.signum() <= 0) return Result.ECONOMY_ERROR;

		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		maintain(bank);

		BigDecimal normalised = Currency.of(amount);
		if (bank.getEconomy().getAmount().compareTo(normalised) < 0) return Result.INSUFFICIENT_BANK_FUNDS;

		try {
			bank.getEconomy().withdrawAmount(normalised);
		} catch (EconomyException e) {
			log.warn("Withdraw from bank failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		user.getEconomy().depositAmount(normalised);
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public Result tryUpgrade(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		BankTier current = maintain(bank);
		BankTier next    = tierRegistry.next(current == null ? null : current.id());
		if (next == null) return Result.ALREADY_MAX_TIER;

		BigDecimal cost = next.upgradeCost();
		if (bank.getEconomy().getAmount().compareTo(cost) < 0) return Result.INSUFFICIENT_BANK_FUNDS;

		if (cost.signum() > 0) {
			try {
				bank.getEconomy().withdrawAmount(cost);
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

		BigDecimal fee = Currency.of(settings.getCreateFee());
		if (Currency.of(user.getEconomy().getBalance()).compareTo(fee) < 0) return Result.CANNOT_AFFORD_CREATION;

		Bank bank = new Bank(user.getUser().getUniqueId(), accountName);
		bank.getEconomy().setUser(user);

		try {
			if (fee.signum() > 0) user.getEconomy().withdrawAmount(fee);
		} catch (EconomyException e) {
			log.warn("Account creation fee withdraw failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		bank.getEconomy().setAmount(Currency.of(settings.getInitialBalance()));

		BankTier first = tierRegistry.first();
		if (first != null) bank.setTierId(first.id());

		// Anchor the interest clock at creation so new accounts don't back-accrue from epoch 0.
		bank.setLastInterestAt(Instant.now());

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

		maintain(bank);

		String trimmed = newName.trim();
		if (trimmed.equals(bank.getName())) return Result.NAME_UNCHANGED;

		BigDecimal fee = Currency.of(settings.getRenameFee());
		if (Currency.of(user.getEconomy().getBalance()).compareTo(fee) < 0) return Result.CANNOT_AFFORD_RENAME;

		try {
			if (fee.signum() > 0) user.getEconomy().withdrawAmount(fee);
		} catch (EconomyException e) {
			log.warn("Rename fee withdraw failed for {}: {}", player.getName(), e.getMessage());
			return Result.ECONOMY_ERROR;
		}
		bank.setName(trimmed);
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	@Override
	public ClaimInfo claimInfo(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasBank() || user.getBank() == null) {
			return new ClaimInfo(false, Currency.ZERO, Currency.ZERO, null, null);
		}
		Bank       bank    = user.getBank();
		BankTier   tier    = resolveTier(bank);
		BigDecimal weekly  = tier == null ? Currency.ZERO : tier.weeklyLoanAmount();
		BigDecimal monthly = tier == null ? Currency.ZERO : tier.monthlyLoanAmount();
		Instant    wReady  = readyAt(bank.getLastWeeklyLoanAt(), Duration.ofDays(7));
		Instant    mReady  = readyAt(bank.getLastMonthlyLoanAt(), Duration.ofDays(30));
		return new ClaimInfo(true, weekly, monthly, wReady, mReady);
	}

	@Override
	public Result tryClaimWeekly(Player player) {
		return tryClaim(player, ClaimKind.WEEKLY);
	}

	@Override
	public Result tryClaimMonthly(Player player) {
		return tryClaim(player, ClaimKind.MONTHLY);
	}

	private Result tryClaim(Player player, ClaimKind kind) {
		User<Player> user = userManager.getUser(player);
		if (user == null) return Result.ECONOMY_ERROR;

		Bank bank = user.getBank();
		if (bank == null) return Result.NO_ACCOUNT;

		BankTier tier = maintain(bank);
		if (tier == null) return Result.TIER_MISSING;

		BigDecimal amount = kind == ClaimKind.WEEKLY ? tier.weeklyLoanAmount() : tier.monthlyLoanAmount();
		if (amount.signum() <= 0) return Result.LOAN_DISABLED;

		Instant  lastClaim = kind == ClaimKind.WEEKLY ? bank.getLastWeeklyLoanAt() : bank.getLastMonthlyLoanAt();
		Duration window    = kind == ClaimKind.WEEKLY ? Duration.ofDays(7) : Duration.ofDays(30);
		Instant  now       = Instant.now();
		if (lastClaim != null && now.isBefore(lastClaim.plus(window))) {
			return Result.LOAN_ON_COOLDOWN;
		}

		// Grants deposit into the BANK balance, not cash. Refuse the claim if the account can't hold the full
		// amount — cooldown stays untouched so the player can come back once they've made room.
		if (bank.getEconomy().getAmount().add(amount).compareTo(tier.maxBalance()) > 0) {
			return Result.LOAN_CAP_FULL;
		}

		bank.getEconomy().depositAmount(amount);
		if (kind == ClaimKind.WEEKLY) bank.setLastWeeklyLoanAt(now);
		else bank.setLastMonthlyLoanAt(now);
		bankRepository.save(bank);
		return Result.SUCCESS;
	}

	/**
	 * Rolls the deposit window and accrues interest for {@code bank} using the caller's current tier. Returns the
	 * resolved {@link BankTier} so callers don't have to call {@link #resolveTier(Bank)} again. Safe to call every
	 * interaction — both inner helpers are idempotent if the window hasn't expired / no elapsed time has passed.
	 */
	@Nullable
	private BankTier maintain(Bank bank) {
		bank.resetIfStale(Instant.now(), Duration.ofSeconds(settings.getResetPeriodSeconds()));

		BankTier tier = resolveTier(bank);
		if (tier != null) {
			bank.accrueInterest(Instant.now(), tier.interestRate(), tier.maxBalance().doubleValue());
		}
		return tier;
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
		return new BankerSnapshot(false,
		                          Currency.ZERO, Currency.ZERO, Currency.ZERO, Currency.ZERO,
		                          0D, null, null, null);
	}

	private enum ClaimKind {
		WEEKLY,
		MONTHLY
	}

}
