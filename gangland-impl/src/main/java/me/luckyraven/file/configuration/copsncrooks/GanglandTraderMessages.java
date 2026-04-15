package me.luckyraven.file.configuration.copsncrooks;

import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.utilities.ChatUtil;

/**
 * Default {@link TraderMessageContract} implementation. Routes every call through a {@link Messages} enum key and
 * substitutes placeholders so cops-n-crooks trader code stays decoupled from the Messages enum.
 */
public final class GanglandTraderMessages implements TraderMessageContract {

	@Override
	public String tipSuccess(double amount) {
		return Messages.TRADER_TIP_SUCCESS.toString()
		                                  .replace("%money_symbol%", Settings.getMoneySymbol())
		                                  .replace("%amount%", Settings.formatDouble(amount));
	}

	@Override
	public String tipInsufficientFunds(double amount) {
		return Messages.TRADER_TIP_INSUFFICIENT_FUNDS.toString()
		                                             .replace("%money_symbol%", Settings.getMoneySymbol())
		                                             .replace("%amount%", Settings.formatDouble(amount));
	}

	@Override
	public String traitInvalid() {
		return Messages.TRADER_TRAIT_INVALID.toString();
	}

	@Override
	public String bargainAccepted(double resolvedPrice) {
		return Messages.TRADER_BARGAIN_ACCEPTED.toString()
		                                       .replace("%money_symbol%", Settings.getMoneySymbol())
		                                       .replace("%price%", Settings.formatDouble(resolvedPrice));
	}

	@Override
	public String bargainCounterOffer(double resolvedPrice) {
		return Messages.TRADER_BARGAIN_COUNTER_OFFER.toString()
		                                            .replace("%money_symbol%", Settings.getMoneySymbol())
		                                            .replace("%price%", Settings.formatDouble(resolvedPrice));
	}

	@Override
	public String bargainRejected() {
		return Messages.TRADER_BARGAIN_REJECTED.toString();
	}

	@Override
	public String bargainHardRejected() {
		return Messages.TRADER_BARGAIN_HARD_REJECTED.toString();
	}

	@Override
	public String bargainAlreadyBelow() {
		return Messages.TRADER_BARGAIN_ALREADY_BELOW.toString();
	}

	@Override
	public String angered(String traderName) {
		String name = ChatUtil.color(traderName != null ? traderName : "Trader");
		return Messages.TRADER_ANGERED.toString().replace("%trader%", name);
	}

}
