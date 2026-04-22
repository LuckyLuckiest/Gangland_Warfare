package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;

import java.math.BigDecimal;

public final class GanglandBankerMessages implements BankerMessageContract {

	private static String fillAmount(String template, BigDecimal amount) {
		return template.replace("%money_symbol%", Settings.getMoneySymbol())
		               .replace("%amount%", Settings.formatAmount(amount));
	}

	@Override
	public String depositSuccess(BigDecimal amount) {
		return fillAmount(Messages.BANKER_DEPOSIT_SUCCESS.toString(), amount);
	}

	@Override
	public String withdrawSuccess(BigDecimal amount) {
		return fillAmount(Messages.BANKER_WITHDRAW_SUCCESS.toString(), amount);
	}

	@Override
	public String noAccount() {
		return Messages.BANKER_NO_ACCOUNT.toString();
	}

	@Override
	public String insufficientCash(BigDecimal amount) {
		return fillAmount(Messages.BANKER_INSUFFICIENT_CASH.toString(), amount);
	}

	@Override
	public String insufficientBankFunds(BigDecimal amount) {
		return fillAmount(Messages.BANKER_INSUFFICIENT_BANK_FUNDS.toString(), amount);
	}

	@Override
	public String dailyDepositReached(BigDecimal limit) {
		return Messages.BANKER_DAILY_DEPOSIT_REACHED.toString()
		                                            .replace("%money_symbol%", Settings.getMoneySymbol())
		                                            .replace("%limit%", Settings.formatAmount(limit));
	}

	@Override
	public String capExceeded(BigDecimal cap) {
		return Messages.BANKER_CAP_EXCEEDED.toString()
		                                   .replace("%money_symbol%", Settings.getMoneySymbol())
		                                   .replace("%cap%", Settings.formatAmount(cap));
	}

	@Override
	public String upgradeSuccess(String tierDisplay) {
		return Messages.BANKER_UPGRADE_SUCCESS.toString().replace("%tier%", ChatUtil.color(tierDisplay));
	}

	@Override
	public String upgradeMaxTier() {
		return Messages.BANKER_UPGRADE_MAX_TIER.toString();
	}

	@Override
	public String upgradeInsufficientFunds(BigDecimal cost) {
		return Messages.BANKER_UPGRADE_INSUFFICIENT_FUNDS.toString()
		                                                 .replace("%money_symbol%", Settings.getMoneySymbol())
		                                                 .replace("%cost%", Settings.formatAmount(cost));
	}

	@Override
	public String tierMissing() {
		return Messages.BANKER_TIER_MISSING.toString();
	}

	@Override
	public String createSuccess(String accountName) {
		return Messages.BANKER_CREATE_SUCCESS.toString().replace("%bank%", accountName);
	}

	@Override
	public String createAlreadyHasAccount() {
		return Messages.BANKER_CREATE_ALREADY_HAS_ACCOUNT.toString();
	}

	@Override
	public String createCannotAfford(BigDecimal fee) {
		return Messages.BANKER_CREATE_CANNOT_AFFORD.toString()
		                                           .replace("%money_symbol%", Settings.getMoneySymbol())
		                                           .replace("%fee%", Settings.formatAmount(fee));
	}

	@Override
	public String createNameEmpty() {
		return Messages.BANKER_CREATE_NAME_EMPTY.toString();
	}

	@Override
	public String renameSuccess(String oldName, String newName) {
		return Messages.BANKER_RENAME_SUCCESS.toString()
		                                     .replace("%old%", oldName == null ? "" : oldName)
		                                     .replace("%new%", newName);
	}

	@Override
	public String renameCannotAfford(BigDecimal fee) {
		return Messages.BANKER_RENAME_CANNOT_AFFORD.toString()
		                                           .replace("%money_symbol%", Settings.getMoneySymbol())
		                                           .replace("%fee%", Settings.formatAmount(fee));
	}

	@Override
	public String renameNameEmpty() {
		return Messages.BANKER_RENAME_NAME_EMPTY.toString();
	}

	@Override
	public String renameNameUnchanged() {
		return Messages.BANKER_RENAME_NAME_UNCHANGED.toString();
	}

	@Override
	public String weeklyLoanSuccess(BigDecimal amount) {
		return fillAmount(Messages.BANKER_LOAN_WEEKLY_SUCCESS.toString(), amount);
	}

	@Override
	public String monthlyLoanSuccess(BigDecimal amount) {
		return fillAmount(Messages.BANKER_LOAN_MONTHLY_SUCCESS.toString(), amount);
	}

	@Override
	public String loanOnCooldown(String remaining) {
		return Messages.BANKER_LOAN_ON_COOLDOWN.toString().replace("%remaining%", remaining);
	}

	@Override
	public String loanDisabled() {
		return Messages.BANKER_LOAN_DISABLED.toString();
	}

	@Override
	public String loanCapFull(BigDecimal amount) {
		return fillAmount(Messages.BANKER_LOAN_CAP_FULL.toString(), amount);
	}

}
