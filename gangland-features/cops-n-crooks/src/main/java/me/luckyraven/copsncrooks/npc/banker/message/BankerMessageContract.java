package me.luckyraven.copsncrooks.npc.banker.message;

/**
 * Banker-scoped message contract. Routes every user-facing banker string back to the host module's {@code Messages}
 * enum so the views never depend on gangland-impl directly.
 */
public interface BankerMessageContract {

	String depositSuccess(double amount);

	String withdrawSuccess(double amount);

	String noAccount();

	String insufficientCash(double amount);

	String insufficientBankFunds(double amount);

	String dailyDepositReached(double limit);

	String dailyWithdrawReached(double limit);

	String capExceeded(double cap);

	String upgradeSuccess(String tierDisplay);

	String upgradeMaxTier();

	String upgradeInsufficientFunds(double cost);

	String tierMissing();

	String createSuccess(String accountName);

	String createAlreadyHasAccount();

	String createCannotAfford(double fee);

	String createNameEmpty();

	String deleteSuccess(String accountName, double refund);

	String renameSuccess(String oldName, String newName);

	String renameCannotAfford(double fee);

	String renameNameEmpty();

	String renameNameUnchanged();

}
