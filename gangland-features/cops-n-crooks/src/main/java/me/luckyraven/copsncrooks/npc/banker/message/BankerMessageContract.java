package me.luckyraven.copsncrooks.npc.banker.message;

import java.math.BigDecimal;

/**
 * Banker-scoped message contract. Routes every user-facing banker string back to the host module's {@code Messages}
 * enum so the views never depend on gangland-impl directly. Currency-bearing parameters are {@link BigDecimal} so
 * values beyond {@code double} precision render correctly.
 */
public interface BankerMessageContract {

	String depositSuccess(BigDecimal amount);

	String withdrawSuccess(BigDecimal amount);

	String noAccount();

	String insufficientCash(BigDecimal amount);

	String insufficientBankFunds(BigDecimal amount);

	String dailyDepositReached(BigDecimal limit);

	String capExceeded(BigDecimal cap);

	String upgradeSuccess(String tierDisplay);

	String upgradeMaxTier();

	String upgradeInsufficientFunds(BigDecimal cost);

	String tierMissing();

	String createSuccess(String accountName);

	String createAlreadyHasAccount();

	String createCannotAfford(BigDecimal fee);

	String createNameEmpty();

	String renameSuccess(String oldName, String newName);

	String renameCannotAfford(BigDecimal fee);

	String renameNameEmpty();

	String renameNameUnchanged();

	String weeklyLoanSuccess(BigDecimal amount);

	String monthlyLoanSuccess(BigDecimal amount);

	String loanOnCooldown(String remaining);

	String loanDisabled();

	String loanCapFull(BigDecimal amount);

}
