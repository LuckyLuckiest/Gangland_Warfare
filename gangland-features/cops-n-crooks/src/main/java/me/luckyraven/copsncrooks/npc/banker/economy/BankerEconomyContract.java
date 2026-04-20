package me.luckyraven.copsncrooks.npc.banker.economy;

import me.luckyraven.copsncrooks.npc.banker.tier.BankTier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Thin contract letting banker views (which live in cops-n-crooks) delegate every money / account mutation to the host
 * module (gangland-impl) without importing {@code UserManager} / {@code EconomyHandler} / the Bank repository directly.
 * All methods are invoked from the Banker NPC's GUI views.
 */
public interface BankerEconomyContract {

	BankerSnapshot snapshot(Player player);

	CreationInfo creationInfo(Player player);

	RenameInfo renameInfo(Player player);

	DeletionInfo deletionInfo(Player player);

	Result tryDeposit(Player player, double amount);

	Result tryWithdraw(Player player, double amount);

	Result tryUpgrade(Player player);

	Result tryCreateAccount(Player player, String accountName);

	Result tryRenameAccount(Player player, String newName);

	Result tryDeleteAccount(Player player);

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
		DAILY_WITHDRAW_REACHED,
		CAP_EXCEEDED,
		ALREADY_MAX_TIER,
		TIER_MISSING,
		ECONOMY_ERROR
	}

	record BankerSnapshot(boolean hasBank,
	                      double cashBalance,
	                      double bankBalance,
	                      double remainingDailyDeposit,
	                      double remainingDailyWithdraw,
	                      double dailyDepositLimit,
	                      double dailyWithdrawLimit,
	                      @Nullable BankTier currentTier,
	                      @Nullable BankTier nextTier) {
	}

	record CreationInfo(boolean hasAccount,
	                    double cashBalance,
	                    double fee,
	                    double initialBalance,
	                    boolean canAfford) {
	}

	/**
	 * Pre-flight data for the rename flow. {@code fee} comes out of cash; {@code canAfford} is pre-computed from the
	 * caller's cash balance so views can render state without re-invoking {@link #tryRenameAccount}.
	 */
	record RenameInfo(boolean hasAccount,
	                  @Nullable String currentName,
	                  double cashBalance,
	                  double fee,
	                  boolean canAfford) {
	}

	/**
	 * Pre-flight data for the close-account flow. {@code refund} is the net cash returned to the player
	 * ({@code bankBalance + createFee / 2 - deleteFee}); if this goes negative the caller doesn't have enough bank
	 * balance to cover the close and {@link Result#INSUFFICIENT_BANK_FUNDS} is returned from
	 * {@link #tryDeleteAccount}.
	 */
	record DeletionInfo(boolean hasAccount,
	                    @Nullable String accountName,
	                    double bankBalance,
	                    double deleteFee,
	                    double refund) {
	}

}
