package org.luckyraven.gangland.copsncrooks.npc.banker.economy;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTier;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thin contract letting banker views (which live in cops-n-crooks) delegate every money / account mutation to the host
 * module (gangland-impl) without importing {@code UserManager} / {@code EconomyHandler} / the Bank repository directly.
 * All methods are invoked from the Banker NPC's GUI views. Currency-bearing parameters and record fields are
 * {@link BigDecimal} — precision survives large tier caps and balances beyond {@code 2^53}.
 */
public interface BankerEconomyContract {

	BankerSnapshot snapshot(Player player);

	CreationInfo creationInfo(Player player);

	RenameInfo renameInfo(Player player);

	Result tryDeposit(Player player, BigDecimal amount);

	Result tryWithdraw(Player player, BigDecimal amount);

	Result tryUpgrade(Player player);

	Result tryCreateAccount(Player player, String accountName);

	Result tryRenameAccount(Player player, String newName);

	ClaimInfo claimInfo(Player player);

	Result tryClaimWeekly(Player player);

	Result tryClaimMonthly(Player player);

	enum Result {
		SUCCESS,
		NO_ACCOUNT,
		ALREADY_HAS_ACCOUNT,
		NAME_EMPTY,
		NAME_UNCHANGED,
		CANNOT_AFFORD_CREATION,
		CANNOT_AFFORD_RENAME,
		INSUFFICIENT_CASH,
		INSUFFICIENT_BANK_FUNDS,
		DAILY_DEPOSIT_REACHED,
		CAP_EXCEEDED,
		ALREADY_MAX_TIER,
		TIER_MISSING,
		LOAN_ON_COOLDOWN,
		LOAN_DISABLED,
		LOAN_CAP_FULL,
		ECONOMY_ERROR
	}

	record BankerSnapshot(boolean hasBank,
	                      BigDecimal cashBalance,
	                      BigDecimal bankBalance,
	                      BigDecimal remainingDailyDeposit,
	                      BigDecimal dailyDepositLimit,
	                      double dailyInterestRate,
	                      @Nullable Instant capResetAt,
	                      @Nullable BankTier currentTier,
	                      @Nullable BankTier nextTier) {
	}

	record CreationInfo(boolean hasAccount,
	                    BigDecimal cashBalance,
	                    BigDecimal fee,
	                    BigDecimal initialBalance,
	                    boolean canAfford) {
	}

	/**
	 * Pre-flight data for the rename flow. {@code fee} comes out of cash; {@code canAfford} is pre-computed from the
	 * caller's cash balance so views can render state without re-invoking {@link #tryRenameAccount}.
	 */
	record RenameInfo(boolean hasAccount,
	                  @Nullable String currentName,
	                  BigDecimal cashBalance,
	                  BigDecimal fee,
	                  boolean canAfford) {
	}

	/**
	 * Pre-flight data for the weekly / monthly loan grants. Amounts are the per-tier grant sizes; {@code readyAt}
	 * instants are {@code null} when never claimed (immediately claimable) or a future instant when still on cooldown.
	 * UI renders the countdown or "available" state directly from these fields.
	 */
	record ClaimInfo(boolean hasAccount,
	                 BigDecimal weeklyAmount,
	                 BigDecimal monthlyAmount,
	                 @Nullable Instant weeklyReadyAt,
	                 @Nullable Instant monthlyReadyAt) {
	}

}
