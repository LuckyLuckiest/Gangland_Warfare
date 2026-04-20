package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.banker.message.BankerMessageContract;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;

public final class GanglandBankerMessages implements BankerMessageContract {

	@Override
	public String depositSuccess(double amount) {
		return fill(Messages.BANKER_DEPOSIT_SUCCESS.toString(), amount);
	}

	@Override
	public String withdrawSuccess(double amount) {
		return fill(Messages.BANKER_WITHDRAW_SUCCESS.toString(), amount);
	}

	@Override
	public String noAccount() {
		return Messages.BANKER_NO_ACCOUNT.toString();
	}

	@Override
	public String insufficientCash(double amount) {
		return fill(Messages.BANKER_INSUFFICIENT_CASH.toString(), amount);
	}

	@Override
	public String insufficientBankFunds(double amount) {
		return fill(Messages.BANKER_INSUFFICIENT_BANK_FUNDS.toString(), amount);
	}

	@Override
	public String dailyDepositReached(double limit) {
		return Messages.BANKER_DAILY_DEPOSIT_REACHED.toString()
		                                            .replace("%money_symbol%", Settings.getMoneySymbol())
		                                            .replace("%limit%", Settings.formatDouble(limit));
	}

	@Override
	public String dailyWithdrawReached(double limit) {
		return Messages.BANKER_DAILY_WITHDRAW_REACHED.toString()
		                                             .replace("%money_symbol%", Settings.getMoneySymbol())
		                                             .replace("%limit%", Settings.formatDouble(limit));
	}

	@Override
	public String capExceeded(double cap) {
		return Messages.BANKER_CAP_EXCEEDED.toString()
		                                   .replace("%money_symbol%", Settings.getMoneySymbol())
		                                   .replace("%cap%", Settings.formatDouble(cap));
	}

	@Override
	public String upgradeSuccess(String tierDisplay) {
		return Messages.BANKER_UPGRADE_SUCCESS.toString().replace("%tier%", tierDisplay);
	}

	@Override
	public String upgradeMaxTier() {
		return Messages.BANKER_UPGRADE_MAX_TIER.toString();
	}

	@Override
	public String upgradeInsufficientFunds(double cost) {
		return Messages.BANKER_UPGRADE_INSUFFICIENT_FUNDS.toString()
		                                                 .replace("%money_symbol%", Settings.getMoneySymbol())
		                                                 .replace("%cost%", Settings.formatDouble(cost));
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
	public String createCannotAfford(double fee) {
		return Messages.BANKER_CREATE_CANNOT_AFFORD.toString()
		                                           .replace("%money_symbol%", Settings.getMoneySymbol())
		                                           .replace("%fee%", Settings.formatDouble(fee));
	}

	@Override
	public String createNameEmpty() {
		return Messages.BANKER_CREATE_NAME_EMPTY.toString();
	}

	@Override
	public String deleteSuccess(String accountName, double refund) {
		return Messages.BANKER_DELETE_SUCCESS.toString()
		                                     .replace("%bank%", accountName)
		                                     .replace("%money_symbol%", Settings.getMoneySymbol())
		                                     .replace("%refund%", Settings.formatDouble(refund));
	}

	@Override
	public String renameSuccess(String oldName, String newName) {
		return Messages.BANKER_RENAME_SUCCESS.toString()
		                                     .replace("%old%", oldName == null ? "" : oldName)
		                                     .replace("%new%", newName);
	}

	@Override
	public String renameCannotAfford(double fee) {
		return Messages.BANKER_RENAME_CANNOT_AFFORD.toString()
		                                           .replace("%money_symbol%", Settings.getMoneySymbol())
		                                           .replace("%fee%", Settings.formatDouble(fee));
	}

	@Override
	public String renameNameEmpty() {
		return Messages.BANKER_RENAME_NAME_EMPTY.toString();
	}

	@Override
	public String renameNameUnchanged() {
		return Messages.BANKER_RENAME_NAME_UNCHANGED.toString();
	}

	private static String fill(String template, double amount) {
		return template.replace("%money_symbol%", Settings.getMoneySymbol())
		               .replace("%amount%", Settings.formatDouble(amount));
	}

}
